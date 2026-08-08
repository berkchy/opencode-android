package dev.opencode.android.server

import android.content.Context
import dev.opencode.android.data.prefs.EmbeddedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL

/**
 * Runs the bundled opencode binary (opencode-linux-arm64-musl) as a local
 * child process and exposes its HTTP server on 127.0.0.1 so the app can
 * connect without any external server.
 */
class OpenCodeServerManager(private val context: Context) {

    sealed class Status {
        object Stopped : Status()
        object Starting : Status()
        data class Running(val port: Int) : Status()
        data class Failed(val message: String) : Status()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Status>(Status.Stopped)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var process: Process? = null
    private var lastPrefs: EmbeddedPrefs? = null

    private val binaryDir: File get() = File(context.filesDir, "ocbin")
    private val binaryFile: File get() = File(binaryDir, "opencode")
    private val workspaceDir: File get() = File(context.filesDir, "workspace")
    private val configFile: File get() = File(workspaceDir, "opencode.json")

    val binaryBundled: Boolean
        get() = try {
            context.assets.open("opencode_bin/opencode").close()
            true
        } catch (_: Exception) {
            false
        }

    fun start(prefs: EmbeddedPrefs) {
        if (_status.value is Status.Running || _status.value is Status.Starting) return
        lastPrefs = prefs
        _status.value = Status.Starting
        scope.launch {
            runCatching { ensureBinary() }.onFailure {
                _status.value = Status.Failed(
                    "Gömülü sunucu başlatılamadı: ${it.message}",
                )
                return@launch
            }
            val port = findFreePort()
            try {
                withContext(Dispatchers.IO) {
                    writeConfig(prefs)
                    startProcess(prefs, port)
                }
                waitForHealth(port)
                if (_status.value is Status.Starting) {
                    _status.value = Status.Running(port)
                }
            } catch (e: Exception) {
                process?.let { kill(it) }
                process = null
                _status.value = Status.Failed(e.message ?: "Sunucu açılamadı")
            }
        }
    }

    fun stop() {
        scope.launch {
            process?.let { kill(it) }
            process = null
            _status.value = Status.Stopped
        }
    }

    fun stopSync() {
        process?.let { kill(it) }
        process = null
        _status.value = Status.Stopped
    }

    fun restart() {
        val prefs = lastPrefs ?: return
        stopSync()
        start(prefs)
    }

    private suspend fun ensureBinary() = withContext(Dispatchers.IO) {
        binaryDir.mkdirs()
        if (!binaryFile.exists() || binaryFile.length() == 0L) {
            if (!binaryBundled) {
                error(
                    "Gömülü opencode binary'si APK içinde yok. " +
                        "Lütfen yeniden derlenmiş bir kurulum kullan.",
                )
            }
            context.assets.open("opencode_bin/opencode").use { input ->
                binaryFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        binaryFile.setExecutable(true, true)
    }

    private fun writeConfig(prefs: EmbeddedPrefs) {
        workspaceDir.mkdirs()
        val root: JsonObject = buildJsonObject {
            put("model", prefs.model)
            put(
                "provider",
                buildJsonObject {
                    if (prefs.apiKey.isNotBlank()) {
                        put("opencode", buildJsonObject {
                            put("apiKey", prefs.apiKey)
                        })
                    } else {
                        put("opencode", JsonObject(emptyMap()))
                    }
                },
            )
        }
        configFile.writeText(root.toString())
    }

    private fun startProcess(prefs: EmbeddedPrefs, port: Int) {
        val env = mapOf("OPENCODE_API_KEY" to prefs.apiKey).filterValues { it.isNotBlank() }
        val pb = ProcessBuilder(
            binaryFile.absolutePath,
            "--hostname", "127.0.0.1",
            "--port", port.toString(),
            "--log-level", "warn",
        )
        pb.directory(workspaceDir)
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p
        // Drain stdout/stderr so the pipe never fills and blocks the child.
        Thread {
            p.inputStream.bufferedReader().forEachLine { }
        }.start()
    }

    private suspend fun waitForHealth(port: Int) {
        var dead = false
        for (i in 0 until 200) {
            if (_status.value !is Status.Starting) return
            if (process?.isAlive == false) {
                dead = true
                break
            }
            if (pingHealth(port)) return
            delay(750)
        }
        if (dead) throw RuntimeException("Gömülü sunucu işlemi beklenmedik şekilde kapandı")
        throw RuntimeException("Gömülü sunucu zaman aşımı: health yanıtı yok")
    }

    private fun pingHealth(port: Int): Boolean = try {
        val conn = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.requestMethod = "GET"
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    private fun findFreePort(): Int =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    private fun kill(p: Process) {
        p.destroy()
        try {
            if (p.isAlive) p.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (p.isAlive) p.destroyForcibly()
        } catch (_: Exception) {
            p.destroyForcibly()
        }
    }
}