package com.yourapp.gemmatest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.yourapp.gemmatest.data.AppDatabase
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.data.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class NovaColorScheme(
    val Bg0: Color,
    val Bg1: Color,
    val Surface: Color,
    val Surface2: Color,
    val Line: Color,
    val Blue500: Color,
    val Blue300: Color,
    val Cyan: Color,
    val Text0: Color,
    val Text1: Color,
    val Text2: Color,
    val CodeBg: Color,
)

val DarkNova = NovaColorScheme(
    Bg0 = Color(0xFF060A16),
    Bg1 = Color(0xFF0B1226),
    Surface = Color(0xFF111A33),
    Surface2 = Color(0xFF152047),
    Line = Color(0xFF22305C),
    Blue500 = Color(0xFF3B6CF6),
    Blue300 = Color(0xFF8FB3FF),
    Cyan = Color(0xFF31D8E0),
    Text0 = Color(0xFFEEF2FF),
    Text1 = Color(0xFFAAB7E0),
    Text2 = Color(0xFF6F7DB0),
    CodeBg = Color(0xFF0B1226),
)

val LightNova = NovaColorScheme(
    Bg0 = Color(0xFFF6F7FB),
    Bg1 = Color(0xFFFFFFFF),
    Surface = Color(0xFFEFF1F8),
    Surface2 = Color(0xFFE3E7F5),
    Line = Color(0xFFD9DEEE),
    Blue500 = Color(0xFF3B6CF6),
    Blue300 = Color(0xFF3B6CF6),
    Cyan = Color(0xFF1AAEB8),
    Text0 = Color(0xFF15192B),
    Text1 = Color(0xFF454E6E),
    Text2 = Color(0xFF7A82A0),
    CodeBg = Color(0xFFEAECF6),
)

val LocalNovaColors = staticCompositionLocalOf { DarkNova }

enum class Role { USER, AI }

data class ChatMsg(
    val role: Role,
    val text: String,
    val streaming: Boolean = false
)

const val MODEL_PATH = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm"
val SUBJECTS = listOf("General", "Code", "Writing", "Study")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            var useSystemTheme by rememberSaveable { mutableStateOf(true) }
            var manualDark by rememberSaveable { mutableStateOf(true) }
            val systemDark = isSystemInDarkTheme()
            val isDark = if (useSystemTheme) systemDark else manualDark
            val colors = if (isDark) DarkNova else LightNova

            val materialScheme = if (isDark) {
                darkColorScheme(background = colors.Bg0, surface = colors.Surface)
            } else {
                lightColorScheme(background = colors.Bg0, surface = colors.Surface)
            }

            MaterialTheme(colorScheme = materialScheme) {
                CompositionLocalProvider(LocalNovaColors provides colors) {
                    NovaApp(
                        isDark = isDark,
                        onToggleTheme = {
                            useSystemTheme = false
                            manualDark = !isDark
                        }
                    )
                }
            }
        }
    }
}

class EngineHolder {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun getConversation(onStatus: (String) -> Unit): Conversation {
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
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0)
                )
            )
            conversation = newConversation
            newConversation
        }
    }

    fun resetConversation() {
        conversation = null
    }

    fun close() {
        engine?.close()
        engine = null
        conversation = null
    }
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val colors = LocalNovaColors.current
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
fun AppHeader(
    onToggleSidebar: () -> Unit,
    onNewChat: () -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean,
    statusText: String
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
        if (statusText.isNotEmpty()) {
            Text(
                statusText, color = colors.Text2, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().background(colors.Bg1).padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
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
fun SubjectRail(subjects: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalNovaColors.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subjects) { s ->
            val active = s == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) colors.Blue500 else colors.Surface)
                    .border(1.dp, if (active) colors.Blue500 else colors.Line, RoundedCornerShape(20.dp))
                    .clickable { onSelect(s) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(s, color = if (active) Color.White else colors.Text1, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Sidebar(
    conversations: List<ConversationEntity>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = LocalNovaColors.current
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(colors.Bg1)
    ) {
        Text(
            "HISTORY", color = colors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(18.dp, 18.dp, 18.dp, 10.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            itemsIndexed(conversations) { _, c ->
                val active = activeId == c.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.Surface2 else Color.Transparent)
                        .clickable { onSelect(c.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.title, color = colors.Text0, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.summary.isNotEmpty()) {
                            Text(c.summary, color = colors.Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Delete",
                        tint = colors.Text2, modifier = Modifier.size(16.dp).clickable { onDelete(c.id) }
                    )
                }
            }
        }
    }
}

sealed class MdBlock {
    data class Code(val text: String) : MdBlock()
    data class Header(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                val buffer = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    buffer.appendLine(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(buffer.toString().trimEnd()))
                if (i < lines.size) i++
            }
            trimmed.startsWith("### ") -> { blocks.add(MdBlock.Header(3, trimmed.removePrefix("### "))); i++ }
            trimmed.startsWith("## ") -> { blocks.add(MdBlock.Header(2, trimmed.removePrefix("## "))); i++ }
            trimmed.startsWith("# ") -> { blocks.add(MdBlock.Header(1, trimmed.removePrefix("# "))); i++ }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(trimmed.removePrefix("- ").removePrefix("* "))); i++
            }
            trimmed.isEmpty() -> { i++ }
            else -> { blocks.add(MdBlock.Paragraph(line)); i++ }
        }
    }
    return blocks
}

