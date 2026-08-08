package dev.opencode.android

import android.app.Application
import dev.opencode.android.data.local.AppDatabase
import dev.opencode.android.data.network.JsonProvider
import dev.opencode.android.data.network.OpenCodeClient
import dev.opencode.android.data.prefs.Connection
import dev.opencode.android.data.prefs.EmbeddedPrefs
import dev.opencode.android.data.prefs.SettingsStore
import dev.opencode.android.data.repository.SessionRepository
import dev.opencode.android.server.OpenCodeServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpenCodeApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: SessionRepository
        private set
    lateinit var server: OpenCodeServerManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _connection = MutableStateFlow(Connection())
    val connection = _connection.asStateFlow()

    var embeddedPrefs: EmbeddedPrefs = EmbeddedPrefs()
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        server = OpenCodeServerManager(this)
        val db = AppDatabase.get(this)
        repository = SessionRepository(db) { buildClient() }

        appScope.launch {
            try {
                settings.embedded.collect { prefs ->
                    embeddedPrefs = prefs
                    if (prefs.enabled) server.start(prefs) else server.stop()
                }
            } catch (_: Exception) {
            }
        }

        appScope.launch {
            try {
                settings.connection.collect { remote ->
                    if (!embeddedPrefs.enabled) {
                        _connection.value = remote
                    }
                }
            } catch (_: Exception) {
            }
        }

        appScope.launch {
            server.status.collect { status ->
                when (status) {
                    is OpenCodeServerManager.Status.Running -> {
                        _connection.value = Connection(serverUrl = "http://127.0.0.1:${status.port}")
                    }
                    else -> {
                        if (embeddedPrefs.enabled && _connection.value.serverUrl.startsWith("http://127.0.0.1")) {
                            _connection.value = Connection()
                        }
                    }
                }
            }
        }
    }

    private fun buildClient(): OpenCodeClient {
        val c = _connection.value
        return OpenCodeClient(
            baseUrl = c.serverUrl,
            username = c.username.ifBlank { null },
            password = c.password.ifBlank { null },
            json = JsonProvider.json,
        )
    }

    fun resetConnection() {
        _connection.value = Connection()
    }

    override fun onTerminate() {
        server.stop()
        super.onTerminate()
    }
}