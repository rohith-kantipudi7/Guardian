package com.guardian.app.data.ai

/**
 * On-device speech-to-text for the in-cabin audio black box, backed by whisper.cpp.
 *
 * The whisper.cpp source is vendored at the Android project root (`/whisper.cpp`, git-ignored).
 * See `whisper.cpp/examples/whisper.android/lib` for the JNI + CMake build that turns [StubWhisper]
 * into a real transcriber: add an `externalNativeBuild` CMake target, a JNI wrapper, and ship a
 * `ggml-tiny.en.bin` model. Until that native lib is wired, [StubWhisper] keeps the app building.
 */
interface WhisperTranscriber {
    /** Transcribes the WAV/PCM at [audioPath], or returns null when transcription is unavailable. */
    suspend fun transcribe(audioPath: String?): String?
}

/** No-op transcriber used until the whisper.cpp native lib (JNI/CMake + ggml model) is wired in. */
class StubWhisper : WhisperTranscriber {
    override suspend fun transcribe(audioPath: String?): String? = null
}
