package com.xiaofei.probetool

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceToggle {
    fun dispatch(context: Context) {
        val intent = Intent(context, GoForegroundService::class.java)
        if (GoForegroundService.isRunning.value) {
            context.stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
