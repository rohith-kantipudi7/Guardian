package com.guardian.app.data.ai

import android.content.Context
import android.util.Log
import com.guardian.app.data.audio.AudioDecoder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device speech-to-text for the in-cabin audio black box, backed by whisper.cpp.
 *
 * The whisper.cpp source is vendored at the Android project root (`/whisper.cpp`, git-ignored) and
 * built natively via `app/src/main/cpp/CMakeLists.txt`. The ggml model lives in
 * `assets/models/ggml-tiny.en.bin` (git-ignored; see mobile README to fetch it).
 */
interface WhisperTranscriber {
    /** Transcribes the audio at [audioPath], or returns null when transcription is unavailable. */
    suspend fun transcribe(audioPath: String?): String?
}

/** No-op transcriber; used only as an explicit fallback when the native lib/model is absent. */
class StubWhisper : WhisperTranscriber {
    override suspend fun transcribe(audioPath: String?): String? = null
}

/** Real whisper.cpp transcriber: decodes the recorded clip to 16 kHz mono and runs ggml tiny.en. */
class WhisperCppTranscriber(private val context: Context) : WhisperTranscriber {
    private val mutex = Mutex()
    @Volatile private var whisper: WhisperContext? = null
    @Volatile private var unavailable = false

    private suspend fun ensureContext(): WhisperContext? {
        whisper?.let { return it }
        if (unavailable) return null
        return mutex.withLock {
            whisper ?: runCatching {
                WhisperContext.createContextFromAsset(context.assets, MODEL_ASSET)
            }.onFailure {
                Log.w(TAG, "Whisper model unavailable: ${it.message}")
                unavailable = true
            }.getOrNull()?.also { whisper = it }
        }
    }

    override suspend fun transcribe(audioPath: String?): String? {
        if (audioPath.isNullOrBlank()) return null
        val ctx = ensureContext() ?: return null
        val samples = AudioDecoder.decodeToMonoFloat16k(audioPath) ?: return null
        if (samples.isEmpty()) return null
        return runCatching {
            ctx.transcribeData(samples, printTimestamp = false).trim()
        }.onFailure { Log.w(TAG, "Transcription failed", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val MODEL_ASSET = "models/ggml-tiny.en.bin"
        const val TAG = "WhisperCpp"
    }
}

