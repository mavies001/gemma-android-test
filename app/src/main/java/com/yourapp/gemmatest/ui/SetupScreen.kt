package com.yourapp.gemmatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.NovaApplication
import com.yourapp.gemmatest.engine.modelFile
import com.yourapp.gemmatest.theme.LocalNovaColors

private const val REQUIRED_FREE_BYTES = 1_288_490_188L // ~1.2 GiB

private sealed class SetupState {
    object CheckingSpace : SetupState()
    object InsufficientSpace : SetupState()
    data class Downloading(val downloaded: Long, val total: Long) : SetupState()
    object Verifying : SetupState()
    object VerifyFailed : SetupState()
    object Warming : SetupState()
    data class Failed(val message: String) : SetupState()
}

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val colors = LocalNovaColors.current
    val context = LocalContext.current
    val application = context.applicationContext as NovaApplication
    val downloader = remember { application.modelDownloader }
    var state by remember { mutableStateOf<SetupState>(SetupState.CheckingSpace) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        state = SetupState.CheckingSpace
        if (downloader.freeSpaceBytes() < REQUIRED_FREE_BYTES) {
            state = SetupState.InsufficientSpace
            return@LaunchedEffect
        }

        try {
            downloader.download { downloaded, total ->
                state = SetupState.Downloading(downloaded, total)
            }
        } catch (e: Exception) {
            state = SetupState.Failed("Setup couldn't finish downloading. Check your connection and try again.")
            return@LaunchedEffect
        }

        state = SetupState.Verifying
        val verified = try { downloader.verify() } catch (e: Exception) { false }
        if (!verified) {
            modelFile(context).delete()
            state = SetupState.VerifyFailed
            return@LaunchedEffect
        }

        state = SetupState.Warming
        try {
            application.engineHolder.warmup()
        } catch (e: Exception) {
            state = SetupState.Failed("Setup finished downloading but couldn't start the model. Try again.")
            return@LaunchedEffect
        }

        onSetupComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.Bg0).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(colors.Blue500, colors.Cyan))),
                contentAlignment = Alignment.Center
            ) { Text("\u2726", fontSize = 26.sp) }
            Spacer(Modifier.height(20.dp))
            Text("Setting up Irachat", color = colors.Text0, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is SetupState.CheckingSpace ->
                    Text("Checking available storage...", color = colors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)

                is SetupState.InsufficientSpace -> {
                    Text(
                        "Irachat needs about 1.2GB of free space to set up. Please free up some space and try again.",
                        color = colors.Text2, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    RetryButton { attempt++ }
                }

                is SetupState.Downloading -> {
                    val progress = if (s.total > 0) s.downloaded.toFloat() / s.total.toFloat() else 0f
                    Text("Downloading the model...", color = colors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = colors.Blue500, trackColor = colors.Surface2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${s.downloaded / (1024 * 1024)} MB of ${s.total / (1024 * 1024)} MB",
                        color = colors.Text2, fontSize = 11.5.sp
                    )
                }

                is SetupState.Verifying ->
                    Text("Verifying download...", color = colors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)

                is SetupState.VerifyFailed -> {
                    Text(
                        "The download didn't verify correctly. This can happen on an unstable connection. Let's try again.",
                        color = colors.Text2, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    RetryButton { attempt++ }
                }

                is SetupState.Warming -> {
                    Text("Getting the model ready...", color = colors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tip: for the best experience, consider closing other apps running in the background.",
                        color = colors.Text2, fontSize = 11.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center
                    )
                }

                is SetupState.Failed -> {
                    Text(s.message, color = colors.Text2, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    RetryButton { attempt++ }
                }
            }
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    val colors = LocalNovaColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(colors.Blue500, colors.Cyan)))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Text("Retry", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
