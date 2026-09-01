package com.yourapp.gemmatest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PipelineCheckScreen()
                }
            }
        }
    }
}

@Composable
fun PipelineCheckScreen() {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "Build pipeline OK.")
        Text(text = "If you can read this, Modal -> Gradle -> APK -> Termux install worked.")
        Text(text = "No model loading yet - this is intentionally bare.")
    }
}
