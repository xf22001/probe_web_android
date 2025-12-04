package com.xiaofei.probetool

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class MyTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        job = scope.launch {
            GoForegroundService.isRunning.collect {
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
    }

    override fun onClick() {
        super.onClick()
        ServiceToggle.dispatch(this)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = GoForegroundService.isRunning.value
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
