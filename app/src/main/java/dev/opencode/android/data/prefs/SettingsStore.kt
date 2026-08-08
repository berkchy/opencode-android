package dev.opencode.android.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opencode_settings")

data class Connection(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isValid: Boolean get() = serverUrl.isNotBlank()
}

/**
 * Configuration for the embedded (on-device) opencode server.
 * Defaults to OpenCode Zen free models — no API key required.
 */
data class EmbeddedPrefs(
    val enabled: Boolean = true,
    val model: String = "opencode/deepseek-v4-flash-free",
    val apiKey: String = "",
)

object EmbeddedDefaults {
    val FREE_MODELS = listOf(
        "opencode/deepseek-v4-flash-free",
        "opencode/mimo-v2.5-free",
        "opencode/laguna-s-2.1-free",
        "opencode/ling-3.0-tiny-free",
        "opencode/longcat-2.0-free",
        "opencode/north-mini-code-free",
        "opencode/nemotron-3-ultra-free",
        "opencode/big-pickle",
    )
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val EMBEDDED_ON = stringPreferencesKey("embedded_on")
        val EMBEDDED_MODEL = stringPreferencesKey("embedded_model")
        val EMBEDDED_API_KEY = stringPreferencesKey("embedded_api_key")
    }

    val connection: Flow<Connection> = context.dataStore.data.map { prefs ->
        Connection(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
        )
    }

    val embedded: Flow<EmbeddedPrefs> = context.dataStore.data.map { prefs ->
        EmbeddedPrefs(
            enabled = prefs[Keys.EMBEDDED_ON]?.toBooleanStrictOrNull() ?: true,
            model = prefs[Keys.EMBEDDED_MODEL] ?: EmbeddedPrefs().model,
            apiKey = prefs[Keys.EMBEDDED_API_KEY] ?: "",
        )
    }

    suspend fun save(connection: Connection) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = connection.serverUrl.trim()
            prefs[Keys.USERNAME] = connection.username.trim()
            prefs[Keys.PASSWORD] = connection.password.trim()
        }
    }

    suspend fun saveEmbedded(prefs: EmbeddedPrefs) {
        context.dataStore.edit { p ->
            p[Keys.EMBEDDED_ON] = prefs.enabled.toString()
            p[Keys.EMBEDDED_MODEL] = prefs.model.trim()
            p[Keys.EMBEDDED_API_KEY] = prefs.apiKey.trim()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}