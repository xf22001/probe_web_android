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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

// 辅助函数：获取日志目录路径
private fun Context.getLogDirectory(): String {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return "${downloadDir.absolutePath}/logs"
}

// 辅助函数：打开日志目录
private fun Context.openLogDirectory() {
    val logDirPath = getLogDirectory()
    val logDir = java.io.File(logDirPath)

    if (!logDir.exists()) {
        // 如果日志目录不存在，提示用户
        android.widget.Toast.makeText(this, "Log directory does not exist yet. Start the service first.", android.widget.Toast.LENGTH_LONG).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        val uri = Uri.parse(logDirPath)
        setDataAndType(uri, "resource/folder")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        startActivity(intent)
    } catch (e: Exception) {
        // 如果无法直接打开文件夹，则尝试使用文件管理器
        val safIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                   Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(safIntent)
        } catch (safException: Exception) {
            // 如果SAF也不行，就打开应用设置页面
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${this@openLogDirectory.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(settingsIntent)
        }
    }
}

// 辅助函数：分享日志文件
private fun Context.shareLogFiles() {
    val logDirPath = getLogDirectory()
    val logDir = java.io.File(logDirPath)

    if (!logDir.exists()) {
        // 如果日志目录不存在，提示用户
        android.widget.Toast.makeText(this, "Log directory does not exist yet. Start the service first.", android.widget.Toast.LENGTH_LONG).show()
        return
    }

    val logFiles = logDir.listFiles()
    if (logFiles.isNullOrEmpty()) {
        // 如果没有日志文件，提示用户
        android.widget.Toast.makeText(this, "No log files to share", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val uris = logFiles.map { file ->
        androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
    }

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        type = "*/*" // 通用类型，允许分享所有类型的文件
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    val chooserIntent = Intent.createChooser(shareIntent, "Share Log Files").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(this, "Unable to share log files: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val isServiceRunning by GoForegroundService.isRunning.collectAsState()

    // 添加一个状态来跟踪Web UI按钮的点击状态，用于实现点击效果
    var isWebButtonPressed by remember { mutableStateOf(false) }

    // 添加状态来跟踪日志按钮菜单的显示
    var showLogMenu by remember { mutableStateOf(false) }

    // 添加状态来跟踪存储权限是否已授予
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val storageManagerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { updatePermissionStatus() }
    )

    val standardPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { updatePermissionStatus() }
    )

    // 添加一个权限检查函数
    fun checkAndRequestLogPermissions() {
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                storageManagerPermissionLauncher.launch(intent)
            } else {
                // 权限已授予，可以执行日志操作
                hasStoragePermission = true
                showLogMenu = true
            }
        } else { // Below Android 11
            val permissionsToCheck = mutableListOf<String>()
            permissionsToCheck.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionsToCheck.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            val permissionsToRequest = permissionsToCheck.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (permissionsToRequest.isNotEmpty()) {
                standardPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                // 权限已授予，可以执行日志操作
                hasStoragePermission = true
                showLogMenu = true
            }
        }
    }

    // 更新权限状态的回调函数
    fun updatePermissionStatus() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        // 如果权限已授予，可以继续执行日志操作
        if (hasStoragePermission) {
            showLogMenu = true
        }
    }

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

        Spacer(modifier = Modifier.height(16.dp))

        // 日志管理按钮 - 包含打开和分享功能
        Box { // 使用Box容器来容纳按钮和下拉菜单
            Button(
                onClick = { checkAndRequestLogPermissions() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasStoragePermission) Color(0xFF795548) else Color.Gray, // 权限未授予时显示灰色
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 40.dp)
            ) {
                Text(
                    text = "Manage Logs",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }

            DropdownMenu(
                expanded = showLogMenu,
                onDismissRequest = { showLogMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open Log Directory") },
                    onClick = {
                        context.openLogDirectory()
                        showLogMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share Log Files") },
                    onClick = {
                        context.shareLogFiles()
                        showLogMenu = false
                    }
                )
            }
        }
    }
}
