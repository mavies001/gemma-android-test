package com.yourapp.gemmatest.repository

import android.content.Context
import android.os.PowerManager
import com.yourapp.gemmatest.data.AppDatabase
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.data.MessageEntity
import com.yourapp.gemmatest.engine.ChatTurn
import com.yourapp.gemmatest.engine.EngineHolder
import com.yourapp.gemmatest.engine.OnlineChatClient
import com.yourapp.gemmatest.model.ChatMsg
import com.yourapp.gemmatest.model.Role
import com.yourapp.gemmatest.service.GenerationForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ChatRepository(
    context: Context,
    private val engineHolder: EngineHolder,
    private val onlineChatClient: OnlineChatClient,
) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun conversationsFlow(): Flow<List<ConversationEntity>> = db.conversationDao().getAllConversations()

    suspend fun loadMessages(conversationId: Long): List<ChatMsg> {
        return db.messageDao().getMessagesForConversationOnce(conversationId).map {
            ChatMsg(role = if (it.role == "USER") Role.USER else Role.AI, text = it.text)
        }
    }

    fun resetActiveConversation() {
        engineHolder.resetConversation()
    }

    suspend fun deleteConversation(id: Long) {
        db.conversationDao().deleteConversation(id)
    }

    private suspend fun ensureConversationRow(
        activeId: Long?,
        firstUserText: String,
        subject: String,
        isOnline: Boolean,
    ): Long {
        if (activeId != null) return activeId
        val now = System.currentTimeMillis()
        return db.conversationDao().insertConversation(
            ConversationEntity(
                title = firstUserText.take(40),
                summary = "",
                subject = subject,
                isOnline = isOnline,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun sendMessage(
        text: String,
        subject: String,
        isOnline: Boolean,
        activeConversationId: Long?,
        onConversationCreated: (Long) -> Unit,
        onToken: (String) -> Unit,
        onError: (originalText: String, conversationWasDeleted: Boolean, wasOnline: Boolean) -> Unit,
    ) {
        val wasNewConversation = activeConversationId == null
        val convId = ensureConversationRow(activeConversationId, text, subject, isOnline)
        if (wasNewConversation) onConversationCreated(convId)

        val userMessageId = db.messageDao().insertMessage(
            MessageEntity(conversationId = convId, role = "USER", text = text, createdAt = System.currentTimeMillis())
        )

        // all messages before the one just inserted
        val priorMessages = db.messageDao().getMessagesForConversationOnce(convId).dropLast(1)
        // item 3: only the single most recent USER-authored message is
        // used as context for a new send, regardless of mode — no AI
        // reply, no earlier turns, applied uniformly (not just after an
        // online detour)
        val lastUserOnly = priorMessages.lastOrNull { it.role == "USER" }?.text

        val aiMessageId = db.messageDao().insertMessage(
            MessageEntity(conversationId = convId, role = "AI", text = "", createdAt = System.currentTimeMillis())
        )

        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GemmaTest::InferenceWakeLock")
        wakeLock.acquire(2 * 60 * 1000L)
        GenerationForegroundService.start(appContext)

        var fullResponse = ""
        var errored = false
        try {
            val deltaFlow: Flow<String> = if (isOnline) {
                onlineChatClient.registerDeviceIfNeeded()
                onlineChatClient.sendMessage(lastUserContext = lastUserOnly, userText = text)
            } else {
                // always reset: history is trimmed to one message anyway,
                // so there's no benefit to keeping a cached Conversation
                // around between sends, and this avoids the stale-cache
                // bug where a chat that went online and came back offline
                // would replay against out-of-date engine state
                engineHolder.resetConversation()
                val effectiveText = if (lastUserOnly != null) "$lastUserOnly\n\n$text" else text
                val conversation = engineHolder.getConversation(history = emptyList(), subject = subject)
                flow {
                    conversation.sendMessageAsync(effectiveText).collect { chunk -> emit(chunk.toString()) }
                }
            }

            withContext(Dispatchers.IO) {
                var chunkCount = 0
                deltaFlow
                    .catch { e ->
                        if (e is CancellationException) throw e
                        errored = true
                    }
                    .collect { chunk ->
                        fullResponse += chunk
                        onToken(fullResponse)
                        chunkCount++
                        if (chunkCount % 5 == 0) {
                            db.messageDao().updateMessageText(aiMessageId, fullResponse)
                        }
                    }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            GenerationForegroundService.stop(appContext)
        }

        if (errored) {
            db.messageDao().deleteMessage(aiMessageId)
            db.messageDao().deleteMessage(userMessageId)
            if (wasNewConversation) {
                db.conversationDao().deleteConversation(convId)
            }
            onError(text, wasNewConversation, isOnline)
            return
        }

        db.messageDao().updateMessageText(aiMessageId, fullResponse)

        if (wasNewConversation) {
            // item 4: summary generator matches how the conversation started
            val summaryText = try {
                if (isOnline) onlineChatClient.generateSummary(text) else engineHolder.generateSummary(text)
            } catch (e: Exception) {
                text.take(60)
            }
            db.conversationDao().getConversation(convId)?.let { conv ->
                db.conversationDao().updateConversation(
                    conv.copy(summary = summaryText, updatedAt = System.currentTimeMillis())
                )
            }
        } else {
            db.conversationDao().touchUpdatedAt(convId, System.currentTimeMillis())
        }
    }

    fun close() {
        engineHolder.close()
    }
}
