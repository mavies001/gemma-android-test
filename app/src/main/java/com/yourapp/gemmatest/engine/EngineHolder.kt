package com.yourapp.gemmatest.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

const val MODEL_PATH = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm"

data class ChatTurn(val role: String, val text: String)

class EngineHolder {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val engineInitMutex = Mutex()
    private var engineInitDeferred: CompletableDeferred<Engine>? = null

    private suspend fun ensureEngine(): Engine {
        engine?.let { return it }

        engineInitMutex.withLock {
            engine?.let { return it }
            val existing = engineInitDeferred
            if (existing != null) return existing.await()

            val deferred = CompletableDeferred<Engine>()
            engineInitDeferred = deferred

            return withContext(Dispatchers.IO) {
                try {
                    val modelFile = File(MODEL_PATH)
                    if (!modelFile.exists()) {
                        throw IllegalStateException("Model file not found at $MODEL_PATH")
                    }
                    val config = EngineConfig(
                        modelPath = MODEL_PATH,
                        backend = Backend.CPU(),
                        maxNumTokens = 1024,
                    )
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    engine = newEngine
                    deferred.complete(newEngine)
                    newEngine
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                    engineInitDeferred = null
                    throw e
                }
            }
        }
    }

    suspend fun warmup() {
        ensureEngine()
    }

    suspend fun getConversation(history: List<ChatTurn> = emptyList()): Conversation {
        conversation?.let { return it }

        val readyEngine = ensureEngine()

        return withContext(Dispatchers.IO) {
            val initialMessages = history.map {
                if (it.role == "USER") Message.user(it.text) else Message.model(it.text)
            }
            val newConversation = readyEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                    initialMessages = initialMessages,
                )
            )
            conversation = newConversation
            newConversation
        }
    }

    // one-off, separate Conversation object — deliberately NOT stored in
    // `conversation`, so it never touches the main chat's context/KV cache.
    suspend fun generateSummary(userText: String, aiText: String): String {
        val readyEngine = ensureEngine()
        return withContext(Dispatchers.IO) {
            val summaryConversation = readyEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3),
                    initialMessages = emptyList(),
                )
            )
            val prompt = "In 4 to 6 words, write a short title summarizing this exchange. " +
                "Respond with only the title, no punctuation, no quotes.\n\n" +
                "User: $userText\nAssistant: $aiText"
            val response = summaryConversation.sendMessage(prompt)
            response.toString().trim().take(60)
        }
    }

    fun resetConversation() {
        conversation = null
    }

    fun close() {
        engine?.close()
        engine = null
        conversation = null
        engineInitDeferred = null
    }
}
