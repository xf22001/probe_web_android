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

data class ServerRuntimeState(
    val isRunning: Boolean = false,
    val isStarting: Boolean = false,
    val lastError: String = ""
)

/**
 * Manages the Go ProbeTool server lifecycle.
 * Runs directly in a background thread — no foreground Service needed
 * since the app does not require background execution.
 */
object GoServerRunner {

    private val _state = MutableStateFlow(ServerRuntimeState())
    val state: StateFlow<ServerRuntimeState> = _state.asStateFlow()

    private var serverThread: Thread? = null

    private fun prepareStaticFiles(context: Context): File {
        val staticDir = File(context.filesDir, "static")
        if (!staticDir.exists() && !staticDir.mkdirs()) {
            throw IllegalStateException("Failed to create static directory: ${staticDir.absolutePath}")
        }

        val files = context.assets.list("static")
            ?: throw IllegalStateException("Static assets are missing")
        if (files.isEmpty()) {
            throw IllegalStateException("Static assets are empty")
        }

        files.forEach { fileName ->
            context.assets.open("static/$fileName").use { input ->
                FileOutputStream(File(staticDir, fileName)).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return staticDir
    }

    fun start(context: Context) {
        if (_state.value.isRunning || _state.value.isStarting) return

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()

        val staticDir = try {
            prepareStaticFiles(context)
        } catch (e: Exception) {
            _state.value = ServerRuntimeState(lastError = e.message ?: "Failed to prepare static assets")
            return
        }

        val timezone = TimeZone.getDefault().id

        _state.value = ServerRuntimeState(isStarting = true)
        serverThread = Thread {
            try {
                Server.start(logDir.absolutePath, downloadDir.absolutePath, staticDir.absolutePath, timezone)
                val running = Server.isRunning()
                _state.value = ServerRuntimeState(
                    isRunning = running,
                    lastError = if (running) "" else Server.lastError().ifBlank { "Server failed to start" }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = ServerRuntimeState(lastError = e.message ?: "Server failed to start")
            }
        }.also { it.start() }
    }

    fun stop() {
        if (!_state.value.isRunning && !_state.value.isStarting) return
        try {
            Server.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val running = try {
            Server.isRunning()
        } catch (e: Exception) {
            false
        }
        _state.value = ServerRuntimeState(
            isRunning = running,
            lastError = if (running) Server.lastError() else ""
        )
        serverThread = null
    }
}
