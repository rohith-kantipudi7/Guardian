package com.guardian.app

import com.guardian.app.data.mock.MockGuardianDeviceClient
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Scenarios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockGuardianDeviceClientTest {

    private fun TestScope.connected(): Pair<MockGuardianDeviceClient, MutableList<CrashEventRecord>> {
        // advanceUntilIdle() ignores backgroundScope, so the client must run on the test scope itself.
        val client = MockGuardianDeviceClient(this, runTicker = false, clock = { 1_000L })
        val events = mutableListOf<CrashEventRecord>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { client.events.toList(events) }
        client.connect()
        advanceUntilIdle()
        assertEquals(LinkState.CONNECTED, client.linkState.value)
        return client to events
    }

    @Test
    fun severeCrashIsDelivered() = runTest {
        val (client, events) = connected()
        client.send(DeviceCommand.Simulate(Scenarios.SEVERE_CRASH))
        advanceUntilIdle()
        assertEquals(CrashState.COOLDOWN, client.crashStatus.value?.state)
        assertTrue(events.last().delivered)
    }

    @Test
    fun minorImpactIsNotAnEmergency() = runTest {
        val (client, events) = connected()
        client.send(DeviceCommand.Simulate(Scenarios.MINOR_IMPACT))
        advanceUntilIdle()
        assertEquals(CrashState.NORMAL, client.crashStatus.value?.state)
        assertTrue(events.isEmpty())
    }

    @Test
    fun rolloverCanBeCancelledDuringVerification() = runTest {
        val (client, events) = connected()
        client.send(DeviceCommand.Simulate(Scenarios.ROLLOVER))
        advanceTimeBy(1_500)
        assertEquals(CrashState.VERIFYING, client.crashStatus.value?.state)
        client.send(DeviceCommand.Cancel)
        advanceUntilIdle()
        assertEquals(CrashState.NORMAL, client.crashStatus.value?.state)
        assertTrue(events.isEmpty())
    }

    @Test
    fun cellularOutageQueuesThenDelivers() = runTest {
        val (client, events) = connected()
        client.send(DeviceCommand.CellularOff)
        client.send(DeviceCommand.Simulate(Scenarios.SEVERE_CRASH))
        advanceUntilIdle()
        assertEquals(CrashState.WAIT_FOR_ACK, client.crashStatus.value?.state)
        assertFalse(events.last().delivered)
        assertEquals(1, client.status.value?.pendingEvents)

        client.send(DeviceCommand.CellularOn)
        advanceUntilIdle()
        assertTrue(events.last().delivered)
        assertEquals(0, client.status.value?.pendingEvents)
    }

    @Test
    fun commandsFailWhenDisconnected() = runTest {
        val (client, _) = connected()
        client.disconnect(userInitiated = true)
        assertFalse(client.send(DeviceCommand.Simulate(Scenarios.SEVERE_CRASH)))
    }
}