fun parseInlineMarkdown(text: String) = buildAnnotatedString {
    val regex = Regex("(\\*\\*.+?\\*\\*)|(`[^`]+`)|(\\*[^*\\n]+?\\*)")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastIndex) append(text.substring(lastIndex, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080))
            ) { append(" " + token.removeSurrounding("`") + " ") }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removeSurrounding("*"))
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) append(text.substring(lastIndex))
}

@Composable
fun MarkdownText(raw: String, textColor: Color, streaming: Boolean = false) {
    val colors = LocalNovaColors.current
    val blocks = remember(raw) { parseMarkdownBlocks(raw) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            when (block) {
                is MdBlock.Code -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.CodeBg)
                            .border(1.dp, colors.Line, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            block.text + if (isLast && streaming) " \u258C" else "",
                            color = textColor, fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp, lineHeight = 18.sp
                        )
                    }
                }
                is MdBlock.Header -> {
                    Text(
                        parseInlineMarkdown(block.text), color = textColor,
                        fontSize = when (block.level) { 1 -> 19.sp; 2 -> 17.sp; else -> 15.5.sp },
                        fontWeight = FontWeight.Bold
                    )
                }
                is MdBlock.Bullet -> {
                    Row {
                        Text("\u2022  ", color = textColor, fontSize = 14.5.sp)
                        Text(
                            parseInlineMarkdown(block.text + (if (isLast && streaming) " \u258C" else "")),
                            color = textColor, fontSize = 14.5.sp, lineHeight = 21.sp
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        parseInlineMarkdown(block.text + (if (isLast && streaming) " \u258C" else "")),
                        color = textColor, fontSize = 14.5.sp, lineHeight = 22.sp
                    )
                }
            }
        }
        if (blocks.isEmpty() && streaming) {
            Text("\u258C", color = textColor, fontSize = 14.5.sp)
        }
    }
}

@Composable
fun ChatMessageRow(msg: ChatMsg) {
    val colors = LocalNovaColors.current
    val isUser = msg.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            Avatar(isUser = false)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f, fill = false).widthIn(max = 620.dp)) {
                MarkdownText(raw = msg.text, textColor = colors.Text0, streaming = msg.streaming)
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(colors.Blue500, Color(0xFF4F7DFA))))
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Text(msg.text, color = Color.White, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Avatar(isUser = true)
        }
    }
}

@Composable
fun Avatar(isUser: Boolean) {
    val colors = LocalNovaColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .let {
                if (isUser) it.background(colors.Surface2)
                else it.background(Brush.linearGradient(listOf(colors.Blue500, colors.Cyan)))
            }
            .let { if (isUser) it.border(1.dp, colors.Line, RoundedCornerShape(9.dp)) else it },
        contentAlignment = Alignment.Center
    ) {
        Text(if (isUser) "\uD83D\uDE42" else "\u2726", fontSize = 14.sp)
    }
}

