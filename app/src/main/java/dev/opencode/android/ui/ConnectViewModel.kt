package dev.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.data.network.JsonProvider
import dev.opencode.android.data.network.OpenCodeClient
import dev.opencode.android.data.prefs.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenCodeApp

    val serverUrl = MutableStateFlow("")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            app.settings.connection.first().let { saved ->
                serverUrl.value = saved.serverUrl
                username.value = saved.username
                password.value = saved.password
            }
        }
    }

    fun testOrSave(andNavigate: suspend () -> Unit) {
        val url = serverUrl.value.trim()
        if (url.isBlank()) {
            _status.value = "Sunucu adresi gir."
            return
        }
        viewModelScope.launch {
            _testing.value = true
            _status.value = null
            try {
                val client = OpenCodeClient(
                    baseUrl = url,
                    username = username.value.ifBlank { null },
                    password = password.value.ifBlank { null },
                    json = JsonProvider.json,
                )
                val ok = client.healthCheck()
                if (ok) {
                    app.settings.save(
                        Connection(
                            serverUrl = url,
                            username = username.value,
                            password = password.value,
                        ),
                    )
                    _status.value = "Bağlantı kuruldu"
                    andNavigate()
                } else {
                    _status.value = "Sunucu yanıt vermedi (health)"
                }
            } catch (e: Exception) {
                _status.value = e.message ?: "Bağlantı hatası"
            } finally {
                _testing.value = false
            }
        }
    }
}