package com.xiaofei.probetool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class MyTileService : TileService() {

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GoForegroundService.ACTION_SERVICE_STATE_CHANGE) {
                updateTile()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val intentFilter = IntentFilter(GoForegroundService.ACTION_SERVICE_STATE_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStateReceiver, intentFilter)
        }
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterReceiver(serviceStateReceiver)
    }

    override fun onClick() {
        super.onClick()
        ServiceToggle.dispatch(this)
        // Optimistic UI update
        val tile = qsTile ?: return
        val isRunning = tile.state == Tile.STATE_ACTIVE
        if (isRunning) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Start Service"
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Service"
        }
        tile.updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = GoForegroundService.isRunning
        tile.state = if (isRunning) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.label = if (isRunning) {
            "Stop Service"
        } else {
            "Start Service"
        }
        tile.updateTile()
    }
}
