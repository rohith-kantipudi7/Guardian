package com.guardian.app.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Tiny base64 JPEG thumbnails (~2 KB) small enough for the ESP32's RAM black box. */
object ThumbnailEncoder {
    private const val WIDTH = 64
    private val cache = HashMap<String, String>()

    /** Cached per file path, so a new frame is encoded once. Returns "" if the file can't be decoded. */
    @Synchronized
    fun encode(path: String?): String {
        if (path == null) return ""
        cache[path]?.let { return it }
        val out = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return ""
            val sample = (bounds.outWidth / (WIDTH * 2)).coerceAtLeast(1)
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return ""
            val h = (bmp.height * WIDTH / bmp.width).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bmp, WIDTH, h, true)
            val bos = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 35, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
        if (cache.size > 8) cache.clear()
        cache[path] = out
        return out
    }
}
