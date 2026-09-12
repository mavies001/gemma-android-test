package com.yourapp.gemmatest.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val MODEL_PATH = "/storage/emulated/0/Download/gemma3-1b-it-int4.litertlm"

data class ChatTurn(val role: String, val text: String)

class EngineHolder {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun getConversation(history: List<ChatTurn> = emptyList()): Conversation {
        conversation?.let { return it }

        return withContext(Dispatchers.IO) {
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

            val initialMessages = history.map {
                if (it.role == "USER") Message.user(it.text) else Message.model(it.text)
            }

            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                    initialMessages = initialMessages,
                )
            )
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
