package com.guardian.app.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** Decodes a compressed audio file (AAC/m4a) into 16 kHz mono float PCM for whisper.cpp. */
object AudioDecoder {
    private const val TARGET_RATE = 16000

    fun decodeToMonoFloat16k(path: String): FloatArray? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val inFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inFormat, null, null, 0)
            codec.start()

            val pcm = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                    else -> if (outIndex >= 0) {
                        if (info.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            for (s in shorts) pcm.add(s)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                    }
                }
            }
            codec.stop()
            codec.release()
            toMono16kFloat(pcm, sampleRate, channels)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun toMono16kFloat(interleaved: List<Short>, sampleRate: Int, channels: Int): FloatArray {
        if (interleaved.isEmpty() || channels <= 0) return FloatArray(0)
        val frames = interleaved.size / channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += interleaved[i * channels + c].toInt()
            mono[i] = (sum.toFloat() / channels) / 32768f
        }
        if (sampleRate == TARGET_RATE || mono.isEmpty()) return mono
        val outLen = (mono.size.toLong() * TARGET_RATE / sampleRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val ratio = mono.size.toFloat() / outLen
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val a = mono[idx.coerceIn(0, mono.size - 1)]
            val b = mono[(idx + 1).coerceIn(0, mono.size - 1)]
            out[i] = a + (b - a) * frac
        }
        return out
    }
}
