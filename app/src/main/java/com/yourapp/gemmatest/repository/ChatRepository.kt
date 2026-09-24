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

    private fun squashConsecutiveRoles(turns: List<ChatTurn>): List<ChatTurn> {
        val result = mutableListOf<ChatTurn>()
        for (turn in turns) {
            val last = result.lastOrNull()
            if (last != null && last.role == turn.role) {
                result[result.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
            } else {
                result.add(turn)
            }
        }
        return result
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

        val priorTurns = squashConsecutiveRoles(
            db.messageDao().getMessagesForConversationOnce(convId)
                .dropLast(1)
                .map { ChatTurn(role = it.role, text = it.text) }
        )

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
                flow {
                    onlineChatClient.registerDeviceIfNeeded()
                    onlineChatClient.sendMessage(history = priorTurns, userText = text).collect { emit(it) }
                }
            } else {
                val conversation = engineHolder.getConversation(history = priorTurns, subject = subject)
                flow {
                    conversation.sendMessageAsync(text).collect { chunk -> emit(chunk.toString()) }
                }
            }

            withContext(Dispatchers.IO) {
                var chunkCount = 0
                deltaFlow
                    .catch { e ->
                        // a user-initiated stop/switch cancels this coroutine —
                        // that must NOT be treated as a generation error (which
                        // would trigger the destructive rollback below)
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
            // summaries always use the local model — free, already warm,
            // doesn't spend GLM tokens even for online conversations
            val summaryText = try {
                engineHolder.generateSummary(text)
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
