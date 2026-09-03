package com.yourapp.gemmatest

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "LiteRT-LM Native Engine Test")
        Button(onClick = {
            log = "Starting...\n"
            scope.launch {
                runEngineTest { message -> log += message + "\n" }
            }
        }) {
            Text("Load model + run test prompt")
        }
        Text(text = log)
    }
}

suspend fun runEngineTest(log: (String) -> Unit) {
    // Model path: reading directly from shared Downloads, matching where
    // it already sits on the device from earlier manual testing.
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
            log("Creating engine with CPU backend...")
            val startTime = System.currentTimeMillis()

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU,
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
