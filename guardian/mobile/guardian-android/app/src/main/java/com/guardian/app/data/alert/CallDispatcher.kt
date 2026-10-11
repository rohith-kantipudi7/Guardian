package com.guardian.app.data.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.guardian.app.data.settings.EmergencyContact
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class CallStatus { QUEUED, CALLING, DONE, FAILED, TEST_ONLY }

data class CallEntry(val label: String, val number: String, val status: CallStatus, val dial: Boolean = true)

/**
 * Places real calls over the SIM to the emergency contacts, one after another (a phone holds one
 * call at a time): the next is dialled when the current call ends. Police/ambulance numbers are
 * deliberately not auto-dialled.
 */
class CallDispatcher(private val context: Context) {

    private companion object {
        const val SPEAK_DELAY_MS = 7000L
    }

    private val tm = context.getSystemService(TelephonyManager::class.java)
    private var entries: List<CallEntry> = emptyList()
    private var onUpdate: (List<CallEntry>) -> Unit = {}
    private var index = -1
    private var sawOffHook = false
    private var active = false
    private var registered = false
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var message = ""
    private var modernCallback: Any? = null
    @Suppress("DEPRECATION") private var legacyListener: PhoneStateListener? = null

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Police/ambulance are only really dialled when [dialServices] is true; otherwise listed as test-only. */
    fun start(contacts: List<EmergencyContact>, spokenMessage: String, dialServices: Boolean, update: (List<CallEntry>) -> Unit) {
        if (active) return
        message = spokenMessage
        val services = listOf("Police" to "112", "Ambulance" to "108")
            .map { CallEntry(it.first, it.second, CallStatus.QUEUED, dial = dialServices) }
        entries = services + contacts.filter { it.phone.isNotBlank() }
            .map { CallEntry(it.name.ifBlank { "Contact" }, it.phone, CallStatus.QUEUED) }
        onUpdate = update
        update(entries)
        if (entries.isEmpty()) return
        active = true
        index = -1
        if (tts == null) tts = TextToSpeech(context) { ttsReady = it == TextToSpeech.SUCCESS }
        listen()
        dialNext()
    }

    fun stop() {
        active = false
        cancelSpeech()
        unlisten()
        entries = emptyList()
    }

    /** Downloads the message as Azure-synthesised speech via the backend so it plays offline later; best effort. */
    suspend fun prepareVoice(message: String, backendUrl: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val file = voiceFile(message)
        if (file.exists()) return@withContext
        runCatching {
            val body = org.json.JSONObject().put("text", message).toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(backendUrl.trimEnd('/') + "/api/ai/speak").post(body).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("TTS HTTP ${r.code}")
                file.writeBytes(r.body!!.bytes())
            }
        }
    }

    private fun voiceFile(message: String) =
        java.io.File(context.cacheDir, "call_voice_${message.hashCode().toUInt()}.mp3")

    private val http = OkHttpClient.Builder().callTimeout(40, java.util.concurrent.TimeUnit.SECONDS).build()
    private var player: android.media.MediaPlayer? = null

    /** There is no API to know when the callee picks up, so the message starts after a delay and repeats. */
    private fun scheduleSpeech() {
        handler.postDelayed({
            if (!active || message.isBlank()) return@postDelayed
            val audio = context.getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = true
            val cached = voiceFile(message)
            if (cached.exists() && playCached(cached)) return@postDelayed
            if (!ttsReady) return@postDelayed
            val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL) }
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "alert1")
            tts?.playSilentUtterance(1500, TextToSpeech.QUEUE_ADD, "gap")
            tts?.speak(message, TextToSpeech.QUEUE_ADD, params, "alert2")
        }, SPEAK_DELAY_MS)
    }

    private fun playCached(file: java.io.File, repeat: Int = 1): Boolean = runCatching {
        player?.release()
        player = android.media.MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener { if (repeat > 0 && active) { it.seekTo(0); it.start(); setOnCompletionListener(null) } }
            start()
        }
    }.isSuccess

    private fun cancelSpeech() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        runCatching { player?.release() }
        player = null
    }

    private fun setStatus(i: Int, s: CallStatus) {
        entries = entries.mapIndexed { j, e -> if (j == i) e.copy(status = s) else e }
        onUpdate(entries)
    }

    private fun dialNext() {
        if (!active) return
        cancelSpeech()
        if (index >= 0 && entries[index].status == CallStatus.CALLING) setStatus(index, CallStatus.DONE)
        index++
        if (index >= entries.size) {
            active = false
            unlisten()
            return
        }
        sawOffHook = false
        if (!entries[index].dial) {
            setStatus(index, CallStatus.TEST_ONLY)
            dialNext()
            return
        }
        val ok = granted(Manifest.permission.CALL_PHONE) && runCatching {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", entries[index].number, null), Bundle())
        }.isSuccess
        if (ok) {
            setStatus(index, CallStatus.CALLING)
            keepAppInFront()
        } else {
            setStatus(index, CallStatus.FAILED)
            dialNext()
        }
    }

    /** The call runs on the SIM; Android always shows the dialer, so pull our alert screen back over it. */
    private fun keepAppInFront() {
        listOf(800L, 2500L, 5000L).forEach { d ->
            handler.postDelayed({
                if (!active) return@postDelayed
                runCatching {
                    context.startActivity(
                        android.content.Intent(context, com.guardian.app.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                }
            }, d)
        }
    }

    private fun onState(state: Int) {
        if (!active) return
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (!sawOffHook) {
                sawOffHook = true
                scheduleSpeech()
                keepAppInFront()
            }
            TelephonyManager.CALL_STATE_IDLE -> if (sawOffHook) dialNext()
        }
    }

    private fun listen() {
        if (registered || !granted(Manifest.permission.READ_PHONE_STATE)) return
        registered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onState(state)
            }
            modernCallback = cb
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
            }
            legacyListener = l
            @Suppress("DEPRECATION")
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    @Suppress("DEPRECATION")
    private fun unlisten() {
        if (!registered) return
        registered = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modernCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            modernCallback = null
        } else {
            legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            legacyListener = null
        }
    }
}
