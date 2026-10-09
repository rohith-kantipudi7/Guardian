package com.guardian.app.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** State of the downloadable on-device LLM (Gemma/LLaMA) model file. */
sealed interface ModelState {
    data object Absent : ModelState
    data class Downloading(val pct: Int) : ModelState
    data class Ready(val path: String, val sizeMb: Long) : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Downloads and tracks the on-device LLM model. The [EdgeReasoner] works without it; once a
 * model is Ready, an LLM backend can use it to generate the assessment (same [OnDeviceAi] API).
 */
class LlmModelManager(context: Context) {
    private val file = File(File(context.filesDir, "models").apply { mkdirs() }, "guardian-llm.gguf")
    private val client = OkHttpClient()

    private val _state = MutableStateFlow(
        if (file.exists()) ModelState.Ready(file.path, file.length() / (1024 * 1024)) else ModelState.Absent,
    )
    val state: StateFlow<ModelState> = _state

    val isReady: Boolean get() = _state.value is ModelState.Ready

    suspend fun download(url: String) = withContext(Dispatchers.IO) {
        try {
            _state.value = ModelState.Downloading(0)
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) {
                    _state.value = ModelState.Failed("HTTP ${resp.code}")
                    return@withContext
                }
                val total = body.contentLength().coerceAtLeast(1)
                var read = 0L
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var n: Int
                        while (input.read(buf).also { n = it } >= 0) {
                            out.write(buf, 0, n)
                            read += n
                            _state.value = ModelState.Downloading(((read * 100) / total).toInt())
                        }
                    }
                }
            }
            _state.value = ModelState.Ready(file.path, file.length() / (1024 * 1024))
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: "download failed")
        }
    }

    fun delete() {
        file.delete()
        _state.value = ModelState.Absent
    }
}
