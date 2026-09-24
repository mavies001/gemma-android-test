package com.yourapp.gemmatest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yourapp.gemmatest.NovaApplication
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.model.ChatMsg
import com.yourapp.gemmatest.model.Role
import com.yourapp.gemmatest.repository.ChatRepository
import com.yourapp.gemmatest.theme.LocalNovaColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val SUBJECTS = listOf("General", "Physical Science", "Biological Science", "Medical Field", "Arts and Humanities")

@Composable
fun NovaApp(isDark: Boolean, onToggleTheme: () -> Unit) {
    val colors = LocalNovaColors.current
    val context = LocalContext.current
    val application = context.applicationContext as NovaApplication
    val repository = remember { ChatRepository(context, application.engineHolder, application.onlineChatClient) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by rememberSaveable { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var waitingForFirstToken by remember { mutableStateOf(false) }
    var showColdStartNotice by remember { mutableStateOf(false) }
    var activeConversationId by remember { mutableStateOf<Long?>(null) }
    var currentSubject by rememberSaveable { mutableStateOf(SUBJECTS.first()) }
    var isOnlineMode by rememberSaveable { mutableStateOf(false) }
    var pendingFallbackText by remember { mutableStateOf<String?>(null) }
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

    fun loadConversation(id: Long) {
        if (isGenerating) stopGeneration()
        scope.launch {
            activeConversationId = id
            repository.resetActiveConversation()
            messages.clear()
            messages.addAll(repository.loadMessages(id))
            drawerState.close()
        }
    }

    fun newChat() {
        if (isGenerating) stopGeneration()
        activeConversationId = null
        currentSubject = SUBJECTS.first()
        repository.resetActiveConversation()
        messages.clear()
        scope.launch { drawerState.close() }
    }

    fun enterSubject(subject: String) {
        if (isGenerating) stopGeneration()
        activeConversationId = null
        currentSubject = subject
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

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || isGenerating) return
        val engineWasReady = application.engineHolder.isReady
        val sendingOnline = isOnlineMode
        messages.add(ChatMsg(Role.USER, text))
        input = ""
        isGenerating = true
        waitingForFirstToken = true
        showColdStartNotice = !sendingOnline && !engineWasReady
        scope.launch { listState.scrollToItem(0) }

        var targetConversationId = activeConversationId

        generationJob = scope.launch {
            repository.sendMessage(
                text = text,
                subject = currentSubject,
                isOnline = sendingOnline,
                activeConversationId = activeConversationId,
                onConversationCreated = { id ->
                    activeConversationId = id
                    targetConversationId = id
                },
                onToken = onToken@{ cumulative ->
                    if (activeConversationId != targetConversationId) return@onToken
                    if (waitingForFirstToken) {
                        messages.add(ChatMsg(Role.AI, cumulative, streaming = true))
                        waitingForFirstToken = false
                    } else {
                        val last = messages.removeAt(messages.size - 1)
                        messages.add(last.copy(text = cumulative, streaming = true))
                    }
                },
                onError = onError@{ originalText, conversationWasDeleted, wasOnline ->
                    if (activeConversationId != targetConversationId) return@onError
                    if (messages.isNotEmpty() && messages.last().streaming) {
                        messages.removeAt(messages.size - 1)
                    }
                    if (messages.isNotEmpty() && messages.last().role == Role.USER && messages.last().text == originalText) {
                        messages.removeAt(messages.size - 1)
                    }
                    input = originalText
                    if (conversationWasDeleted) {
                        activeConversationId = null
                    }
                    if (wasOnline) {
                        pendingFallbackText = originalText
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

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, waitingForFirstToken) {
        val isAtBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
        if ((messages.isNotEmpty() || waitingForFirstToken) && isAtBottom) {
            listState.scrollToItem(0)
        }
    }

    DisposableEffect(Unit) {
        onDispose { repository.close() }
    }

    // shown after an online-mode failure — user chooses to retry online or
    // fall back to the local model for this message
    pendingFallbackText?.let { failedText ->
        AlertDialog(
            onDismissRequest = { pendingFallbackText = null },
            title = { Text("Couldn't reach Irachat online") },
            text = { Text("Your message is back in the input box. Would you like to try again online, or switch to the local model?") },
            confirmButton = {
                TextButton(onClick = {
                    isOnlineMode = false
                    pendingFallbackText = null
                    input = failedText
                    sendMessage()
                }) { Text("Use local model") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingFallbackText = null
                    input = failedText
                }) { Text("Not now") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
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
                    isOnline = isOnlineMode,
                    onToggleOnline = { if (!isGenerating) isOnlineMode = !isOnlineMode },
                )

                SubjectDropdown(subjects = SUBJECTS, selected = currentSubject, onSelect = { enterSubject(it) })

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
                            Text("Ask Irachat anything", color = colors.Text0, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Pick a subject above, or just start typing.",
                                color = colors.Text2, fontSize = 13.5.sp, lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState, modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            contentPadding = PaddingValues(18.dp, 26.dp, 18.dp, 10.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            if (waitingForFirstToken) item { TypingIndicator(showColdStartHint = showColdStartNotice) }
                            itemsIndexed(messages.asReversed()) { _, m -> ChatMessageRow(m) }
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
