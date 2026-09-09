package com.yourapp.gemmatest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

object NovaColors {
    val Bg0 = Color(0xFF060A16)
    val Bg1 = Color(0xFF0B1226)
    val Surface = Color(0xFF111A33)
    val Surface2 = Color(0xFF152047)
    val Line = Color(0xFF22305C)
    val Blue500 = Color(0xFF3B6CF6)
    val Blue300 = Color(0xFF8FB3FF)
    val Cyan = Color(0xFF31D8E0)
    val Text0 = Color(0xFFEEF2FF)
    val Text1 = Color(0xFFAAB7E0)
    val Text2 = Color(0xFF6F7DB0)
}

data class Subject(val id: String, val label: String, val icon: String)

enum class Role { USER, AI }

data class ChatMsg(
    val role: Role,
    val text: String,
    val streaming: Boolean = false
)

val SUBJECTS = listOf(
    Subject("general", "General Chat", "\uD83D\uDCAC"),
    Subject("coding", "Coding Help", "\uD83D\uDCBB"),
    Subject("writing", "Writing & Editing", "\u270D\uFE0F"),
    Subject("research", "Research & Analysis", "\uD83D\uDD0E"),
)

const val MODEL_PATH = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = NovaColors.Bg0)) {
                NovaApp()
            }
        }
    }
}

class EngineHolder {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun getConversation(context: Context, onStatus: (String) -> Unit): Conversation {
        conversation?.let { return it }

        return withContext(Dispatchers.IO) {
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                throw IllegalStateException("Model file not found at $MODEL_PATH")
            }

            onStatus("Loading model...")
            val config = EngineConfig(
                modelPath = MODEL_PATH,
                backend = Backend.CPU(),
                maxNumTokens = 1024,
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine

            onStatus("Starting conversation...")
            val newConversation = newEngine.createConversation()
            conversation = newConversation
            newConversation
        }
    }

    fun close() {
        engine?.close()
        engine = null
        conversation = null
    }
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(modifier = modifier.fillMaxSize().background(NovaColors.Bg0)) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.75f)
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationX = 60f + drift * 120f
                    translationY = 40f + drift * 160f
                    alpha = 0.25f
                }
                .background(
                    Brush.radialGradient(listOf(NovaColors.Blue500, Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = -60f - drift * 100f
                    translationY = -40f - drift * 140f
                    alpha = 0.2f
                }
                .background(
                    Brush.radialGradient(listOf(NovaColors.Cyan, Color.Transparent)),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    Box(
        modifier = Modifier
            .size(11.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(NovaColors.Cyan, NovaColors.Blue500)))
    )
}

@Composable
fun SubjectDropdown(subject: Subject, onSubjectChange: (Subject) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NovaColors.Surface)
                .border(1.dp, NovaColors.Line, RoundedCornerShape(10.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(subject.icon, fontSize = 14.sp)
            Text(subject.label, color = NovaColors.Blue300, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = NovaColors.Text1,
                modifier = Modifier.size(16.dp)
            )
        }

        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, 130),
                onDismissRequest = { open = false }
            ) {
                Column(
                    modifier = Modifier
                        .width(230.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NovaColors.Surface)
                        .border(1.dp, NovaColors.Line, RoundedCornerShape(14.dp))
                        .padding(8.dp)
                ) {
                    SUBJECTS.forEach { s ->
                        val active = s.id == subject.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) NovaColors.Blue500.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable {
                                    onSubjectChange(s)
                                    open = false
                                }
                                .padding(horizontal = 11.dp, vertical = 10.dp)
                        ) {
                            Text(s.icon, fontSize = 14.sp)
                            Text(s.label, fontSize = 13.5.sp, color = if (active) NovaColors.Text0 else NovaColors.Text1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeader(subject: Subject, onSubjectChange: (Subject) -> Unit, statusText: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NovaColors.Bg1.copy(alpha = 0.9f))
                .border(width = 1.dp, color = NovaColors.Line)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulsingDot()
                Text("Nova", color = NovaColors.Text0, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            SubjectDropdown(subject, onSubjectChange)
        }
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                color = NovaColors.Text2,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovaColors.Bg1)
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ChatMessageRow(msg: ChatMsg) {
    val isUser = msg.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) Avatar(isUser = false)
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = if (isUser) 280.dp else 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) Brush.linearGradient(listOf(NovaColors.Blue500, Color(0xFF4F7DFA)))
                    else Brush.linearGradient(listOf(NovaColors.Surface, NovaColors.Surface))
                )
                .let { if (!isUser) it.border(1.dp, NovaColors.Line, RoundedCornerShape(16.dp)) else it }
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                msg.text + if (msg.streaming) " \u258C" else "",
                color = if (isUser) Color.White else NovaColors.Text0,
                fontSize = 14.5.sp,
                lineHeight = 22.sp
            )
        }
        if (isUser) Avatar(isUser = true)
    }
}

