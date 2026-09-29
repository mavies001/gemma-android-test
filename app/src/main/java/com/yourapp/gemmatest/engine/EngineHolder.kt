package com.yourapp.gemmatest.engine

import android.content.Context
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

const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"
const val EXPECTED_MODEL_SIZE = 584_417_280L

// app-private, app-specific external storage — no permission needed on
// modern Android, cleaned up automatically on uninstall
fun modelFile(context: Context): File = File(context.getExternalFilesDir(null), MODEL_FILENAME)

data class ChatTurn(val role: String, val text: String)

private const val LENGTH_RULE =
    " Respond in 1 to 3 short sentences by default (roughly 20-60 words). Only go longer " +
    "than that if the user explicitly asks for more detail, a full explanation, a list of " +
    "steps, or code."

private val SUBJECT_INSTRUCTIONS = mapOf(
    "General" to "You are Irachat, an on-device AI assistant developed by IRA Inc, a Nigerian " +
        "technology company. Help with everyday questions on any topic." + LENGTH_RULE,
    "Physical Science" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "physics, chemistry, mathematics, and engineering. Explain concepts clearly and show " +
        "step-by-step reasoning for calculations." + LENGTH_RULE,
    "Biological Science" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "biology, ecology, genetics, and life sciences. Use accurate terminology." + LENGTH_RULE,
    "Medical Field" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting with " +
        "medical and health-related topics. Explain clearly and accurately. You are not a " +
        "substitute for a doctor — for diagnosis, treatment, or anything urgent, tell the user " +
        "to consult a healthcare professional." + LENGTH_RULE,
    "Arts and Humanities" to "You are Irachat, developed by IRA Inc (Nigeria). You're assisting " +
        "with literature, history, philosophy, and culture. Engage thoughtfully." + LENGTH_RULE,
)
private val FALLBACK_INSTRUCTION =
    "You are Irachat, an on-device AI assistant developed by IRA Inc, a Nigerian technology company." + LENGTH_RULE

class EngineHolder(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val engineInitMutex = Mutex()
    private var engineInitDeferred: CompletableDeferred<Engine>? = null

    val isReady: Boolean
        get() = engine != null

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
                    val file = modelFile(context)
                    if (!file.exists()) {
                        throw IllegalStateException("Model file not found at ${file.absolutePath}")
                    }
                    val config = EngineConfig(
                        modelPath = file.absolutePath,
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
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.8),
                    initialMessages = initialMessages,
                )
            )
            conversation = newConversation
            newConversation
        }
    }

    suspend fun generateSummary(userText: String): String {
        val readyEngine = ensureEngine()
        return withContext(Dispatchers.IO) {
            val summaryConversation = readyEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3),
                    initialMessages = emptyList(),
                )
            )
            val prompt = "Write a short 4 to 6 word title describing what this message is " +
                "about. Respond with only the title, no punctuation, no quotes.\n\n" +
                "Message: $userText"
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
