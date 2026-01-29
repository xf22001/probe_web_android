package com.xiaofei.probetool

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofei.probetool.ui.theme.ProbetoolTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProbetoolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControlScreen()
                }
            }
        }
    }
}

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val isServiceRunning by GoForegroundService.isRunning.collectAsState()

    // 添加一个状态来跟踪Web UI按钮的点击状态，用于实现点击效果
    var isWebButtonPressed by remember { mutableStateOf(false) }

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
        // 启动/停止服务按钮 - 具有颜色变化效果
        Button(
            onClick = {
                ServiceToggle.dispatch(context)
            },
            colors = if (isServiceRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9E9E9E), // 灰色 - 停止服务
                    contentColor = Color.White
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50), // 绿色 - 启动服务
                    contentColor = Color.White
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 40.dp)
        ) {
            Text(
                text = if (isServiceRunning) "Stop Service" else "Start Service",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Open Web UI 按钮 - 具有点击效果
        Button(
            onClick = {
                // 设置按下状态以提供视觉反馈
                isWebButtonPressed = true
                // 延迟重置状态以确保用户能看到效果
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isWebButtonPressed = false
                }, 150) // 150毫秒后重置状态

                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8000"))
                context.startActivity(browserIntent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isWebButtonPressed) Color(0xFF64B5F6) else Color(0xFF2196F3), // 蓝色调
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 40.dp)
        ) {
            Text(
                text = "Open Web UI",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}
