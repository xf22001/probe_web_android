package com.xiaofei.probetool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class GoForegroundService : Service() {

    companion object {
        init {
            System.loadLibrary("gojni")
        }

        @JvmStatic
        external fun Start(ftpDir: String, logDir: String, staticDir: String)
        @JvmStatic
        external fun Stop()
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize and start the Go backend here
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, "GoServiceChannel")
            .setContentTitle("Probe Tool Service")
            .setContentText("Go backend is running...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)

        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(downloadDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val staticDir = File(filesDir, "static")
        if (!staticDir.exists()) {
            staticDir.mkdirs()
            assets.list("static")?.forEach { fileName ->
                assets.open("static/$fileName").use { input ->
                    val outputFile = File(staticDir, fileName)
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        // This is where you would call your Go function, passing the paths
        Thread {
            Start(downloadDir!!.absolutePath, logDir.absolutePath, staticDir.absolutePath)
        }.start()


        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the Go backend here
        Stop()
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
