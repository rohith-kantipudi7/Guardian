package com.guardian.app

import com.guardian.app.data.ble.BlePayloadParser
import com.guardian.app.data.ble.GuardianBleUuids
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePayloadParserTest {

    @Test
    fun uuidsMatchFirmware() {
        assertEquals("47550000-4152-4449-414e-000000000000", GuardianBleUuids.SERVICE.toString())
        assertEquals("47550008-4152-4449-414e-000000000000", GuardianBleUuids.COMMAND.toString())
    }

    @Test
    fun parsesDeviceStatus() {
        val json = """{"deviceId":"GD-001","vehicleId":"VH-001","firmware":"0.1.0","battery":91,"gps":"AVAILABLE",
            "cellular":"CONNECTED","vehicleConnected":true,"crashState":"NORMAL","pendingEvents":0}"""
        val s = BlePayloadParser.applyStatus(DeviceStatus(), GuardianBleUuids.DEVICE_STATUS, json.encodeToByteArray(), 5)!!
        assertEquals("GD-001", s.deviceId)
        assertEquals(91, s.battery)
        assertEquals("CONNECTED", s.cellular)
        assertTrue(s.vehicleConnected)
        assertEquals(5L, s.lastUpdateMs)
    }

    @Test
    fun vehicleSpeedMayBeNull() {
        val s = BlePayloadParser.applyStatus(
            DeviceStatus(vehicleSpeedKmh = 50.0),
            GuardianBleUuids.VEHICLE_STATUS,
            """{"connected":false,"speed":null,"rpm":null}""".encodeToByteArray(),
            0,
        )!!
        assertNull(s.vehicleSpeedKmh)
        assertFalse(s.vehicleConnected)
    }

    @Test
    fun parsesCrashStatus() {
        val c = BlePayloadParser.parseCrashStatus(
            """{"state":"VERIFYING","severity":"MODERATE","confidence":0.63,"reasons":"impact,rapid_rotation","verifyRemainingMs":9000}"""
                .encodeToByteArray(),
        )!!
        assertEquals(CrashState.VERIFYING, c.state)
        assertEquals(Severity.MODERATE, c.severity)
        assertEquals(9000L, c.verifyRemainingMs)
        assertTrue(c.state.isAlert)
    }

    @Test
    fun parsesEvent() {
        val e = BlePayloadParser.parseEvent(
            """{"eventId":"EVT-1","timestamp":"2026-10-05T10:30:00Z","severity":"CRITICAL","confidence":0.92,
                "lat":12.97,"lon":77.59,"phoneConnected":false,"delivered":true}""".encodeToByteArray(),
        )!!
        assertEquals("EVT-1", e.eventId)
        assertEquals(Severity.CRITICAL, e.severity)
        assertFalse(e.phoneConnected)
        assertTrue(e.delivered)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(BlePayloadParser.parseEvent("not json".encodeToByteArray()))
        assertNull(BlePayloadParser.parseEvent("""{"severity":"CRITICAL"}""".encodeToByteArray()))
    }

    @Test
    fun encodesCommands() {
        assertEquals("""{"cmd":"CANCEL"}""", BlePayloadParser.encodeCommand(DeviceCommand.Cancel).decodeToString())
        assertEquals(
            """{"cmd":"SIMULATE","arg":"severe_crash"}""",
            BlePayloadParser.encodeCommand(DeviceCommand.Simulate("severe_crash")).decodeToString(),
        )
    }
}
