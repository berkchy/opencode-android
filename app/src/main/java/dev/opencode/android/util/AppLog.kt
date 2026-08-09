package dev.opencode.android.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny file logger. Writes time-stamped lines to `filesDir/app.log` (rolling at
 * ~256 KB) and mirrors them to logcat so on-device errors can be inspected:
 *
 * - `adb logcat -s OpenCodeAndroid`
 * - `files/app.log` inside the app data directory
 */
object AppLog {

    private const val TAG = "OpenCode"
    private const val MAX_BYTES = 256 * 1024

    private var fileLabel: String = ""

    fun init(context: Context) {
        fileLabel = context.filesDir.absolutePath + "/app.log"
        d("init, app log: $fileLabel")
    }

    fun d(msg: String) = log(Log.DEBUG, msg)

    fun e(msg: String, t: Throwable? = null) = log(Log.ERROR, msg + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    fun i(msg: String) = log(Log.INFO, msg)

    fun tail(maxBytes: Int = 8192): String = try {
        val f = java.io.File(fileLabel)
        if (!f.exists()) return ""
        val size = f.length()
        val skip = (size - maxBytes).coerceAtLeast(0L)
        f.inputStream().use { it.skip(skip); it.readBytes().toString(Charsets.UTF_8) }.trim()
    } catch (_: Exception) {
        ""
    }

    private fun log(level: Int, msg: String) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$ts [$level] $msg"
            val f = java.io.File(fileLabel)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                f.createNewFile()
            }
            if (f.length() > MAX_BYTES) {
                f.writeText(line + "\n")
            } else {
                f.appendText(line + "\n")
            }
        } catch (_: Exception) {
        }
        if (level >= Log.ERROR) Log.e(TAG, msg) else Log.d(TAG, msg)
    }
}