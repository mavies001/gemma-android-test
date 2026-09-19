package com.yourapp.gemmatest.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
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

private val SUBJECT_INSTRUCTIONS = mapOf(
    "General" to "You are Irachat, an on-device AI assistant developed by IRA Inc, a Nigerian " +
        "technology company. Help with everyday questions on any topic. Keep answers concise " +
        "and clear unless asked for more detail.",
    "Physical Science" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "physics, chemistry, mathematics, and engineering. Explain concepts clearly, use simple " +
        "analogies where helpful, and show step-by-step reasoning for calculations. Keep answers " +
        "concise unless asked for depth.",
    "Biological Science" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "biology, ecology, genetics, and life sciences. Use accurate terminology and explain " +
        "concepts clearly. Keep answers concise unless asked for depth.",
    "Medical Field" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "medical and health-related topics. Explain clearly and accurately. You are not a " +
        "substitute for a doctor — for diagnosis, treatment, or anything urgent, tell the user " +
        "to consult a healthcare professional.",
    "Arts and Humanities" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting " +
        "with literature, history, philosophy, and culture. Engage thoughtfully and encourage " +
        "critical thinking. Keep answers concise unless asked for depth.",
)
private const val FALLBACK_INSTRUCTION =
    "You are Irachat, an on-device AI assistant developed by IRA Inc, a Nigerian technology " +
    "company. Keep answers concise and clear unless asked for more detail."

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

    suspend fun getConversation(history: List<ChatTurn> = emptyList(), subject: String = "General"): Conversation {
        conversation?.let { return it }

        val readyEngine = ensureEngine()

        return withContext(Dispatchers.IO) {
            val initialMessages = history.map {
                if (it.role == "USER") Message.user(it.text) else Message.model(it.text)
            }
            val instructionText = SUBJECT_INSTRUCTIONS[subject] ?: FALLBACK_INSTRUCTION
            val newConversation = readyEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(instructionText),
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                    initialMessages = initialMessages,
                )
            )
            conversation = newConversation
            newConversation
        }
    }

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
