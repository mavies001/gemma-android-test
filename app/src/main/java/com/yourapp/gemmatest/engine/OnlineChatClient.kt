package com.yourapp.gemmatest.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private const val GATEWAY_REGISTER_URL = "https://ayokemi--irachat-gateway-register.modal.run"
private const val GATEWAY_CHAT_URL = "https://ayokemi--irachat-gateway-chat.modal.run"

// Online mode is global (not subject-specific), per product decision —
// one comprehensive system prompt regardless of which subject was last
// selected. Deliberately NOT reusing EngineHolder's LENGTH_RULE — online
// answers should be thorough, not constrained to 1-3 sentences.
private const val ONLINE_SYSTEM_INSTRUCTION =
    "You are Irachat, an AI assistant developed by IRA Inc, a Nigerian technology " +
    "company, currently answering using an online connection. Give thorough, " +
    "well-organized, accurate answers. Do not artificially limit response length " +
    "— explain fully, use step-by-step reasoning where it helps, and use lists or " +
    "headers where they make an answer clearer. Never mention that you are 'GLM', " +
    "any underlying model name, or any technical implementation detail — you are " +
    "simply Irachat."

class OnlineChatClient(private val deviceAuth: DeviceAuth) {

    class OnlineChatException(message: String, val httpCode: Int? = null) : Exception(message)

    suspend fun registerDeviceIfNeeded() {
        if (deviceAuth.isRegistered) return
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("device_id", deviceAuth.deviceId)
                put("public_key", deviceAuth.publicKeyBase64())
            }
            val conn = openConnection(GATEWAY_REGISTER_URL)
            writeBody(conn, body.toString())
            val code = conn.responseCode
            conn.disconnect()
            // 200 = newly registered, 409 = already registered — both fine
            if (code != 200 && code != 409) {
                throw OnlineChatException("Device registration failed", code)
            }
            deviceAuth.markRegistered()
        }
    }

    fun sendMessage(history: List<ChatTurn>, userText: String): Flow<String> = flow {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", ONLINE_SYSTEM_INSTRUCTION)
            })
            for (turn in history) {
                put(JSONObject().apply {
                    put("role", if (turn.role == "USER") "user" else "assistant")
                    put("content", turn.text)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            })
        }

        val innerRequest = JSONObject().apply {
            put("model", "zai-org/GLM-5.3")
            put("messages", messages)
            put("temperature", 0.6)
            put("max_tokens", 2048)
            put("top_p", 0.9)
            put("stream", true)
            put("reasoning_effort", "low")
        }
        // this exact string is what gets signed AND what the gateway
        // verifies against — no re-serialization on the server side, so
        // there's no risk of a cross-language JSON-formatting mismatch
        val requestJsonString = innerRequest.toString()

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signedMessage = "$timestamp.$requestJsonString".toByteArray(StandardCharsets.UTF_8)
        val signature = deviceAuth.sign(signedMessage)

        val payload = JSONObject().apply {
            put("device_id", deviceAuth.deviceId)
            put("timestamp", timestamp)
            put("signature", signature)
            put("request_json", requestJsonString)
        }

        val conn = openConnection(GATEWAY_CHAT_URL)
        writeBody(conn, payload.toString())

        val code = conn.responseCode
        if (code != 200) {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw OnlineChatException("Online request failed ($code): $errorText", code)
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
        reader.useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val obj = JSONObject(data)
                    if (obj.has("error")) {
                        throw OnlineChatException(obj.getString("error"))
                    }
                    val delta = obj.optString("delta", "")
                    if (delta.isNotEmpty()) emit(delta)
                } catch (e: org.json.JSONException) {
                    // malformed SSE chunk — skip it rather than kill the whole stream
                }
            }
        }
        conn.disconnect()
    }.flowOn(Dispatchers.IO)

    private fun openConnection(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.connectTimeout = 15_000
        conn.readTimeout = 90_000
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        conn.setFixedLengthStreamingMode(bytes.size)
        val os: OutputStream = conn.outputStream
        os.write(bytes)
        os.flush()
        os.close()
    }
}