@Composable
fun Avatar(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (isUser) NovaColors.Surface2
                else Brush.linearGradient(listOf(NovaColors.Blue500, NovaColors.Cyan))
            )
            .let { if (isUser) it.border(1.dp, NovaColors.Line, RoundedCornerShape(9.dp)) else it },
        contentAlignment = Alignment.Center
    ) {
        Text(if (isUser) "\uD83D\uDE42" else "\u2726", fontSize = 14.sp)
    }
}

@Composable
fun TypingIndicator(statusText: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(isUser = false)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NovaColors.Surface)
                .border(1.dp, NovaColors.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                val transition = rememberInfiniteTransition(label = "dot$i")
                val bounce by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, delayMillis = i * 150, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bounce$i"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { translationY = bounce }
                        .clip(CircleShape)
                        .background(NovaColors.Blue300)
                )
            }
            if (statusText.isNotEmpty()) {
                Text(statusText, color = NovaColors.Text2, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun InputArea(
    input: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 20.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NovaColors.Surface)
                .border(1.dp, NovaColors.Line, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, color = NovaColors.Text2, fontSize = 14.5.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = NovaColors.Text0,
                    unfocusedTextColor = NovaColors.Text0
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 6
            )
            val canSend = input.isNotBlank() && enabled
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (canSend) Brush.linearGradient(listOf(NovaColors.Blue500, NovaColors.Cyan))
                        else Brush.linearGradient(listOf(NovaColors.Line, NovaColors.Line))
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            "Runs fully on-device. Nova can make mistakes.",
            color = NovaColors.Text2,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun NovaApp() {
    val context = LocalContext.current
    var subject by remember { mutableStateOf(SUBJECTS[0]) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val engineHolder = remember { EngineHolder() }

    val isStreaming = messages.isNotEmpty() && messages.last().streaming

    fun revealReply(fullText: String) {
        messages.add(ChatMsg(Role.AI, "", streaming = true))
        val words = fullText.split(" ")
        scope.launch {
            var i = 0
            var acc = ""
            while (i < words.size) {
                val chunkSize = 2 + Random.nextInt(3)
                val next = words.subList(i, minOf(i + chunkSize, words.size)).joinToString(" ")
                acc = if (acc.isEmpty()) next else "$acc $next"
                i += chunkSize
                val last = messages.removeAt(messages.size - 1)
                messages.add(last.copy(text = acc, streaming = i < words.size))
                delay(25 + Random.nextLong(30))
            }
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || thinking || isStreaming) return
        messages.add(ChatMsg(Role.USER, text))
        input = ""
        thinking = true
        statusText = ""

        scope.launch {
            try {
                val conversation = engineHolder.getConversation(context) { status ->
                    statusText = status
                }
                statusText = "Generating..."
                val response = withContext(Dispatchers.IO) {
                    conversation.sendMessage(text)
                }
                thinking = false
                statusText = ""
                revealReply(response.toString())
            } catch (e: Exception) {
                thinking = false
                statusText = ""
                messages.add(ChatMsg(Role.AI, "Error: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DisposableEffect(Unit) {
        onDispose { engineHolder.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(subject = subject, onSubjectChange = { subject = it }, statusText = statusText)

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(NovaColors.Blue500, NovaColors.Cyan))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2726", fontSize = 26.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Ask Nova anything", color = NovaColors.Text0, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Running Gemma3-1B fully on-device. First response may take a few seconds while the model loads.",
                                color = NovaColors.Text2,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(18.dp, 26.dp, 18.dp, 10.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(messages) { _, m -> ChatMessageRow(m) }
                            if (thinking) item { TypingIndicator(statusText) }
                        }
                    }
                }

                InputArea(
                    input = input,
                    onInputChange = { input = it },
                    enabled = !thinking && !isStreaming,
                    placeholder = "Message Nova...",
                    onSend = { sendMessage() }
                )
            }
        }
    }
}
