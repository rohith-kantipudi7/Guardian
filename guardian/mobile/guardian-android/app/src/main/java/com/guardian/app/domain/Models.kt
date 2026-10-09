package com.guardian.app.domain

enum class LinkState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

enum class Severity { NORMAL, MINOR, MODERATE, CRITICAL;
    companion object {
        fun parse(v: String?): Severity = entries.firstOrNull { it.name == v } ?: NORMAL
    }
}

/** Mirrors the firmware crash state machine (docs/crash-detection.md). */
enum class CrashState {
    NORMAL, SUSPECTED_IMPACT, ANALYZING, FALSE_POSITIVE, PROBABLE_CRASH, VERIFYING, CANCELLED,
    EMERGENCY_ACTIVE, TRANSMIT_EVENT, WAIT_FOR_ACK, COOLDOWN;

    val isAlert: Boolean get() = this in setOf(VERIFYING, EMERGENCY_ACTIVE, TRANSMIT_EVENT, WAIT_FOR_ACK)

    companion object {
        fun parse(v: String?): CrashState = entries.firstOrNull { it.name == v } ?: NORMAL
    }
}

data class DeviceStatus(
    val deviceId: String = "",
    val vehicleId: String = "",
    val firmware: String = "",
    val battery: Int? = null,
    val gps: String = "UNAVAILABLE",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyM: Double? = null,
    val cellular: String = "UNAVAILABLE",
    val vehicleConnected: Boolean = false,
    val vehicleSpeedKmh: Double? = null,
    val crashState: CrashState = CrashState.NORMAL,
    val pendingEvents: Int = 0,
    val lastUpdateMs: Long = 0,
)

data class CrashStatus(
    val state: CrashState,
    val severity: Severity? = null,
    val confidence: Double? = null,
    val reasons: String? = null,
    val verifyRemainingMs: Long? = null,
)

/** A crash event as seen by the phone, from BLE (device) or the backend. */
data class CrashEventRecord(
    val eventId: String,
    val timestamp: String,
    val severity: Severity,
    val confidence: Double,
    val latitude: Double?,
    val longitude: Double?,
    val phoneConnected: Boolean,
    val delivered: Boolean,
    val status: String,
    val source: String,
)

/** Someone the backend alerted automatically (police / hospital / family) and whether they opened it. */
data class Responder(
    val type: String,
    val name: String,
    val seen: Boolean,
) {
    val label: String get() = when (type) {
        "POLICE" -> "Police"
        "HOSPITAL" -> "Ambulance"
        else -> "Family"
    }
    val progress: String get() = if (seen) "Seen" else "Alerted"
}

/** Commands written to the device COMMAND characteristic (docs/ble-protocol.md). */
sealed class DeviceCommand(val cmd: String, val arg: String = "") {
    data object Ping : DeviceCommand("PING")
    data object Cancel : DeviceCommand("CANCEL")
    data object GetHistory : DeviceCommand("GET_HISTORY")
    data class Simulate(val scenario: String) : DeviceCommand("SIMULATE", scenario)
    data object CellularOff : DeviceCommand("CELLULAR_OFF")
    data object CellularOn : DeviceCommand("CELLULAR_ON")
}

object Scenarios {
    const val NORMAL = "normal"
    const val HARD_BRAKE = "hard_brake"
    const val POTHOLE = "pothole"
    const val MINOR_IMPACT = "minor_impact"
    const val SEVERE_CRASH = "severe_crash"
    const val ROLLOVER = "rollover"
}
