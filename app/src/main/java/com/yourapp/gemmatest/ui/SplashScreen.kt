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
import kotlinx.coroutines.delay

/**
 * Full-screen splash shown right after the system SplashScreen hands off.
 *
 * Uses the RAW logo JPG directly — no transparency processing needed. Its
 * own background color (#1C2127, sampled directly from the source file)
 * matches `backgroundColor` below exactly, so an opaque covering box in the
 * same color reveals the logo with no visible seam, without needing an
 * alpha channel at all.
 *
 * The reveal is a simple height wipe: an opaque box the same color as the
 * background covers the full logo at start, then shrinks away top-to-bottom
 * to reveal it underneath.
 */
@Composable
fun NovaSplashScreen(
    onFinished: () -> Unit,
    backgroundColor: Color = Color(0xFF1C2127), // matches the JPG's own background exactly
    durationMillis: Int = 900,
    holdMillis: Long = 400,
) {
    var revealTriggered by remember { mutableFloatStateOf(0f) }

    // 0 = fully covered, 1 = fully revealed
    val revealProgress by animateFloatAsState(
        targetValue = revealTriggered,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "logo_reveal",
    )

    LaunchedEffect(Unit) {
        delay(150) // small beat before the wipe starts
        revealTriggered = 1f
        delay(durationMillis.toLong() + holdMillis)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(220.dp)) {
            Image(
                painter = painterResource(id = R.drawable.logo_illuminated),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // opaque cover, same color as background, shrinking away from
            // the top as revealProgress goes 0 -> 1, revealing the logo
            // underneath top-to-bottom
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
