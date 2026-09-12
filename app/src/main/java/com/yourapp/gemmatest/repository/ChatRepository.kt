package com.yourapp.gemmatest.repository

import android.content.Context
import android.os.PowerManager
import com.yourapp.gemmatest.data.AppDatabase
import com.yourapp.gemmatest.data.ConversationEntity
import com.yourapp.gemmatest.data.MessageEntity
import com.yourapp.gemmatest.engine.ChatTurn
import com.yourapp.gemmatest.engine.EngineHolder
import com.yourapp.gemmatest.model.ChatMsg
import com.yourapp.gemmatest.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext

class ChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val engineHolder = EngineHolder()
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

    private suspend fun ensureConversationRow(activeId: Long?, firstUserText: String): Long {
        if (activeId != null) return activeId
        val now = System.currentTimeMillis()
        return db.conversationDao().insertConversation(
            ConversationEntity(title = firstUserText.take(40), summary = "", createdAt = now, updatedAt = now)
        )
    }

    suspend fun sendMessage(
        text: String,
        activeConversationId: Long?,
        onConversationCreated: (Long) -> Unit,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val convId = ensureConversationRow(activeConversationId, text)
        if (activeConversationId == null) onConversationCreated(convId)

        db.messageDao().insertMessage(
            MessageEntity(conversationId = convId, role = "USER", text = text, createdAt = System.currentTimeMillis())
        )

        val priorTurns = db.messageDao().getMessagesForConversationOnce(convId)
            .dropLast(1)
            .map { ChatTurn(role = it.role, text = it.text) }

        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GemmaTest::InferenceWakeLock")
        wakeLock.acquire(2 * 60 * 1000L)

        var fullResponse = ""
        var errored = false
        try {
            val conversation = engineHolder.getConversation(history = priorTurns)
            withContext(Dispatchers.IO) {
                conversation.sendMessageAsync(text)
                    .catch { e ->
                        errored = true
                        onError(e.message ?: "Unknown error")
                    }
                    .collect { chunk ->
                        fullResponse += chunk.toString()
                        onToken(fullResponse)
                    }
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }

        if (!errored) {
            db.messageDao().insertMessage(
                MessageEntity(conversationId = convId, role = "AI", text = fullResponse, createdAt = System.currentTimeMillis())
            )
            db.conversationDao().getConversation(convId)?.let { conv ->
                db.conversationDao().updateConversation(
                    conv.copy(summary = fullResponse.take(80), updatedAt = System.currentTimeMillis())
                )
            }
        }
    }

    fun close() {
        engineHolder.close()
    }
}
