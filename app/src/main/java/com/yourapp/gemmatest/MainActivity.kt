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
import com.yourapp.gemmatest.data.AppDatabase
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

enum class Role { USER, AI }

data class ChatMsg(
    val role: Role,
    val text: String,
    val streaming: Boolean = false
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
            val newConversation = newEngine.createConversation()
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
                .background(Brush.radialGradient(listOf(NovaColors.Blue500, Color.Transparent)), shape = CircleShape)
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
                .background(Brush.radialGradient(listOf(NovaColors.Cyan, Color.Transparent)), shape = CircleShape)
        )
    }
}

@Composable
fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Box(
        modifier = Modifier.size(11.dp).scale(scale).clip(CircleShape)
            .background(Brush.radialGradient(listOf(NovaColors.Cyan, NovaColors.Blue500)))
    )
}

@Composable
fun AppHeader(sidebarOpen: Boolean, onToggleSidebar: () -> Unit, onNewChat: () -> Unit, statusText: String) {
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NovaColors.Surface)
                        .border(1.dp, NovaColors.Line, RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleSidebar),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "History", tint = NovaColors.Text1, modifier = Modifier.size(18.dp))
                }
                PulsingDot()
                Text("Nova", color = NovaColors.Text0, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NovaColors.Surface)
                    .border(1.dp, NovaColors.Line, RoundedCornerShape(10.dp))
                    .clickable(onClick = onNewChat),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New chat", tint = NovaColors.Blue300, modifier = Modifier.size(18.dp))
            }
        }
        if (statusText.isNotEmpty()) {
            Text(
                statusText, color = NovaColors.Text2, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().background(NovaColors.Bg1).padding(horizontal = 22.dp, vertical = 4.dp)
            )
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
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(NovaColors.Bg1.copy(alpha = 0.97f))
            .border(width = 1.dp, color = NovaColors.Line)
    ) {
        Text(
            "HISTORY", color = NovaColors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
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
                        .background(if (active) NovaColors.Surface2 else Color.Transparent)
                        .clickable { onSelect(c.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.title, color = NovaColors.Text0, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.summary.isNotEmpty()) {
                            Text(c.summary, color = NovaColors.Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Delete",
                        tint = NovaColors.Text2, modifier = Modifier.size(16.dp).clickable { onDelete(c.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageRow(msg: ChatMsg) {
    val isUser = msg.role == Role.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
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
                fontSize = 14.5.sp, lineHeight = 22.sp
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
            .let {
                if (isUser) it.background(NovaColors.Surface2)
                else it.background(Brush.linearGradient(listOf(NovaColors.Blue500, NovaColors.Cyan)))
            }
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
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(NovaColors.Surface)
                .border(1.dp, NovaColors.Line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                val transition = rememberInfiniteTransition(label = "dot$i")
                val bounce by transition.animateFloat(
                    initialValue = 0f, targetValue = -6f,
                    animationSpec = infiniteRepeatable(tween(1200, delayMillis = i * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bounce$i"
                )
                Box(modifier = Modifier.size(7.dp).graphicsLayer { translationY = bounce }.clip(CircleShape).background(NovaColors.Blue300))
            }
            if (statusText.isNotEmpty()) Text(statusText, color = NovaColors.Text2, fontSize = 11.sp)
        }
    }
}

@Composable
fun InputArea(input: String, onInputChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 20.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NovaColors.Surface)
                .border(1.dp, NovaColors.Line, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)
        ) {
            TextField(
                value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f),
                placeholder = { Text("Message Nova...", color = NovaColors.Text2, fontSize = 14.5.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = NovaColors.Text0, unfocusedTextColor = NovaColors.Text0
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), maxLines = 6
            )
            val canSend = input.isNotBlank() && enabled
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
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
            "Runs fully on-device. Nova can make mistakes.", color = NovaColors.Text2, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun NovaApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var sidebarOpen by remember { mutableStateOf(false) }
    var activeConversationId by remember { mutableStateOf<Long?>(null) }
    val conversations = remember { mutableStateListOf<ConversationEntity>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val engineHolder = remember { EngineHolder() }

    LaunchedEffect(Unit) {
        db.conversationDao().getAllConversations().collect { list ->
            conversations.clear()
            conversations.addAll(list)
        }
    }

    val isStreaming = messages.isNotEmpty() && messages.last().streaming

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
            sidebarOpen = false
        }
    }

    fun newChat() {
        activeConversationId = null
        engineHolder.resetConversation()
        messages.clear()
        sidebarOpen = false
    }

    fun deleteConversation(id: Long) {
        scope.launch {
            db.conversationDao().deleteConversation(id)
            if (activeConversationId == id) newChat()
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
            AppHeader(
                sidebarOpen = sidebarOpen,
                onToggleSidebar = { sidebarOpen = !sidebarOpen },
                onNewChat = { newChat() },
                statusText = statusText
            )

            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = sidebarOpen) {
                    Sidebar(
                        conversations = conversations,
                        activeId = activeConversationId,
                        onSelect = { loadConversation(it) },
                        onDelete = { deleteConversation(it) },
                    )
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (messages.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                        .background(Brush.linearGradient(listOf(NovaColors.Blue500, NovaColors.Cyan))),
                                    contentAlignment = Alignment.Center
                                ) { Text("\u2726", fontSize = 26.sp) }
                                Spacer(Modifier.height(16.dp))
                                Text("Ask Nova anything", color = NovaColors.Text0, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Running Gemma3-1B fully on-device.",
                                    color = NovaColors.Text2, fontSize = 13.5.sp, lineHeight = 20.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        input = input, onInputChange = { input = it },
                        enabled = !thinking && !isStreaming, onSend = { sendMessage() }
                    )
                }
            }
        }
    }
}
