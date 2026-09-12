package com.yourapp.gemmatest.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.theme.LocalNovaColors

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val colors = LocalNovaColors.current
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(modifier = modifier.fillMaxSize().background(colors.Bg0)) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.75f)
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationX = 60f + drift * 120f
                    translationY = 40f + drift * 160f
                    alpha = 0.22f
                }
                .background(Brush.radialGradient(listOf(colors.Blue500, Color.Transparent)), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = -60f - drift * 100f
                    translationY = -40f - drift * 140f
                    alpha = 0.18f
                }
                .background(Brush.radialGradient(listOf(colors.Cyan, Color.Transparent)), shape = CircleShape)
        )
    }
}

@Composable
fun PulsingDot() {
    val colors = LocalNovaColors.current
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Box(
        modifier = Modifier.size(11.dp).scale(scale).clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.Cyan, colors.Blue500)))
    )
}

@Composable
fun HeaderIconButton(icon: ImageVector, contentDescription: String, tint: Color? = null, onClick: () -> Unit) {
    val colors = LocalNovaColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.Surface)
            .border(1.dp, colors.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: colors.Text1, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun AppHeader(
    onToggleSidebar: () -> Unit,
    onNewChat: () -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean,
    isGenerating: Boolean,
) {
    val colors = LocalNovaColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.Bg1.copy(alpha = 0.9f))
                .border(width = 1.dp, color = colors.Line)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderIconButton(icon = Icons.Filled.Menu, contentDescription = "History", onClick = onToggleSidebar)
                PulsingDot()
                Text("Nova", color = colors.Text0, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.Surface)
                        .border(1.dp, colors.Line, RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleTheme),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isDark) "\u2600" else "\u263E", fontSize = 15.sp, color = colors.Text1)
                }
                HeaderIconButton(icon = Icons.Filled.Add, contentDescription = "New chat", tint = colors.Blue300, onClick = onNewChat)
            }
        }
        if (isGenerating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.Bg1)
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                AnimatedDots()
            }
        }
    }
}
