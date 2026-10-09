package com.guardian.app.data.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class ConversationClip(val path: String, val durationSec: Int)

/** Records a short in-cabin audio clip after a crash, to be stored in the black box. */
class ConversationCapture(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun capture(seconds: Int = 8): ConversationClip? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "incabin_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            delay(seconds * 1000L)
            recorder.stop()
            ConversationClip(file.absolutePath, seconds)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { recorder.release() }
        }
    }
}
