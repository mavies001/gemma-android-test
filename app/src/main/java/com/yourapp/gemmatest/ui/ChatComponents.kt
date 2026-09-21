package com.yourapp.gemmatest.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.markdown.MarkdownText
import com.yourapp.gemmatest.model.ChatMsg
import com.yourapp.gemmatest.model.Role
import com.yourapp.gemmatest.theme.LocalNovaColors
import kotlinx.coroutines.delay

@Composable
fun AnimatedDots() {
    val colors = LocalNovaColors.current
    var dotCount by remember { mutableStateOf(1) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = (dotCount % 3) + 1
        }
    }
    Text(".".repeat(dotCount), color = colors.Text2, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
}

@Composable
fun ChatMessageRow(msg: ChatMsg) {
    val colors = LocalNovaColors.current
    val isUser = msg.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            SelectionContainer(modifier = Modifier.weight(1f, fill = false).widthIn(max = 620.dp)) {
                MarkdownText(raw = msg.text, textColor = colors.Text0, streaming = msg.streaming)
            }
        } else {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(colors.Blue500, Color(0xFF4F7DFA))))
                        .padding(horizontal = 15.dp, vertical = 12.dp)
                ) {
                    Text(msg.text, color = Color.White, fontSize = 14.5.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

// "Typing" label + up to 20 dots filling in across two rows, then resetting
// and looping — replaces the previous shimmer-skeleton indicator
@Composable
fun TypingIndicator(showColdStartHint: Boolean = false) {
    val colors = LocalNovaColors.current
    var visibleCount by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            visibleCount = (visibleCount + 1) % 21
        }
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Typing", color = colors.Text2, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 10) {
                        val dotIndex = row * 10 + col
                        val visible = dotIndex < visibleCount
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (visible) colors.Blue500 else colors.Line)
                        )
                    }
                }
            }
        }
        if (showColdStartHint) {
            Spacer(Modifier.height(8.dp))
            Text(
                "First response may take a while while the on-device model loads.",
                color = colors.Text2, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun InputArea(
    input: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val colors = LocalNovaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 20.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.Surface)
                .border(1.dp, colors.Line, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)
        ) {
            TextField(
                value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f),
                enabled = !isGenerating,
                placeholder = { Text("Message Ira...", color = colors.Text2, fontSize = 14.5.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.Text0, unfocusedTextColor = colors.Text0
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), maxLines = 6
            )
            val canSend = input.isNotBlank() && !isGenerating

            val pulseTransition = rememberInfiniteTransition(label = "sendPulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "sendPulseScale",
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(if (isGenerating) pulseScale else 1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (canSend || isGenerating) Brush.linearGradient(listOf(colors.Blue500, colors.Cyan))
                        else Brush.linearGradient(listOf(colors.Line, colors.Line))
                    )
                    .clickable(enabled = canSend || isGenerating) { if (isGenerating) onStop() else onSend() },
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            "Irachat can make mistakes.", color = colors.Text2, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}
