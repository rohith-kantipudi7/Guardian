package com.guardian.app

import com.guardian.app.data.virtual.SimOutcome
import com.guardian.app.data.virtual.SimState
import com.guardian.app.data.virtual.toCrashStatus
import com.guardian.app.data.virtual.toDeviceStatus
import com.guardian.app.data.virtual.toEvent
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDeviceMappingTest {
    private fun outcome(emergency: Boolean, delivered: Boolean, severity: String? = "CRITICAL", transitions: List<String> = listOf("SUSPECTED_IMPACT")) =
        SimOutcome("severe_crash", emergency, delivered, "EVT-1", severity, 0.92, listOf("high_deceleration"), transitions)

    @Test
    fun deliveredCrashIsEmergencyActive() {
        val c = outcome(emergency = true, delivered = true).toCrashStatus()
        assertEquals(CrashState.EMERGENCY_ACTIVE, c.state)
        assertEquals(Severity.CRITICAL, c.severity)
        assertEquals("high_deceleration", c.reasons)
    }

    @Test
    fun crashInDeadZoneWaitsForAck() {
        val o = outcome(emergency = true, delivered = false)
        assertEquals(CrashState.WAIT_FOR_ACK, o.toCrashStatus().state)
        val e = o.toEvent(now = 0)
        assertFalse(e.delivered)
        assertEquals("QUEUED_ON_DEVICE", e.status)
        assertTrue(o.toEvent(now = 0, delivered = true).delivered)
    }

    @Test
    fun rejectedImpactIsFalsePositiveAndQuietDriveIsNormal() {
        assertEquals(CrashState.FALSE_POSITIVE, outcome(false, false, "NORMAL").toCrashStatus().state)
        assertEquals(CrashState.NORMAL, outcome(false, false, null, emptyList()).toCrashStatus().state)
    }

    @Test
    fun networkModeMapsToCellularStatus() {
        val s = SimState(deviceId = "GD-001", vehicleId = "VH-001", phoneConnected = true, network = "OFFLINE", battery = 90, pendingEvents = 1)
        val d = s.toDeviceStatus(CrashState.NORMAL, now = 5)
        assertEquals("DISCONNECTED", d.cellular)
        assertEquals(1, d.pendingEvents)
        assertEquals("CONNECTED", s.copy(network = "ONLINE").toDeviceStatus(CrashState.NORMAL, 5).cellular)
    }
}
