package dev.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.OpenCodeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            app.settings.clear()
            app.resetConnection()
        }
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