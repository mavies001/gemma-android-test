package com.yourapp.gemmatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.model.ChatMsg
import com.yourapp.gemmatest.model.Role
import com.yourapp.gemmatest.repository.ChatRepository
import com.yourapp.gemmatest.theme.LocalNovaColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val SUBJECTS = listOf("General", "Code", "Writing", "Study")

@Composable
fun NovaApp(isDark: Boolean, onToggleTheme: () -> Unit) {
    val colors = LocalNovaColors.current
    val context = LocalContext.current
    val repository = remember { ChatRepository(context) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by rememberSaveable { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var waitingForFirstToken by remember { mutableStateOf(false) }
    var activeConversationId by remember { mutableStateOf<Long?>(null) }
    var currentSubject by rememberSaveable { mutableStateOf(SUBJECTS.first()) }
    val conversations = remember { mutableStateListOf<ConversationEntity>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var generationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        repository.conversationsFlow().collect { list ->
            conversations.clear()
            conversations.addAll(list)
        }
    }

    fun loadConversation(id: Long) {
        scope.launch {
            activeConversationId = id
            repository.resetActiveConversation()
            messages.clear()
            messages.addAll(repository.loadMessages(id))
            drawerState.close()
        }
    }

    fun newChat() {
        activeConversationId = null
        repository.resetActiveConversation()
        messages.clear()
        scope.launch { drawerState.close() }
    }

    fun deleteConversation(id: Long) {
        scope.launch {
            repository.deleteConversation(id)
            if (activeConversationId == id) newChat()
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        isGenerating = false
        waitingForFirstToken = false
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
        isGenerating = true
        waitingForFirstToken = true

        generationJob = scope.launch {
            repository.sendMessage(
                text = text,
                activeConversationId = activeConversationId,
                onConversationCreated = { id -> activeConversationId = id },
                onToken = { cumulative ->
                    if (waitingForFirstToken) {
                        messages.add(ChatMsg(Role.AI, cumulative, streaming = true))
                        waitingForFirstToken = false
                    } else {
                        val last = messages.removeAt(messages.size - 1)
                        messages.add(last.copy(text = cumulative, streaming = true))
                    }
                },
                onError = { err ->
                    if (waitingForFirstToken) {
                        messages.add(ChatMsg(Role.AI, "Error: $err", streaming = false))
                        waitingForFirstToken = false
                    } else {
                        val last = messages.removeAt(messages.size - 1)
                        messages.add(last.copy(text = "Error: $err", streaming = false))
                    }
                }
            )
            if (messages.isNotEmpty() && messages.last().streaming) {
                val last = messages.removeAt(messages.size - 1)
                messages.add(last.copy(streaming = false))
            }
            isGenerating = false
            waitingForFirstToken = false
            generationJob = null
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    DisposableEffect(Unit) {
        onDispose { repository.close() }
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
                    isGenerating = isGenerating,
                )

                SubjectRail(subjects = SUBJECTS, selected = currentSubject, onSelect = { currentSubject = it })

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
                            if (waitingForFirstToken) item { TypingIndicator() }
                        }
                    }
                }

                InputArea(
                    input = input, onInputChange = { input = it },
                    isGenerating = isGenerating,
                    onSend = { sendMessage() },
                    onStop = { stopGeneration() }
                )
            }
        }
    }
}
