package com.xiaofei.probetool

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaofei.probetool.ui.theme.ProbetoolTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(GoForegroundService.isRunning)

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GoForegroundService.ACTION_SERVICE_STATE_CHANGE) {
                isServiceRunning = intent.getBooleanExtra(GoForegroundService.EXTRA_IS_RUNNING, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProbetoolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControlScreen(
                        isServiceRunning = isServiceRunning,
                        onToggleService = { newState -> isServiceRunning = newState }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(GoForegroundService.ACTION_SERVICE_STATE_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStateReceiver, intentFilter)
        }
        isServiceRunning = GoForegroundService.isRunning
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(serviceStateReceiver)
    }
}

@Composable
fun ControlScreen(isServiceRunning: Boolean, onToggleService: (Boolean) -> Unit) {
    val context = LocalContext.current

    val storageManagerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { /* You can check permission status again here if needed */ }
    )

    val standardPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { /* Handle results */ }
    )

    LaunchedEffect(key1 = true) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                storageManagerPermissionLauncher.launch(intent)
            }
        } else { // Below Android 11
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            standardPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            val serviceIntent = Intent(context, GoForegroundService::class.java)
            if (isServiceRunning) {
                context.stopService(serviceIntent)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            onToggleService(!isServiceRunning)
        }) {
            Text(if (isServiceRunning) "Stop Service" else "Start Service")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8000"))
            context.startActivity(browserIntent)
        }) {
            Text("Open Web UI")
        }
    }
}
