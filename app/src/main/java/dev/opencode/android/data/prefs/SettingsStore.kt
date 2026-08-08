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

class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    val connection: Flow<Connection> = context.dataStore.data.map { prefs ->
        Connection(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
        )
    }

    suspend fun save(connection: Connection) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = connection.serverUrl.trim()
            prefs[Keys.USERNAME] = connection.username.trim()
            prefs[Keys.PASSWORD] = connection.password.trim()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}