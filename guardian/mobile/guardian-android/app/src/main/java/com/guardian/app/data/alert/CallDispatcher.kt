package com.guardian.app.data.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.guardian.app.data.settings.EmergencyContact

enum class CallStatus { QUEUED, CALLING, DONE, FAILED }

data class CallEntry(val label: String, val number: String, val status: CallStatus)

/**
 * Places real calls over the SIM to the emergency contacts, one after another (a phone holds one
 * call at a time): the next is dialled when the current call ends. Police/ambulance numbers are
 * deliberately not auto-dialled.
 */
class CallDispatcher(private val context: Context) {

    private val tm = context.getSystemService(TelephonyManager::class.java)
    private var entries: List<CallEntry> = emptyList()
    private var onUpdate: (List<CallEntry>) -> Unit = {}
    private var index = -1
    private var sawOffHook = false
    private var active = false
    private var registered = false
    private var modernCallback: Any? = null
    @Suppress("DEPRECATION") private var legacyListener: PhoneStateListener? = null

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun start(contacts: List<EmergencyContact>, update: (List<CallEntry>) -> Unit) {
        if (active) return
        entries = contacts.filter { it.phone.isNotBlank() }
            .map { CallEntry(it.name.ifBlank { "Contact" }, it.phone, CallStatus.QUEUED) }
        onUpdate = update
        update(entries)
        if (entries.isEmpty()) return
        active = true
        index = -1
        listen()
        dialNext()
    }

    fun stop() {
        active = false
        unlisten()
        entries = emptyList()
    }

    private fun setStatus(i: Int, s: CallStatus) {
        entries = entries.mapIndexed { j, e -> if (j == i) e.copy(status = s) else e }
        onUpdate(entries)
    }

    private fun dialNext() {
        if (!active) return
        if (index >= 0) setStatus(index, if (entries[index].status == CallStatus.FAILED) CallStatus.FAILED else CallStatus.DONE)
        index++
        if (index >= entries.size) {
            active = false
            unlisten()
            return
        }
        sawOffHook = false
        val ok = granted(Manifest.permission.CALL_PHONE) && runCatching {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", entries[index].number, null), Bundle())
        }.isSuccess
        if (ok) setStatus(index, CallStatus.CALLING) else {
            setStatus(index, CallStatus.FAILED)
            dialNext()
        }
    }

    private fun onState(state: Int) {
        if (!active) return
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> sawOffHook = true
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
