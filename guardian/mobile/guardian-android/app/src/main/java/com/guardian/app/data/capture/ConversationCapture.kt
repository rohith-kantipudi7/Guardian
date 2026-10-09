package com.guardian.app.data.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ConversationClip(val path: String, val durationSec: Int)

/** Pre-impact (before) + post-impact (after) in-cabin audio captured for the black box. */
data class CrashAudio(
    val prePath: String?,
    val preDurationSec: Int,
    val postPath: String?,
    val postDurationSec: Int,
)

/**
 * In-cabin audio for the black box. A rolling pre-buffer records continuously while armed so the
 * crash clip includes the seconds BEFORE impact, then a longer clip is recorded AFTER impact.
 * One shared mic mutex keeps the pre-buffer and on-demand captures from colliding.
 */
class ConversationCapture(private val context: Context) {

    private val micMutex = Mutex()
    private var preJob: Job? = null

    @Volatile private var activeRecorder: MediaRecorder? = null
    @Volatile private var activeFile: File? = null
    @Volatile private var lastSegment: File? = null

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun configure(r: MediaRecorder, file: File) {
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setOutputFile(file.absolutePath)
    }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Total before + after seconds a crash clip is expected to span. */
    val expectedCrashAudioSec: Int get() = PRE_SECONDS + POST_SECONDS

    /** Continuously records short segments so the crash black box includes audio from BEFORE impact. */
    fun startPreBuffer(scope: CoroutineScope) {
        if (preJob?.isActive == true) return
        preJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (!micGranted()) {
                    delay(3000)
                    continue
                }
                val file = File(context.cacheDir, "prebuf_${System.currentTimeMillis()}.m4a")
                val started = micMutex.withLock {
                    runCatching {
                        val r = newRecorder()
                        configure(r, file)
                        r.prepare()
                        r.start()
                        activeRecorder = r
                        activeFile = file
                        true
                    }.getOrDefault(false)
                }
                if (!started) {
                    delay(1500)
                    continue
                }
                try {
                    delay(PRE_SECONDS * 1000L)
                } finally {
                    micMutex.withLock {
                        val r = activeRecorder
                        if (r != null) {
                            runCatching { r.stop() }
                            runCatching { r.release() }
                            lastSegment?.takeIf { it != file }?.let { old -> runCatching { old.delete() } }
                            lastSegment = activeFile
                            activeRecorder = null
                        }
                    }
                }
            }
        }
    }

    fun stopPreBuffer() {
        preJob?.cancel()
        preJob = null
    }

    /** On a crash: finalize the pre-impact audio ending at the crash, then record the after clip. */
    suspend fun captureAroundCrash(postSeconds: Int = POST_SECONDS): CrashAudio {
        preJob?.cancel()
        preJob = null
        val pre = withContext(Dispatchers.IO) {
            micMutex.withLock {
                val r = activeRecorder
                val f = activeFile
                activeRecorder = null
                if (r != null) {
                    val ok = runCatching { r.stop() }.isSuccess
                    runCatching { r.release() }
                    if (ok && f != null && f.length() > 0) f else lastSegment
                } else {
                    lastSegment
                }
            }
        }
        val preDur = pre?.let { durationSec(it) } ?: 0
        val post = capture(postSeconds)
        return CrashAudio(pre?.absolutePath, preDur, post?.path, post?.durationSec ?: 0)
    }

    /** Records a single clip now (the after-impact clip, and the demo capture). */
    suspend fun capture(seconds: Int = POST_SECONDS): ConversationClip? = withContext(Dispatchers.IO) {
        if (!micGranted()) return@withContext null
        micMutex.withLock {
            val file = File(context.cacheDir, "incabin_${System.currentTimeMillis()}.m4a")
            val recorder = newRecorder()
            try {
                configure(recorder, file)
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

    private fun durationSec(file: File): Int = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt()
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrDefault(0)

    private companion object {
        const val PRE_SECONDS = 12
        const val POST_SECONDS = 15
    }
}
