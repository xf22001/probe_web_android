package com.xiaofei.probetool

import android.content.Context
import android.os.Environment
import com.xiaofei.probetool.server.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone

/**
 * Manages the Go ProbeTool server lifecycle.
 * Runs directly in a background thread — no foreground Service needed
 * since the app does not require background execution.
 */
object GoServerRunner {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var serverThread: Thread? = null

    fun start(context: Context) {
        if (_isRunning.value) return

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()

        val staticDir = File(context.filesDir, "static")
        if (!staticDir.exists()) {
            staticDir.mkdirs()
        }
        try {
            context.assets.list("static")?.forEach { fileName ->
                context.assets.open("static/$fileName").use { input ->
                    FileOutputStream(File(staticDir, fileName)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val timezone = TimeZone.getDefault().id

        _isRunning.value = true
        serverThread = Thread {
            try {
                Server.start(logDir.absolutePath, downloadDir.absolutePath, staticDir.absolutePath, timezone)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.also { it.start() }
    }

    fun stop() {
        if (!_isRunning.value) return
        try {
            Server.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isRunning.value = false
        serverThread = null
    }
}