@Composable
fun TypingIndicator(statusText: String) {
    val colors = LocalNovaColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(isUser = false)
        Row(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(colors.Surface)
                .border(1.dp, colors.Line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                val transition = rememberInfiniteTransition(label = "dot$i")
                val bounce by transition.animateFloat(
                    initialValue = 0f, targetValue = -6f,
                    animationSpec = infiniteRepeatable(tween(1200, delayMillis = i * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bounce$i"
                )
                Box(modifier = Modifier.size(7.dp).graphicsLayer { translationY = bounce }.clip(CircleShape).background(colors.Blue300))
            }
            if (statusText.isNotEmpty()) Text(statusText, color = colors.Text2, fontSize = 11.sp)
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
                placeholder = { Text("Message Nova...", color = colors.Text2, fontSize = 14.5.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.Text0, unfocusedTextColor = colors.Text0
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), maxLines = 6
            )

            val canSend = input.isNotBlank() && !isGenerating
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(
                        when {
                            isGenerating -> Brush.linearGradient(listOf(colors.Blue500, colors.Cyan))
                            canSend -> Brush.linearGradient(listOf(colors.Blue500, colors.Cyan))
                            else -> Brush.linearGradient(listOf(colors.Line, colors.Line))
                        }
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
            "Runs fully on-device. Nova can make mistakes.", color = colors.Text2, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NovaApp(isDark: Boolean, onToggleTheme: () -> Unit) {
    val colors = LocalNovaColors.current
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var activeConversationId by remember { mutableStateOf<Long?>(null) }
    var currentSubject by rememberSaveable { mutableStateOf(SUBJECTS.first()) }
    val conversations = remember { mutableStateListOf<ConversationEntity>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val engineHolder = remember { EngineHolder() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var generationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        db.conversationDao().getAllConversations().collect { list ->
            conversations.clear()
            conversations.addAll(list)
        }
    }

    val isStreaming = messages.isNotEmpty() && messages.last().streaming
    val isGenerating = thinking || isStreaming

    suspend fun ensureConversationRow(firstUserText: String): Long {
        activeConversationId?.let { return it }
        val now = System.currentTimeMillis()
        val title = firstUserText.take(40)
        val id = db.conversationDao().insertConversation(
            ConversationEntity(title = title, summary = "", createdAt = now, updatedAt = now)
        )
        activeConversationId = id
        return id
    }

    fun loadConversation(id: Long) {
        scope.launch {
            activeConversationId = id
            engineHolder.resetConversation()
            messages.clear()
            val saved = db.messageDao().getMessagesForConversationOnce(id)
            saved.forEach { m ->
                messages.add(ChatMsg(role = if (m.role == "USER") Role.USER else Role.AI, text = m.text))
            }
            drawerState.close()
        }
    }

    fun newChat() {
        activeConversationId = null
        engineHolder.resetConversation()
        messages.clear()
        scope.launch { drawerState.close() }
    }

    fun deleteConversation(id: Long) {
        scope.launch {
            db.conversationDao().deleteConversation(id)
            if (activeConversationId == id) newChat()
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        thinking = false
        statusText = ""
        if (messages.isNotEmpty() && messages.last().streaming) {
            val last = messages.removeAt(messages.size - 1)
            messages.add(last.copy(streaming = false))
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || isGenerating) return
        messages.add(ChatMsg(Role.USER, text))
        input = ""
        thinking = true
        statusText = ""

        generationJob = scope.launch {
            try {
                val convId = ensureConversationRow(text)
                db.messageDao().insertMessage(
                    MessageEntity(conversationId = convId, role = "USER", text = text, createdAt = System.currentTimeMillis())
                )

                val conversation = engineHolder.getConversation { status -> statusText = status }
                statusText = "Generating..."

                messages.add(ChatMsg(Role.AI, "", streaming = true))
                thinking = false

                var fullResponse = ""
                withContext(Dispatchers.IO) {
                    conversation.sendMessageAsync(text)
                        .catch { e ->
                            withContext(Dispatchers.Main) {
                                val last = messages.removeAt(messages.size - 1)
                                messages.add(last.copy(text = "Error: ${e.message}", streaming = false))
                            }
                        }
                        .collect { chunk ->
                            fullResponse += chunk.toString()
                            withContext(Dispatchers.Main) {
                                val last = messages.removeAt(messages.size - 1)
                                messages.add(last.copy(text = fullResponse, streaming = true))
                            }
                        }
                }

                if (messages.isNotEmpty() && messages.last().streaming) {
                    val last = messages.removeAt(messages.size - 1)
                    messages.add(last.copy(streaming = false))
                }
                statusText = ""

                db.messageDao().insertMessage(
                    MessageEntity(conversationId = convId, role = "AI", text = fullResponse, createdAt = System.currentTimeMillis())
                )
                db.conversationDao().updateConversation(
                    db.conversationDao().getConversation(convId)!!.copy(
                        summary = fullResponse.take(80),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) {
                // user tapped stop
            } catch (e: Exception) {
                thinking = false
                statusText = ""
                messages.add(ChatMsg(Role.AI, "Error: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                generationJob = null
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, thinking) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    DisposableEffect(Unit) {
        onDispose { engineHolder.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = colors.Bg1) {
                    Sidebar(
                        conversations = conversations,
                        activeId = activeConversationId,
                        onSelect = { loadConversation(it) },
                        onDelete = { deleteConversation(it) },
                    )
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    onToggleSidebar = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } },
                    onNewChat = { newChat() },
                    onToggleTheme = onToggleTheme,
                    isDark = isDark,
                    statusText = statusText
                )

                SubjectRail(
                    subjects = SUBJECTS,
                    selected = currentSubject,
                    onSelect = { currentSubject = it }
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(colors.Blue500, colors.Cyan))),
                                contentAlignment = Alignment.Center
                            ) { Text("\u2726", fontSize = 26.sp) }
                            Spacer(Modifier.height(16.dp))
                            Text("Ask Nova anything", color = colors.Text0, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Running Gemma3-1B fully on-device.",
                                color = colors.Text2, fontSize = 13.5.sp, lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState, modifier = Modifier.fillMaxSize(),
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
                    isGenerating = isGenerating,
                    onSend = { sendMessage() },
                    onStop = { stopGeneration() }
                )
            }
        }
    }
}
