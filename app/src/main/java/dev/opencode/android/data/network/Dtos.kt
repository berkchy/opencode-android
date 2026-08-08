package dev.opencode.android.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class TokenCache(
    @SerialName("read") val read: Long = 0,
    @SerialName("write") val write: Long = 0,
)

@Serializable
data class TokenUsage(
    @SerialName("input") val input: Long = 0,
    @SerialName("output") val output: Long = 0,
    @SerialName("reasoning") val reasoning: Long = 0,
    @SerialName("cache") val cache: TokenCache = TokenCache(),
)

@Serializable
data class SessionSummary(
    @SerialName("additions") val additions: Int = 0,
    @SerialName("deletions") val deletions: Int = 0,
    @SerialName("files") val files: Int = 0,
    @SerialName("diffs") val diffs: List<JsonElement> = emptyList(),
)

@Serializable
data class TimeUsage(
    @SerialName("created") val created: Long? = null,
    @SerialName("updated") val updated: Long? = null,
    @SerialName("completed") val completed: Long? = null,
)

@Serializable
data class ModelRef(
    @SerialName("id") val id: String? = null,
    @SerialName("providerID") val providerID: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("variant") val variant: String? = null,
    @SerialName("modelID") val modelID: String? = null,
)

@Serializable
data class SwitchModelRequest(
    @SerialName("model") val model: SwitchModelRef,
)

@Serializable
data class SwitchModelRef(
    @SerialName("id") val id: String,
    @SerialName("provider") val provider: String,
)

@Serializable
data class Session(
    @SerialName("id") val id: String = "",
    @SerialName("slug") val slug: String? = null,
    @SerialName("projectID") val projectID: String? = null,
    @SerialName("workspaceID") val workspaceID: String? = null,
    @SerialName("directory") val directory: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("parentID") val parentID: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("agentID") val agentID: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("summary") val summary: SessionSummary? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("tokens") val tokens: TokenUsage? = null,
    @SerialName("time") val time: TimeUsage? = null,
    @SerialName("version") val version: Int? = null,
)

@Serializable
data class SessionCreateRequest(
    @SerialName("title") val title: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("model") val model: ModelRef? = null,
    @SerialName("parentID") val parentID: String? = null,
)

@Serializable
data class SessionPatchRequest(
    @SerialName("title") val title: String? = null,
    @SerialName("summary") val summary: SessionSummary? = null,
)

@Serializable
data class TextPartInput(
    @SerialName("type") val type: String = "text",
    @SerialName("text") val text: String,
    @SerialName("synthetic") val synthetic: Boolean? = null,
    @SerialName("ignored") val ignored: Boolean? = null,
)

@Serializable
data class MessageSendRequest(
    @SerialName("messageID") val messageID: String? = null,
    @SerialName("model") val model: ModelRef? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("noReply") val noReply: Boolean? = null,
    @SerialName("parts") val parts: List<TextPartInput> = emptyList(),
)

@Serializable
data class ToolState(
    @SerialName("status") val status: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("input") val input: JsonElement? = null,
    @SerialName("output") val output: JsonElement? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("totalTime") val totalTime: Long? = null,
)

@Serializable
data class Part(
    @SerialName("id") val id: String = "",
    @SerialName("sessionID") val sessionID: String? = null,
    @SerialName("messageID") val messageID: String? = null,
    @SerialName("type") val type: String = "",
    @SerialName("text") val text: String? = null,
    @SerialName("tool") val tool: String? = null,
    @SerialName("callID") val callID: String? = null,
    @SerialName("state") val state: ToolState? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("snapshot") val snapshot: String? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("time") val time: TimeUsage? = null,
    @SerialName("metadata") val metadata: JsonObject? = null,
)

@Serializable
data class MessageError(
    @SerialName("type") val type: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("code") val code: String? = null,
)

@Serializable
data class Message(
    @SerialName("id") val id: String = "",
    @SerialName("sessionID") val sessionID: String? = null,
    @SerialName("role") val role: String = "",
    @SerialName("model") val model: String? = null,
    @SerialName("providerID") val providerID: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("parentID") val parentID: String? = null,
    @SerialName("error") val error: MessageError? = null,
    @SerialName("format") val format: JsonElement? = null,
    @SerialName("time") val time: TimeUsage? = null,
    @SerialName("summary") val summary: SessionSummary? = null,
    @SerialName("parts") val parts: List<Part> = emptyList(),
    @SerialName("synthetic") val synthetic: Boolean? = null,
)

@Serializable
data class Model(
    @SerialName("id") val id: String = "",
    @SerialName("providerID") val providerID: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("family") val family: String? = null,
    @SerialName("capabilities") val capabilities: JsonElement? = null,
)

@Serializable
data class Provider(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("env") val env: List<String> = emptyList(),
)

@Serializable
data class QuestionOption(
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
)

@Serializable
data class Question(
    @SerialName("id") val id: String = "",
    @SerialName("requestID") val requestID: String = "",
    @SerialName("sessionID") val sessionID: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("options") val options: List<QuestionOption> = emptyList(),
)

@Serializable
data class QuestionReplyRequest(
    @SerialName("selected") val selected: String,
    @SerialName("userResponse") val userResponse: String? = null,
)

@Serializable
data class Project(
    @SerialName("id") val id: String = "",
    @SerialName("path") val path: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("directories") val directories: List<String> = emptyList(),
)

@Serializable
data class ServerEvent(
    @SerialName("type") val type: String = "",
    @SerialName("data") val data: JsonObject = JsonObject(emptyMap()),
)
