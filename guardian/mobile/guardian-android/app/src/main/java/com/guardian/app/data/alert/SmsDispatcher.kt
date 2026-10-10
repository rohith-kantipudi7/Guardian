package com.guardian.app.data.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.guardian.app.data.settings.EmergencyContact
import com.guardian.app.domain.Severity

/** One alert message and whether it was actually sent over cellular SMS. */
data class AlertDispatch(
    val label: String,
    val recipient: String,
    val message: String,
    val sent: Boolean,
)

/**
 * Sends the real crash alert by cellular SMS — works with no internet. Family / configured contacts
 * get a real text; the police/ambulance desks are composed and shown (we don't auto-text 112/108 in
 * a demo). The exact messages are returned so the UI can showcase what went out.
 */
class SmsDispatcher(private val context: Context) {

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun canSend() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun dispatch(
        severity: Severity,
        confidence: Double,
        locationCoords: String?,
        vehicle: String,
        contacts: List<EmergencyContact>,
    ): List<AlertDispatch> {
        val where = locationCoords?.let { "https://maps.google.com/?q=$it" } ?: "location unavailable"
        val base = "GUARDIAN crash alert: ${severity.name} impact detected " +
            "(${(confidence * 100).toInt()}%) on $vehicle. Location: $where."
        val out = mutableListOf<AlertDispatch>()
        // Emergency desks: composed for the demo, not auto-dialled.
        out += AlertDispatch("Police control room", "112", "$base Please dispatch.", sent = false)
        out += AlertDispatch("Ambulance", "108", "$base Medical assistance may be required.", sent = false)
        // Real SMS to the occupant's configured contacts.
        for (c in contacts) {
            val msg = "$base This is an automated alert from ${c.name.ifBlank { "a Guardian user" }}'s vehicle."
            out += AlertDispatch(c.name.ifBlank { "Contact" }, c.phone, msg, sent = send(c.phone, msg))
        }
        return out
    }

    private fun send(phone: String, message: String): Boolean {
        if (!canSend() || phone.isBlank()) return false
        return runCatching {
            val sms = smsManager()
            val parts = sms.divideMessage(message)
            if (parts.size > 1) {
                sms.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                sms.sendTextMessage(phone, null, message, null, null)
            }
            true
        }.getOrDefault(false)
    }
}
