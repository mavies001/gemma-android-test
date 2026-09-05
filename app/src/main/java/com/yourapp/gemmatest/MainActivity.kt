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
    var log by remember { mutableStateOf("Pick a test to run.") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "LiteRT-LM Storage Location Comparison")

        Button(onClick = {
            log = "Starting Test A (Downloads path)...\n"
            scope.launch {
                runEngineTest(
                    modelPath = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm",
                    context = context,
                    label = "A (Downloads)",
                ) { message -> log += message + "\n" }
            }
        }) {
            Text("Test A: Read from Downloads")
        }

        Button(onClick = {
            log = "Starting Test B (internal storage)...\n"
            scope.launch {
                val internalFile = File(context.filesDir, "gemma3-1b-it-int4.litertlm")
                val sourceFile = File("/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm")

                if (!internalFile.exists() || internalFile.length() != sourceFile.length()) {
                    log += "Copying model into internal storage (one-time)...\n"
                    val copyStart = System.currentTimeMillis()
                    sourceFile.copyTo(internalFile, overwrite = true)
                    val copyTime = (System.currentTimeMillis() - copyStart) / 1000.0
                    log += "Copy finished in ${copyTime}s\n"
                } else {
                    log += "Model already present in internal storage, skipping copy.\n"
                }

                runEngineTest(
                    modelPath = internalFile.absolutePath,
                    context = context,
                    label = "B (Internal)",
                ) { message -> log += message + "\n" }
            }
        }) {
            Text("Test B: Read from Internal Storage")
        }

        Text(text = log)
    }
}

suspend fun runEngineTest(
    modelPath: String,
    context: Context,
    label: String,
    log: (String) -> Unit,
) {
    val modelFile = File(modelPath)
    log("[$label] Checking model file...")
    if (!modelFile.exists()) {
        log("[$label] ERROR: model file not found at $modelPath")
        return
    }
    log("[$label] Model file found: ${modelFile.length() / 1024 / 1024} MB")

    withContext(Dispatchers.IO) {
        try {
            log("[$label] Creating engine with CPU backend...")
            val startTime = System.currentTimeMillis()

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
            val engine = Engine(engineConfig)
            engine.initialize()

            val loadTime = (System.currentTimeMillis() - startTime) / 1000.0
            log("[$label] Engine initialized in ${loadTime}s")

            val conversation = engine.createConversation()

            log("[$label] Sending test prompt...")
            val promptStart = System.currentTimeMillis()

            val response = conversation.sendMessage("What is the tallest building in the world?")

            val promptTime = (System.currentTimeMillis() - promptStart) / 1000.0
            log("[$label] Response received in ${promptTime}s:")
            log("[$label] $response")

            engine.close()
            log("[$label] Done. Engine closed.")

        } catch (e: Exception) {
            log("[$label] CAUGHT ERROR: ${e.message}")
            log("[$label] Type: ${e.javaClass.simpleName}")
        }
    }
}
