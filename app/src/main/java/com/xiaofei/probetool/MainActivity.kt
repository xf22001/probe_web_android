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
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xiaofei.probetool.ui.theme.ProbetoolTheme

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val isServiceRunning by GoForegroundService.isRunning.collectAsState()

    // 添加一个状态来跟踪Web UI按钮的点击状态，用于实现点击效果
    var isWebButtonPressed by remember { mutableStateOf(false) }

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

    // 添加状态来跟踪日志文件选择底部表单的显示
    var showLogFileDialog by remember { mutableStateOf(false) }
    
    // 获取日志文件列表
    var logFiles by remember { mutableStateOf<Array<File>>(emptyArray()) }

    // 底部表单状态
    val bottomSheetState = rememberModalBottomSheetState()

    // 权限请求启动器
    val storageManagerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            hasStoragePermission = Environment.isExternalStorageManager()
        }
    )

    val standardPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasStoragePermission = permissions.all { it.value == true }
        }
    )

    // 加载日志文件列表
    fun loadLogFiles() {
        val logDir = File(context.getLogDirectory())
        if (logDir.exists()) {
            val files = logDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.toTypedArray()
            logFiles = files ?: emptyArray()
        } else {
            logFiles = emptyArray()
        }
    }

    // 分享选定的日志文件
    fun shareLogFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "*/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Log File").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Unable to share log file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

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
                hasStoragePermission = true
                // 权限已获得，获取日志文件列表并显示底部表单
                loadLogFiles()
                showLogFileDialog = true
            }
        } else { // Below Android 11
            val permissionsToRequest = listOf(Manifest.permission.READ_EXTERNAL_STORAGE).let { perms ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    perms + Manifest.permission.WRITE_EXTERNAL_STORAGE
                } else perms
            }.filter {
                ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (permissionsToRequest.isNotEmpty()) {
                standardPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                hasStoragePermission = true
                // 权限已获得，获取日志文件列表并显示底部表单
                loadLogFiles()
                showLogFileDialog = true
            }
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
            // 对于Android 11+，我们不添加传统存储权限，因为使用了MANAGE_EXTERNAL_STORAGE
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                        containerColor = Color(0xFF4CAF50), // 绿色背景 - 表示服务正在运行
                        contentColor = Color(0xFFE53935) // 红色文字 - 表示停止操作
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3), // 蓝色背景 - 表示服务停止
                        contentColor = Color(0xFFFFFFFF) // 白色文字 - 表示启动操作
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

            // 分享日志按钮
            Button(
                onClick = {
                    if (hasStoragePermission) {
                        loadLogFiles()
                        showLogFileDialog = true
                    } else {
                        checkAndRequestLogPermissions()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasStoragePermission) Color(0xFF2196F3) else Color.Gray, // 蓝色主题，权限未授予时显示灰色
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 40.dp)
            ) {
                Text(
                    text = "Share Logs",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }

        // 日志文件选择底部表单
        if (showLogFileDialog) {
            ModalBottomSheet(
                onDismissRequest = { showLogFileDialog = false },
                sheetState = bottomSheetState
            ) {
                LazyColumn {
                    items(logFiles.toList()) { file ->
                        ListItem(
                            headlineContent = { Text(text = file.name) },
                            supportingContent = {
                                Text(
                                    text = "Size: ${file.length()} bytes, Modified: ${
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                                    }"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLogFile(file)
                                    showLogFileDialog = false
                                }
                        )
                    }

                    if (logFiles.isEmpty()) {
                        item {
                            Text(
                                text = "No log files to share",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}