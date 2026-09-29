package com.yourapp.gemmatest.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yourapp.gemmatest.R
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// Blocks until the engine is fully warm, per the "block startup" decision —
// waits for max(reveal animation, warmup completion). On a cold engine
// init this can be much longer than the animation itself.
@Composable
fun NovaSplashScreen(
    onFinished: () -> Unit,
    warmup: suspend () -> Unit,
    backgroundColor: Color = Color(0xFF1C2127),
    durationMillis: Int = 900,
    holdMillis: Long = 400,
    warmupStartDelayMillis: Long = 200,
) {
    var revealTriggered by remember { mutableFloatStateOf(0f) }

    val revealProgress by animateFloatAsState(
        targetValue = revealTriggered,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "logo_reveal",
    )

    LaunchedEffect(Unit) {
        delay(150)
        revealTriggered = 1f

        coroutineScope {
            val animationDone = async { delay(durationMillis.toLong() + holdMillis) }
            val warmupDone = async {
                delay(warmupStartDelayMillis)
                try {
                    warmup()
                } catch (e: Exception) {
                    // Setup already verified the model file, so this is
                    // unexpected if it happens — proceed anyway rather than
                    // block forever; NovaApp's cold-start hint covers a
                    // not-actually-ready engine
                }
            }
            awaitAll(animationDone, warmupDone)
        }

        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(220.dp)) {
            Image(
                painter = painterResource(id = R.drawable.logo_illuminated),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(1f - revealProgress)
                    .align(Alignment.TopStart)
                    .background(backgroundColor)
            )
        }
    }
}
