package dev.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.data.local.SessionEntity
import dev.opencode.android.data.network.Model
import dev.opencode.android.data.network.ModelRef
import dev.opencode.android.data.network.SessionCreateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenCodeApp
    private val repo = app.repository

    private val _sessions = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions

    val connection = app.connection

    val models: StateFlow<List<Model>> = repo.models

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        refresh(showLoading = true)
        viewModelScope.launch { repo.refreshModels() }
    }

    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _loading.value = true
            _refreshing.value = true
            _error.value = null
            try {
                repo.refreshSessions()
            } catch (e: Exception) {
                _error.value = e.message ?: "Oturumlar getirilemedi"
            } finally {
                _loading.value = false
                _refreshing.value = false
            }
        }
    }

    fun create(title: String?, model: Model?) {
        viewModelScope.launch {
            _error.value = null
            try {
                repo.client().createSession(
                    SessionCreateRequest(
                        title = title?.ifBlank { null },
                        model = model?.let {
                            ModelRef(id = it.id, providerID = it.providerID)
                        },
                    ),
                )
                repo.refreshSessions()
            } catch (e: Exception) {
                _error.value = e.message ?: "Oturum oluşturulamadı"
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching {
                repo.client().deleteSession(id)
            }.onFailure { e ->
                _error.value = e.message ?: "Silinemedi"
            }
            repo.deleteSessionLocal(id)
        }
    }

    fun logout() {
        viewModelScope.launch {
            app.settings.clear()
            app.resetConnection()
        }
    }

    fun dismissError() {
        _error.value = null
    }
}