package com.guardian.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Loud local alarm raised the instant a crash is detected: a synthesized two-tone emergency siren
 * (not a music ringtone) on the alarm stream, plus a repeating vibration. Keeps wailing until the
 * occupant taps I'M SAFE / dismisses, so bystanders and responders are drawn to the vehicle.
 */
class AlarmSiren(private val context: Context) {

    private var track: AudioTrack? = null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun start() {
        if (track != null) return
        raiseAlarmVolume()
        runCatching { track = buildSiren().also { it.play() } }
        val pattern = longArrayOf(0, 700, 300)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    fun stop() {
        runCatching {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        }
        track = null
        runCatching { vibrator?.cancel() }
    }

    /**
     * One seamless loop of a two-tone (lo/hi) siren. Each tone is a whole number of wave cycles so
     * there are no clicks at the tone or loop boundaries.
     */
    private fun buildSiren(): AudioTrack {
        val lo = 735.0 // 44100 / 735 = 60 samples per cycle (exact)
        val hi = 1050.0 // 44100 / 1050 = 42 samples per cycle (exact)
        val seg = SAMPLE_RATE * 2 / 5 // 0.4 s per tone (whole cycles for both tones)
        val amp = Short.MAX_VALUE * 0.85
        val buf = ShortArray(seg * 2)
        for (i in 0 until seg) buf[i] = (sin(2 * PI * lo * i / SAMPLE_RATE) * amp).toInt().toShort()
        for (i in 0 until seg) buf[seg + i] = (sin(2 * PI * hi * i / SAMPLE_RATE) * amp).toInt().toShort()

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack(attrs, format, buf.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
        t.write(buf, 0, buf.size)
        t.setLoopPoints(0, buf.size, -1) // loop forever
        return t
    }

    private fun raiseAlarmVolume() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
