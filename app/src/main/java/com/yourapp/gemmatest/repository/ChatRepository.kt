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
import com.yourapp.gemmatest.service.GenerationForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext

class ChatRepository(context: Context, private val engineHolder: EngineHolder) {
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

    private suspend fun ensureConversationRow(activeId: Long?, firstUserText: String, subject: String): Long {
        if (activeId != null) return activeId
        val now = System.currentTimeMillis()
        return db.conversationDao().insertConversation(
            ConversationEntity(title = firstUserText.take(40), summary = "", subject = subject, createdAt = now, updatedAt = now)
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
        activeConversationId: Long?,
        onConversationCreated: (Long) -> Unit,
        onToken: (String) -> Unit,
        onError: (originalText: String, conversationWasDeleted: Boolean) -> Unit,
    ) {
        val wasNewConversation = activeConversationId == null
        val convId = ensureConversationRow(activeConversationId, text, subject)
        if (wasNewConversation) onConversationCreated(convId)

        val userMessageId = db.messageDao().insertMessage(
            MessageEntity(conversationId = convId, role = "USER", text = text, createdAt = System.currentTimeMillis())
        )

        // build history BEFORE inserting the AI placeholder row below, so
        // dropLast(1) correctly excludes only the just-inserted user message
        val priorTurns = squashConsecutiveRoles(
            db.messageDao().getMessagesForConversationOnce(convId)
                .dropLast(1)
                .map { ChatTurn(role = it.role, text = it.text) }
        )

        // placeholder AI row, inserted immediately — from this point on, the
        // user message always has a pair, even if the process is killed
        // outright (OOM kill, OS background killer) before generation ends
        val aiMessageId = db.messageDao().insertMessage(
            MessageEntity(conversationId = convId, role = "AI", text = "", createdAt = System.currentTimeMillis())
        )

        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GemmaTest::InferenceWakeLock")
        wakeLock.acquire(2 * 60 * 1000L)
        GenerationForegroundService.start(appContext)

        var fullResponse = ""
        var errored = false
        try {
            val conversation = engineHolder.getConversation(history = priorTurns, subject = subject)
            withContext(Dispatchers.IO) {
                var chunkCount = 0
                conversation.sendMessageAsync(text)
                    .catch { errored = true }
                    .collect { chunk ->
                        fullResponse += chunk.toString()
                        onToken(fullResponse)
                        chunkCount++
                        // periodic flush to DB — doesn't need to happen on every
                        // single token, just often enough that a kill mid-stream
                        // only loses a small trailing amount, not everything
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
            // a genuinely CAUGHT error still gets a full clean rollback —
            // this is different from a hard process kill, which the code
            // above already guards against via the incremental writes
            db.messageDao().deleteMessage(aiMessageId)
            db.messageDao().deleteMessage(userMessageId)
            if (wasNewConversation) {
                db.conversationDao().deleteConversation(convId)
            }
            onError(text, wasNewConversation)
            return
        }

        // guaranteed final flush — covers any tokens after the last
        // throttle checkpoint that the periodic write above might have missed
        db.messageDao().updateMessageText(aiMessageId, fullResponse)

        val summaryText = try {
            engineHolder.generateSummary(text, fullResponse)
        } catch (e: Exception) {
            fullResponse.take(60)
        }
        db.conversationDao().getConversation(convId)?.let { conv ->
            db.conversationDao().updateConversation(
                conv.copy(summary = summaryText, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun close() {
        engineHolder.close()
    }
}
