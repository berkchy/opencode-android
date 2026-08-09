package dev.opencode.android

import android.app.Application
import dev.opencode.android.data.local.AppDatabase
import dev.opencode.android.data.network.JsonProvider
import dev.opencode.android.data.network.OpenCodeClient
import dev.opencode.android.data.prefs.Connection
import dev.opencode.android.data.prefs.SettingsStore
import dev.opencode.android.data.repository.SessionRepository
import dev.opencode.android.util.AppLog
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _connection = MutableStateFlow(Connection())
    val connection = _connection.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("app start v" + tryVersion())
        settings = SettingsStore(this)
        val db = AppDatabase.get(this)
        repository = SessionRepository(db) { buildClient() }

        appScope.launch {
            try {
                settings.connection.collect { remote ->
                    if (_connection.value != remote) {
                        _connection.value = remote
                        if (remote.isValid) {
                            AppLog.i("restored saved server: ${remote.serverUrl}")
                        }
                    }
                }
            } catch (_: Exception) {
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

    private fun tryVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    fun resetConnection() {
        _connection.value = Connection()
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}