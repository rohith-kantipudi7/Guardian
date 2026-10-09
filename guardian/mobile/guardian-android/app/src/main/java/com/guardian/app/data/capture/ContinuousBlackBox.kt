package com.guardian.app.data.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Always-on vehicle black box: while armed, the phone captures a still every 10 s (≈6/min,
 * alternating cabin + road) into a rolling 10-minute window — older frames are overridden. A
 * crash then uses the most recent buffered frames instead of opening the camera at the chaotic
 * moment of impact (which was unreliable). Audio is buffered separately by [ConversationCapture].
 */
class ContinuousBlackBox(
    private val context: Context,
    private val camera: DualCameraCapture,
) {
    private val dir = File(context.cacheDir, "blackbox_photos")
    private var job: Job? = null

    @Volatile private var lastCabin: String? = null
    @Volatile private var lastRoad: String? = null

    private fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        dir.mkdirs()
        job = scope.launch(Dispatchers.Default) {
            var front = true
            while (isActive) {
                if (cameraGranted()) {
                    val path = runCatching { camera.captureSingle(front, dir) }.getOrNull()
                    if (path != null) {
                        if (front) lastCabin = path else lastRoad = path
                    }
                    prune()
                    front = !front
                }
                delay(PHOTO_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** The newest buffered cabin + road frames, for a crash bundle. */
    fun latest(): DualPhotos = DualPhotos(lastCabin, lastRoad)

    /** Drops frames older than the rolling window (overrides the oldest ~10 minutes). */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        runCatching {
            dir.listFiles()?.forEach { f -> if (f.lastModified() < cutoff) f.delete() }
        }
    }

    private companion object {
        const val PHOTO_INTERVAL_MS = 10_000L // one still every 10 s ≈ 6 per minute
        const val WINDOW_MS = 10 * 60 * 1000L // keep the last 10 minutes
    }
}
