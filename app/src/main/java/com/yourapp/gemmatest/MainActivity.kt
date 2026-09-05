package com.yourapp.gemmatest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EngineCheckScreen()
                }
            }
        }
    }
}

@Composable
fun EngineCheckScreen() {
    var log by remember { mutableStateOf("Tap the button to test engine load.") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "LiteRT-LM Thread Count Test (7 threads)")
        Button(onClick = {
            log = "Starting...\n"
            scope.launch {
                runEngineTest(context) { message -> log += message + "\n" }
            }
        }) {
            Text("Test A: Read from Downloads, 7 threads")
        }
        Text(text = log)
    }
}

suspend fun runEngineTest(context: Context, log: (String) -> Unit) {
    val modelPath = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm"

    val modelFile = File(modelPath)
    log("Checking model file...")
    if (!modelFile.exists()) {
        log("ERROR: model file not found at $modelPath")
        return
    }
    log("Model file found: ${modelFile.length() / 1024 / 1024} MB")

    withContext(Dispatchers.IO) {
        try {
            log("Creating engine with CPU backend, 7 threads...")
            val startTime = System.currentTimeMillis()

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(threadCount = 7),
                cacheDir = context.cacheDir.path,
            )
            val engine = Engine(engineConfig)
            engine.initialize()

            val loadTime = (System.currentTimeMillis() - startTime) / 1000.0
            log("Engine initialized in ${loadTime}s")

            log("Creating conversation...")
            val conversation = engine.createConversation()

            log("Sending test prompt...")
            val promptStart = System.currentTimeMillis()

            val response = conversation.sendMessage("What is the tallest building in the world?")

            val promptTime = (System.currentTimeMillis() - promptStart) / 1000.0
            log("Response received in ${promptTime}s:")
            log(response.toString())

            engine.close()
            log("Done. Engine closed.")

        } catch (e: Exception) {
            log("CAUGHT ERROR: ${e.message}")
            log("Type: ${e.javaClass.simpleName}")
        }
    }
}
