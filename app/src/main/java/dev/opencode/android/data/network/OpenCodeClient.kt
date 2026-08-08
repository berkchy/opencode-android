package dev.opencode.android.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    override val message: String,
) : Exception("HTTP $statusCode: $message")

/**
 * Thin HTTP + SSE client for the opencode server (opencode serve).
 */
class OpenCodeClient(
    private val baseUrl: String,
    private val username: String?,
    private val password: String?,
    private val json: Json,
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val base = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    private val authHeader: String? by lazy {
        if (password.isNullOrEmpty()) null
        else "Basic " + Base64.getEncoder().encodeToString(
            "${username?.takeIf { it.isNotEmpty() } ?: "opencode"}:$password".toByteArray()
        )
    }

    private fun builder(path: String): Request.Builder {
        val url = base + path
        val request = Request.Builder().url(url)
        authHeader?.let { request.header("Authorization", it) }
        request.header("Accept", "application/json")
        return request
    }

    private suspend fun <T> execute(
        request: Request,
        block: (Int, String?) -> T,
    ): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body ?: response.message)
            }
            block(response.code, body)
        }
    }

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(builder("/health").get().build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun listProjects(): List<Project> = execute(
        builder("/project").get().build()
    ) { _, body -> parseList(body) }

    suspend fun listSessions(): List<Session> = execute(
        builder("/session").get().build()
    ) { _, body -> parseList(body) }

    suspend fun getSession(id: String): Session = execute(
        builder("/session/$id").get().build()
    ) { _, body -> parseObject(body) }

    suspend fun createSession(request: SessionCreateRequest): Session = execute(
        builder("/session").post(json.encodeToString(request).toRequestBody(JSON)).build()
    ) { _, body -> parseObject(body) }

    suspend fun patchSession(id: String, request: SessionPatchRequest): Session = execute(
        builder("/session/$id").patch(json.encodeToString(request).toRequestBody(JSON)).build()
    ) { _, body -> parseObject(body) }

    suspend fun deleteSession(id: String): Boolean = execute(
        builder("/session/$id").delete().build()
    ) { _, _ -> true }

    suspend fun listMessages(sessionId: String): List<Message> = execute(
        builder("/session/$sessionId/message").get().build()
    ) { _, body -> parseList(body) }

    suspend fun sendMessage(
        sessionId: String,
        request: MessageSendRequest,
    ): Message = execute(
        builder("/session/$sessionId/message").post(json.encodeToString(request).toRequestBody(JSON)).build()
    ) { _, body ->
        runCatching { parseObject<Message>(body) }.getOrElse { Message(id = "") }
    }

    suspend fun abort(sessionId: String): Boolean = execute(
        builder("/session/$sessionId/abort").post("".toRequestBody(JSON)).build()
    ) { _, body -> json.decodeFromString<Boolean>(body ?: "true") }

    suspend fun replyToQuestion(requestId: String, request: QuestionReplyRequest): Boolean = execute(
        builder("/question/$requestId/reply").post(json.encodeToString(request).toRequestBody(JSON)).build()
    ) { _, _ -> true }

    suspend fun listModels(): List<Model> = execute(
        builder("/model").get().build()
    ) { _, body -> parseList(body) }

    suspend fun listProviders(): List<Provider> = execute(
        builder("/provider").get().build()
    ) { _, body -> parseList(body) }

    /**
     * Switch the model used by subsequent turns (experimental v2 route).
     */
    suspend fun switchModel(sessionId: String, modelId: String, providerId: String): Boolean = execute(
        builder("/api/session/$sessionId/model")
            .post(json.encodeToString(SwitchModelRequest(SwitchModelRef(modelId, providerId))).toRequestBody(JSON))
            .build()
    ) { code, _ -> code in 200..204 }

    /**
     * Merge a partial config (used to register custom providers / models).
     */
    suspend fun patchConfig(payload: kotlinx.serialization.json.JsonElement): Boolean = execute(
        builder("/config").patch(payload.toString().toRequestBody(JSON)).build()
    ) { code, _ -> code in 200..299 }

    fun openEventStream(listener: ServerEventListener): EventSource {
        val builder = builder("/event")
            .header("Accept", "text/event-stream")
        val request = builder.build()
        return EventSources.createFactory(OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        ).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val event = ServerEvent(
                        type = type.orEmpty().ifEmpty { extractTypeFromData(data) },
                        data = parseData(data),
                    )
                    listener.onEvent(event)
                } catch (_: Exception) {
                    // ignore malformed frames
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                listener.onFailure(t)
            }

            override fun onClosed(eventSource: EventSource) {
                listener.onClosed()
            }
        })
    }

    private fun extractTypeFromData(data: String): String {
        return try {
            val obj = json.parseToJsonElement(data) as? kotlinx.serialization.json.JsonObject
            obj?.get("type")?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseData(data: String): JsonObject {
        return try {
            json.parseToJsonElement(data) as? JsonObject ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
    }

    private inline fun <reified T> parseList(body: String?): List<T> =
        json.decodeFromString<List<T>>(body ?: "[]")

    private inline fun <reified T> parseObject(body: String?): T {
        if (body.isNullOrBlank()) throw ApiException(0, "empty response")
        return json.decodeFromString<T>(body)
    }
}

fun interface ServerEventListener {
    fun onEvent(event: ServerEvent)
    fun onFailure(t: Throwable?) = Unit
    fun onClosed() = Unit
}