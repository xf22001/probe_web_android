package com.xiaofei.probetool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            val serviceIntent = Intent(context, GoForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }) {
            Text("Start Service")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val serviceIntent = Intent(context, GoForegroundService::class.java)
            context.stopService(serviceIntent)
        }) {
            Text("Stop Service")
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
