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
import kotlin.collections.buildMap

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
    private val loaderFile: File get() = File(binaryDir, "lib/ld-musl-aarch64.so.1")
    private val workspaceDir: File get() = File(context.filesDir, "workspace")
    private val configFile: File get() = File(workspaceDir, "opencode.json")

    val binaryBundled: Boolean
        get() = try {
            context.assets.open("opencode_bin/opencode").close()
            true
        } catch (_: Exception) {
            false
        }

    private val homeDir: File get() = File(context.filesDir, "home")
    private val serverLog: File get() = File(context.filesDir, "server.log")
    private val lastErrorFile: File get() = File(context.filesDir, "last_error.txt")

    fun lastError(): String = try {
        if (lastErrorFile.exists()) lastErrorFile.readText().trim() else ""
    } catch (_: Exception) {
        ""
    }

    private fun rememberFailure(prefs: EmbeddedPrefs, message: String) {
        try {
            var diag = ""
            val d = File(context.filesDir, "diag.txt")
            if (d.exists()) diag = d.readText().trim()
            lastErrorFile.writeText(
                buildString {
                    appendLine("Timestamp: ${System.currentTimeMillis()}")
                    appendLine("model=${prefs.model}")
                    if (diag.isNotEmpty()) appendLine(diag)
                    appendLine("msg=$message")
                    val t = logTail(4096)
                    if (t.isNotEmpty()) append("log:\n").append(t)
                },
            )
        } catch (_: Exception) {
        }
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
                    workspaceDir.mkdirs()
                    homeDir.mkdirs()
                    exec("chmod", "775", workspaceDir.absolutePath)
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
                val failMsg = buildString {
                    append(e.message ?: "Sunucu açılamadı")
                    val tail = logTail()
                    if (tail.isNotEmpty()) {
                        append("\n\nSon log:\n")
                        append(tail)
                    }
                }
                rememberFailure(prefs, failMsg)
                _status.value = Status.Failed(failMsg)
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
        // Android umask/File API may leave files non-executable; force 0755
        // across dir + files and verify the result actually sticks.
        exec("chmod", "775", binaryDir.absolutePath)
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
        copyAssetDir("opencode_bin/lib", File(binaryDir, "lib"))
        File(binaryDir, "lib").let { d ->
            if (d.exists()) exec("chmod", "775", d.absolutePath)
        }
        listOf(binaryFile, loaderFile)
            .filter { it.exists() }
            .forEach { f ->
                exec("chmod", "755", f.absolutePath)
                f.setReadable(true, false)
                f.setExecutable(true, false)
                if (!f.canExecute()) {
                    error("Exec izni ayarlanamadı: ${f.absolutePath}")
                }
            }
        commitDiagnostics()
    }

    private fun commitDiagnostics() {
        try {
            val probe = execQuiet("/system/bin/sh", "-c", "echo exec-ok")
            File(context.filesDir, "diag.txt").writeText(
                "binary=${binaryFile.canExecute()} size=${binaryFile.length()}\n" +
                    "loader=${loaderFile.canExecute()} size=${loaderFile.length()}\n" +
                    "dir=${binaryDir.canRead()}/${binaryDir.canExecute()}\n" +
                    "probe=${probe}\n",
            )
        } catch (_: Exception) {
        }
    }

    private fun exec(vararg cmd: String) {
        try {
            ProcessBuilder(*cmd).redirectErrorStream(true).start()
                .apply { waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
        } catch (_: Exception) {
            // chmod may be unavailable; File.set* below still runs
        }
    }

    private fun execQuiet(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        out.trim()
    } catch (e: Exception) {
        "exec-err:${e.message}"
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        try {
            context.assets.list(assetPath)?.forEach { child ->
                val childPath = "$assetPath/$child"
                val isDir = context.assets.list(childPath)?.isNotEmpty() ?: false
                if (isDir) {
                    copyAssetDir(childPath, File(destDir, child))
                } else {
                    val out = File(destDir, child)
                    if (!out.exists() || out.length() == 0L) {
                        out.parentFile?.mkdirs()
                        context.assets.open(childPath).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // no lib dir bundled -> the binary may be a static build
        }
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
        val libDir = File(binaryDir, "lib")
        val env = buildMap {
            put("TMPDIR", File(homeDir, "tmp").absolutePath)
            put("HOME", homeDir.absolutePath)
            put("XDG_DATA_HOME", File(homeDir, ".local/share").absolutePath)
            put("XDG_CONFIG_HOME", File(homeDir, ".config").absolutePath)
            put("XDG_CACHE_HOME", File(homeDir, ".cache").absolutePath)
            put("XDG_STATE_HOME", File(homeDir, ".local/state").absolutePath)
            put("OPENCODE_CONFIG", configFile.absolutePath)
            if (prefs.apiKey.isNotBlank()) put("OPENCODE_API_KEY", prefs.apiKey)
            put("OPENCODE_SERVER_USERNAME", "opencode")
        }
        env.keys.forEach {
            File(env.getValue(it)).apply { parentFile?.mkdirs(); mkdirs() }
        }

        val args = listOf(
            "--hostname", "127.0.0.1",
            "--port", port.toString(),
            "--log-level", "warn",
        )
        // The musl build is dynamically linked: run it through the bundled
        // musl loader with its lib dir on the search path.
        val cmd = if (loaderFile.exists()) {
            listOf(loaderFile.absolutePath, binaryFile.absolutePath) + args
        } else {
            listOf(binaryFile.absolutePath) + args
        }
        val pb = ProcessBuilder(cmd)
        pb.directory(workspaceDir)
        pb.environment().putAll(env)
        if (libDir.exists()) {
            pb.environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
        }
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p
        // Keep a rolling server log so failures can be diagnosed.
        val logFile = serverLog
        Thread {
            val writer = java.io.FileWriter(logFile, true)
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    writer.appendLine(line)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    writer.flush()
                    writer.close()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun logTail(maxBytes: Int = 4096): String {
        return try {
            if (!serverLog.exists()) return ""
            val s = serverLog.length()
            val skip = (s - maxBytes).coerceAtLeast(0L)
            serverLog.inputStream().use { input ->
                input.skip(skip)
                input.readBytes().toString(Charsets.UTF_8)
            }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun waitForHealth(port: Int) {
        var dead = false
        var exit: Int? = null
        for (i in 0 until 200) {
            if (_status.value !is Status.Starting) return
            if (process?.isAlive == false) {
                dead = true
                exit = try {
                    process?.exitValue()
                } catch (_: Exception) {
                    null
                }
                break
            }
            if (pingHealth(port)) return
            delay(750)
        }
        if (dead) {
            throw RuntimeException(when (exit) {
                159 -> "Bu cihazın seccomp/sistem politikası bu sunucuyu engelliyor (SIGSYS). " +
                    "Bun tabanlı binary Android 12+ (API 31) gerektirir; mevcut sürüm: API " +
                    android.os.Build.VERSION.SDK_INT + ". Gömülü sunucu bu cihazda çalışmaz."
                127 -> "ld-musl yükleyici bulunamadı (lib/ eksik?)"
                else -> "Gömülü sunucu işlemi beklenmedik şekilde kapandı (exit=${exit ?: "?"})"
            })
        }
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