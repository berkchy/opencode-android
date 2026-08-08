package dev.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.data.prefs.EmbeddedPrefs
import dev.opencode.android.server.OpenCodeServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenCodeApp
    private val repo = app.repository

    val connection = app.connection
    val models = repo.models
    val serverStatus: StateFlow<OpenCodeServerManager.Status> = app.server.status

    private val _embedded = MutableStateFlow(app.embeddedPrefs)
    val embedded = _embedded.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            _embedded.value = app.settings.embedded.first()
        }
    }

    fun logout() {
        viewModelScope.launch {
            app.settings.clear()
            app.resetConnection()
        }
    }

    fun saveEmbedded(enabled: Boolean, model: String, apiKey: String, restart: Boolean = true) {
        val prefs = EmbeddedPrefs(
            enabled = enabled,
            model = model.ifBlank { EmbeddedPrefs().model },
            apiKey = apiKey,
        )
        _embedded.value = prefs
        viewModelScope.launch {
            app.settings.saveEmbedded(prefs)
            if (enabled && restart) app.server.restart() else app.server.stop()
        }
    }

    fun stopEmbeddedServer() {
        app.server.stop()
    }

    fun addProvider(providerId: String, name: String, baseUrl: String, apiKey: String, modelsText: String) {
        viewModelScope.launch {
            _status.value = null
            try {
                val modelNames = modelsText.split(',', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                val modelsJson = JsonObject(
                    modelNames.associateWith { modelId ->
                        JsonObject(mapOf(
                            "name" to JsonPrimitive(modelId),
                            "attachment" to JsonPrimitive(false),
                        ))
                    },
                )

                val providerConfig = buildJsonObject {
                    put("id", providerId)
                    put("name", name)
                    put("options", buildJsonObject {
                        put("baseURL", baseUrl)
                        if (apiKey.isNotBlank()) put("apiKey", apiKey)
                    })
                    put("models", modelsJson)
                }

                val payload = buildJsonObject {
                    put("providers", buildJsonObject {
                        put(providerId, providerConfig)
                    })
                }

                val ok = repo.client().patchConfig(payload)
                _status.value = if (ok) "Provider eklendi. Sohbette model listesini yenile."
                else "Config güncellenemedi"
            } catch (e: Exception) {
                _status.value = e.message ?: "Hata"
            }
        }
    }
}