package dev.opencode.android.data.repository

import dev.opencode.android.data.local.AppDatabase
import dev.opencode.android.data.local.MessageEntity
import dev.opencode.android.data.local.SessionEntity
import dev.opencode.android.data.network.JsonProvider
import dev.opencode.android.data.network.Message
import dev.opencode.android.data.network.Model
import dev.opencode.android.data.network.OpenCodeClient
import dev.opencode.android.data.network.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bridges the opencode server and the on-device cache (Room).
 * Sessions and messages are stored in Android app data so history survives restarts.
 */
class SessionRepository(
    private val db: AppDatabase,
    private val client: () -> OpenCodeClient,
) {
    private val json: Json = JsonProvider.json

    private val sessionDao = db.sessionDao()
    private val messageDao = db.messageDao()

    private val _models = MutableStateFlow<List<Model>>(emptyList())
    val models: StateFlow<List<Model>> = _models.asStateFlow()

    fun client(): OpenCodeClient = client()

    suspend fun refreshModels() {
        runCatching { client().listModels() }
            .onSuccess { _models.value = it }
    }

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.observe(sessionId)

    suspend fun refreshSessions() {
        runCatching { client().listSessions() }
            .onSuccess { sessions ->
                sessionDao.upsertAll(sessions.map { it.toEntity(json) })
            }
    }

    suspend fun upsertSession(session: Session) {
        sessionDao.upsert(session.toEntity(json))
    }

    suspend fun deleteSessionLocal(id: String) {
        messageDao.deleteAllBySession(id)
        sessionDao.delete(id)
    }

    suspend fun refreshMessages(sessionId: String) {
        runCatching { client().listMessages(sessionId) }
            .onSuccess { messages ->
                messageDao.upsertAll(messages.map { it.toEntity(sessionId, json) })
            }
    }

    suspend fun upsertMessage(message: Message, sessionIdOverride: String? = null) {
        val sessionId = sessionIdOverride ?: message.sessionID ?: return
        messageDao.upsert(message.toEntity(sessionId, json))
    }

    suspend fun deleteMessage(id: String) {
        messageDao.delete(id)
    }

    private fun Session.toEntity(json: Json): SessionEntity =
        SessionEntity(
            id = id,
            projectID = projectID,
            workspaceID = workspaceID,
            directory = directory,
            path = path,
            title = title ?: slug,
            agent = agent ?: agentID,
            model = model,
            cost = cost,
            summaryJson = summary?.let { json.encodeToString(it) },
            tokensJson = tokens?.let { json.encodeToString(it) },
            timeCreated = time?.created,
            timeUpdated = time?.updated,
        )

    private fun Message.toEntity(sessionId: String, json: Json): MessageEntity =
        MessageEntity(
            id = id,
            sessionId = sessionId,
            role = role,
            timeCreated = time?.created,
            model = model,
            agent = agent,
            errorJson = error?.let { json.encodeToString(it) },
            json = json.encodeToString(this),
        )
}