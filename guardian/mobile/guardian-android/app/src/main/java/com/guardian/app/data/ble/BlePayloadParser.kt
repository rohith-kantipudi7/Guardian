package com.guardian.app.data.ble

import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

/** Pure JSON <-> domain mapping for BLE characteristic values (docs/ble-protocol.md). */
object BlePayloadParser {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(bytes: ByteArray): JsonObject? =
        runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    /** Merges one characteristic update into the current status. Returns null if not a status characteristic. */
    fun applyStatus(current: DeviceStatus, uuid: UUID, bytes: ByteArray, nowMs: Long): DeviceStatus? {
        val o = parse(bytes) ?: return null
        val s = when (uuid) {
            GuardianBleUuids.DEVICE_STATUS -> current.copy(
                deviceId = o.str("deviceId") ?: current.deviceId,
                vehicleId = o.str("vehicleId") ?: current.vehicleId,
                firmware = o.str("firmware") ?: current.firmware,
                battery = o.int("battery") ?: current.battery,
                gps = o.str("gps") ?: current.gps,
                cellular = o.str("cellular") ?: current.cellular,
                vehicleConnected = o.bool("vehicleConnected") ?: current.vehicleConnected,
                crashState = o.str("crashState")?.let(CrashState::parse) ?: current.crashState,
                pendingEvents = o.int("pendingEvents") ?: current.pendingEvents,
            )
            GuardianBleUuids.BATTERY -> current.copy(battery = o.int("battery") ?: current.battery)
            GuardianBleUuids.GPS_STATUS -> current.copy(
                gps = o.str("status") ?: current.gps,
                latitude = o.dbl("lat"),
                longitude = o.dbl("lon"),
                gpsAccuracyM = o.dbl("accuracy"),
            )
            GuardianBleUuids.CELLULAR_STATUS -> current.copy(
                cellular = o.str("status") ?: current.cellular,
                pendingEvents = o.int("pendingEvents") ?: current.pendingEvents,
            )
            GuardianBleUuids.VEHICLE_STATUS -> current.copy(
                vehicleConnected = o.bool("connected") ?: current.vehicleConnected,
                vehicleSpeedKmh = o.dbl("speed"),
            )
            else -> return null
        }
        return s.copy(lastUpdateMs = nowMs)
    }

    fun parseCrashStatus(bytes: ByteArray): CrashStatus? {
        val o = parse(bytes) ?: return null
        return CrashStatus(
            state = CrashState.parse(o.str("state")),
            severity = o.str("severity")?.let(Severity::parse),
            confidence = o.dbl("confidence"),
            reasons = o.str("reasons"),
            verifyRemainingMs = (o["verifyRemainingMs"] as? JsonPrimitive)?.longOrNull,
        )
    }

    fun parseEvent(bytes: ByteArray): CrashEventRecord? {
        val o = parse(bytes) ?: return null
        val id = o.str("eventId") ?: return null
        val delivered = o.bool("delivered") ?: false
        return CrashEventRecord(
            eventId = id,
            timestamp = o.str("timestamp") ?: "",
            severity = Severity.parse(o.str("severity")),
            confidence = o.dbl("confidence") ?: 0.0,
            latitude = o.dbl("lat"),
            longitude = o.dbl("lon"),
            phoneConnected = o.bool("phoneConnected") ?: false,
            delivered = delivered,
            status = if (delivered) "EMERGENCY_ACTIVE" else "QUEUED_ON_DEVICE",
            source = "DEVICE",
        )
    }

    fun encodeCommand(c: DeviceCommand): ByteArray = buildJsonObject {
        put("cmd", c.cmd)
        if (c.arg.isNotEmpty()) put("arg", c.arg)
    }.toString().encodeToByteArray()
}
