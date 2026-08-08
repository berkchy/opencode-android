package dev.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.data.local.AppDatabase
import dev.opencode.android.data.local.MessageEntity
import dev.opencode.android.data.network.JsonProvider
import dev.opencode.android.data.network.Message
import dev.opencode.android.data.network.MessageSendRequest
import dev.opencode.android.data.network.Model
import dev.opencode.android.data.network.Part
import dev.opencode.android.data.network.Question
import dev.opencode.android.data.network.TextPartInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A user-picked file attached to the next message. */
data class Attachment(
    val name: String,
    val content: String,
)

class ChatViewModel(
    application: Application,
    val sessionId: String,
) : AndroidViewModel(application) {

    companion object {
        fun Factory(application: Application, sessionId: String) =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T = ChatViewModel(application, sessionId) as T
            }
    }

    private val app = application as OpenCodeApp
    private val repo = app.repository
    private val json = JsonProvider.json
    private val db: AppDatabase = AppDatabase.get(app)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _question = MutableStateFlow<Question?>(null)
    val question: StateFlow<Question?> = _question.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _model = MutableStateFlow<String?>(null)
    val model: StateFlow<String?> = _model.asStateFlow()

    val models: StateFlow<List<Model>> = repo.models

    val messages: StateFlow<List<MessageEntity>> = repo.observeMessages(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val inflight = mutableMapOf<String, Message>()

    init {
        connectEventStream()
        viewModelScope.launch {
            val session = runCatching { repo.client().getSession(sessionId) }.getOrNull()
            _title.value = session?.title
            _model.value = session?.model
            repo.refreshModels()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repo.refreshMessages(sessionId)
        }
    }

    fun send(text: String, attachments: List<Attachment> = emptyList()) {
        if (busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val parts = attachments.map { a ->
                    TextPartInput(
                        text = "## Dosya: ${a.name}\n\n${a.content}",
                        synthetic = true,
                    )
                } + if (text.isNotBlank()) {
                    listOf(TextPartInput(text = text))
                } else {
                    emptyList()
                }
                if (parts.isEmpty()) {
                    _busy.value = false
                    return@launch
                }
                repo.client().sendMessage(
                    sessionId = sessionId,
                    request = MessageSendRequest(parts = parts),
                )
            } catch (e: Exception) {
                _busy.value = false
                _error.value = e.message ?: "Mesaj gönderilemedi"
            }
        }
    }

    /** Switch the session model for subsequent turns. */
    fun switchModel(model: Model) {
        if (model.id.isBlank()) return
        viewModelScope.launch {
            _error.value = null
            try {
                val ok = repo.client().switchModel(
                    sessionId = sessionId,
                    modelId = model.id,
                    providerId = model.providerID ?: "",
                )
                if (ok) _model.value = model.id
                else _error.value = "Model değiştirilemedi"
            } catch (e: Exception) {
                _error.value = "Model değiştirilemedi: ${e.message}"
            }
        }
    }

    fun abort() {
        if (!_busy.value) return
        viewModelScope.launch {
            runCatching { repo.client().abort(sessionId) }
        }
    }

    fun replyQuestion(question: Question, answer: String) {
        _question.value = null
        viewModelScope.launch {
            runCatching {
                repo.client().replyToQuestion(
                    requestId = question.requestID,
                    request = dev.opencode.android.data.network.QuestionReplyRequest(
                        selected = answer,
                    ),
                )
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private fun connectEventStream() {
        try {
            repo.client().openEvent(
                listener = object : dev.opencode.android.data.network.ServerEventListener {
                    override fun onEvent(event: dev.opencode.android.data.network.ServerEvent) {
                        handleEvent(event)
                    }

                    override fun onFailure(t: Throwable?) {
                        // stream breaks are handled on next reconnect; ignore transient drops,
                        // but caching stays intact on device
                    }
                },
            )
        } catch (_: Exception) {
        }
    }

    private fun handleEvent(event: dev.opencode.android.data.network.ServerEvent) {
        viewModelScope.launch {
            try {
                when (event.type) {
                    "session.status" -> Unit
                    "session.busy" -> _busy.value = true
                    "session.idle" -> _busy.value = false
                    "session.updated", "session.created", "session.deleted" -> Unit

                    "message.updated", "message.created" -> {
                        val message = json.decodeFromString<Message>(event.data.toString())
                        upsertCached(message)
                    }

                    "message.part.updated" -> {
                        val part = json.decodeFromString<Part>(event.data.toString())
                        mergePart(part)
                    }

                    "message.part.removed", "message.removed" -> {
                        val part = json.decodeFromString<Part>(event.data.toString())
                        removePart(part)
                    }

                    "permission.asked", "question.asked" -> {
                        val q = runCatching {
                            json.decodeFromString<Question>(event.data.toString())
                        }.getOrNull()
                        if (q != null && q.options.isNotEmpty()) {
                            _question.value = q
                        }
                    }

                    "user.message.created" -> {
                        val message = json.decodeFromString<Message>(event.data.toString())
                        upsertCached(message)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun upsertCached(message: Message) {
        inflight[message.id] = message
        repo.upsertMessage(message, sessionIdOverride = sessionId)
    }

    private suspend fun mergePart(part: Part) {
        val messageId = part.messageID ?: return
        val existing: Message? = inflight[messageId] ?: runCatching {
            db.messageDao().get(messageId)?.let { json.decodeFromString<Message>(it.json) }
        }.getOrNull()

        val base = existing ?: Message(
            id = messageId,
            sessionID = sessionId,
            role = "assistant",
        )
        val idx = base.parts.indexOfFirst { it.id == part.id }
        val newParts = if (idx >= 0) {
            base.parts.toMutableList().also { it[idx] = part }
        } else {
            base.parts + part
        }
        val merged = base.copy(parts = newParts)
        inflight[messageId] = merged
        repo.upsertMessage(merged, sessionIdOverride = sessionId)
    }

    private suspend fun removePart(part: Part) {
        val messageId = part.messageID ?: return
        val message = inflight[messageId] ?: return
        val merged = message.copy(parts = message.parts.filterNot { it.id == part.id })
        inflight[messageId] = merged
        repo.upsertMessage(merged, sessionIdOverride = sessionId)
    }

    override fun onCleared() {
        super.onCleared()
        inflight.clear()
    }
}