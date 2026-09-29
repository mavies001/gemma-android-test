package com.yourapp.gemmatest.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val DOWNLOAD_URL = "https://ayokemi--irachat-model-server-download.modal.run"
private const val CHECKSUM_URL = "https://ayokemi--irachat-model-server-checksum.modal.run"

class ModelDownloader(private val context: Context) {

    fun isModelPresent(): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() == EXPECTED_MODEL_SIZE
    }

    fun freeSpaceBytes(): Long {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return dir.usableSpace
    }

    // resumable: if a partial file already exists, requests only the
    // remaining bytes via a Range header, matching model_server.py's
    // Range-request support
    suspend fun download(onProgress: (downloaded: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        val existingBytes = if (file.exists()) file.length() else 0L

        val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
        }
        conn.connect()

        val code = conn.responseCode
        if (code != 200 && code != 206) {
            conn.disconnect()
            throw java.io.IOException("Server returned HTTP $code")
        }

        // asked for a range but server sent the whole file back — it
        // doesn't support resuming, so start over rather than corrupt
        // the file by appending at the wrong offset
        val append = existingBytes > 0 && code == 206
        if (existingBytes > 0 && code == 200) {
            file.delete()
        }

        val contentLength = conn.contentLengthLong
        val totalBytes = if (code == 206) existingBytes + contentLength else contentLength
        var downloaded = if (append) existingBytes else 0L

        FileOutputStream(file, append).use { out ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, totalBytes)
                }
            }
        }
        conn.disconnect()
    }

    // fetches the server-computed SHA-256 (model_server.py's /checksum
    // endpoint) and compares against a fresh hash of the local file —
    // catches corruption that a size-only check would miss
    suspend fun verify(): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        if (!file.exists()) return@withContext false

        val conn = URL(CHECKSUM_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val expectedSha256 = JSONObject(responseText).getString("sha256")

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        actualSha256.equals(expectedSha256, ignoreCase = true)
    }
}
