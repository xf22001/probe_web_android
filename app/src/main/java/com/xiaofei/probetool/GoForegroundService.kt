package com.xiaofei.probetool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.xiaofei.probetool.lib.probetool.Probetool
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone

class GoForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "ACTION_STOP"
        var isRunning = false
        const val ACTION_SERVICE_STATE_CHANGE = "com.xiaofei.probetool.SERVICE_STATE_CHANGE"
        const val EXTRA_IS_RUNNING = "IS_RUNNING"
    }

    private fun sendStateUpdateBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_SERVICE_STATE_CHANGE).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
        }
        sendBroadcast(intent)
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, ComponentName(this, MyTileService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            sendStateUpdateBroadcast(true)
            requestTileUpdate()
            // ... existing start logic ...
            createNotificationChannel()

            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

            val stopIntent = Intent(this, GoForegroundService::class.java).apply { action = ACTION_STOP }
            val stopPendingIntent =
                PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

            val notification: Notification = NotificationCompat.Builder(this, "GoServiceChannel")
                .setContentTitle("Probe Tool Service")
                .setContentText("Go backend is running...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .addAction(R.mipmap.ic_launcher_round, "停止", stopPendingIntent)
                .build()

            startForeground(1, notification)

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logDir = File(downloadDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val staticDir = File(filesDir, "static")
            if (!staticDir.exists()) {
                staticDir.mkdirs()
                try {
                    assets.list("static")?.forEach { fileName ->
                        assets.open("static/$fileName").use { input ->
                            val outputFile = File(staticDir, fileName)
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val currentTimeZone = TimeZone.getDefault().id // Get current timezone

            Thread {
                try {
                    Probetool.start(downloadDir!!.absolutePath, logDir.absolutePath, staticDir.absolutePath, currentTimeZone)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            isRunning = false
            sendStateUpdateBroadcast(false)
            requestTileUpdate()
            try {
                Probetool.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "GoServiceChannel",
                "Go Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}