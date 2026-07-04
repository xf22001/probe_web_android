package com.xiaofei.probetool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xiaofei.probetool.ui.theme.ProbetoolTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProbetoolTheme {
                ProbeToolApp()
            }
        }
    }
}

private fun Context.getLogDirectory(): String {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return "${downloadDir.absolutePath}/logs"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeToolApp() {
    val context = LocalContext.current
    val isServiceRunning by GoForegroundService.isRunning.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Log export state
    var showLogSheet by remember { mutableStateOf(false) }
    var logFiles by remember { mutableStateOf<Array<File>>(emptyArray()) }
    val bottomSheetState = rememberModalBottomSheetState()

    // Storage permission
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val storageManagerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = Environment.isExternalStorageManager()
    }

    val standardPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.all { it.value == true }
    }

    fun loadLogFiles() {
        val logDir = File(context.getLogDirectory())
        if (logDir.exists()) {
            logFiles = logDir.listFiles()?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }?.toTypedArray() ?: emptyArray()
        } else {
            logFiles = emptyArray()
        }
    }

    fun shareLogFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "*/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Log File").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Unable to share log file: ${e.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestLogPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                storageManagerPermissionLauncher.launch(intent)
            } else {
                hasStoragePermission = true
                loadLogFiles()
                showLogSheet = true
            }
        } else {
            val permissionsToRequest = listOf(Manifest.permission.READ_EXTERNAL_STORAGE).let { perms ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    perms + Manifest.permission.WRITE_EXTERNAL_STORAGE
                } else perms
            }.filter {
                ContextCompat.checkSelfPermission(context, it) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (permissionsToRequest.isNotEmpty()) {
                standardPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                hasStoragePermission = true
                loadLogFiles()
                showLogSheet = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                storageManagerPermissionLauncher.launch(intent)
            }
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            standardPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))

                // — Service status card —
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceRunning)
                            Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isServiceRunning) "Service Running" else "Service Stopped",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isServiceRunning) "http://127.0.0.1:8000" else "Tap to start",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                ServiceToggle.dispatch(context)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) Color(0xFFE53935) else Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(
                                if (isServiceRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isServiceRunning) "STOP SERVICE" else "START SERVICE",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                // — Export Logs —
                NavigationDrawerItem(
                    icon = {
                        Icon(Icons.Default.Share, contentDescription = null)
                    },
                    label = { Text("Export Logs") },
                    selected = false,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (hasStoragePermission) {
                            loadLogFiles()
                            showLogSheet = true
                        } else {
                            checkAndRequestLogPermissions()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0x99000000)
                    )
                )
            }
        ) { padding ->
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                    }
                },
                update = { webView ->
                    val targetUrl = if (isServiceRunning) "http://127.0.0.1:8000" else "stopped"
                    val currentTag = webView.getTag(android.R.id.hint) as? String

                    // Only reload when the target actually changes
                    if (currentTag != targetUrl) {
                        webView.setTag(android.R.id.hint, targetUrl)
                        if (isServiceRunning) {
                            // Delay to give the Go server time to start
                            webView.postDelayed({
                                webView.loadUrl("http://127.0.0.1:8000")
                            }, 1500)
                        } else {
                            val stoppedHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                                <body style="background:#1a1a2e;color:#ccc;display:flex;align-items:center;justify-content:center;height:100vh;font-family:-apple-system,sans-serif;margin:0">
                                <div style="text-align:center">
                                    <div style="font-size:48px;margin-bottom:16px">&#x25A0;</div>
                                    <h2 style="color:#eee;margin:0">Service Stopped</h2>
                                    <p style="color:#888;margin-top:8px">&#x2630; Open menu to start</p>
                                </div>
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, stoppedHtml, "text/html", "UTF-8", null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    // — Log export bottom sheet —
    if (showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogSheet = false },
            sheetState = bottomSheetState
        ) {
            LazyColumn {
                items(logFiles.toList()) { file ->
                    ListItem(
                        headlineContent = { Text(text = file.name) },
                        supportingContent = {
                            Text(
                                text = "Size: ${file.length()} bytes, Modified: ${
                                    java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(file.lastModified()))
                                }"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shareLogFile(file)
                                showLogSheet = false
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
