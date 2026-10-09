package com.guardian.app.data.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Cabin (front) + road (rear) photos captured right after a crash, for the black box. */
data class DualPhotos(val cabinPath: String?, val roadPath: String?)

/**
 * Captures one still from the front (cabin) and one from the rear (road) camera using CameraX,
 * with no preview — driven by a short-lived lifecycle so it works from the crash flow. A single
 * mutex serialises all captures so the continuous black-box buffer and one-off captures never
 * contend for the camera.
 */
class DualCameraCapture(private val context: Context) {

    private val mutex = Mutex()

    suspend fun capture(): DualPhotos = mutex.withLock {
        withContext(Dispatchers.Main) {
            val provider = runCatching { cameraProvider() }.getOrNull() ?: return@withContext DualPhotos(null, null)
            val lifecycle = CaptureLifecycle().also { it.resume() }
            val cabin = captureOne(provider, lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, "cabin", context.cacheDir)
            val road = captureOne(provider, lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, "road", context.cacheDir)
            provider.unbindAll()
            lifecycle.destroy()
            DualPhotos(cabin, road)
        }
    }

    /** Captures a single still from one camera into [outDir] (used by the continuous buffer). */
    suspend fun captureSingle(front: Boolean, outDir: File): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            val provider = runCatching { cameraProvider() }.getOrNull() ?: return@withContext null
            val lifecycle = CaptureLifecycle().also { it.resume() }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val path = captureOne(provider, lifecycle, selector, if (front) "cabin" else "road", outDir)
            provider.unbindAll()
            lifecycle.destroy()
            path
        }
    }

    private suspend fun captureOne(
        provider: ProcessCameraProvider,
        lifecycle: CaptureLifecycle,
        selector: CameraSelector,
        tag: String,
        outDir: File,
    ): String? {
        val imageCapture = ImageCapture.Builder().build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle, selector, imageCapture)
        } catch (e: Exception) {
            return null
        }
        outDir.mkdirs()
        val file = File(outDir, "${tag}_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        return suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(file.absolutePath)
                    override fun onError(exc: ImageCaptureException) = cont.resume(null)
                },
            )
        }
    }

    private suspend fun cameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

    /** Minimal lifecycle so CameraX can bind without an Activity or preview. */
    private class CaptureLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
