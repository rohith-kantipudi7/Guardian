package com.guardian.app.data.mock

import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Scenarios
import com.guardian.app.domain.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Hardware-free Guardian device for UI development and demos without the ESP32.
 * Mirrors the firmware state machine timing. Events stay local: in a real system the
 * device (or the Python simulator) owns the emergency path to the backend.
 */
class MockGuardianDeviceClient(
    private val scope: CoroutineScope,
    private val runTicker: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : GuardianDeviceClient {

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    private val _status = MutableStateFlow<DeviceStatus?>(null)
    private val _crash = MutableStateFlow<CrashStatus?>(CrashStatus(CrashState.NORMAL))
    private val _events = MutableSharedFlow<CrashEventRecord>(replay = 0, extraBufferCapacity = 64)

    override val linkState: StateFlow<LinkState> = _link
    override val status: StateFlow<DeviceStatus?> = _status
    override val crashStatus: StateFlow<CrashStatus?> = _crash
    override val events: SharedFlow<CrashEventRecord> = _events

    private val history = mutableListOf<CrashEventRecord>()
    private var cellularOn = true
    private var ticker: Job? = null
    private var scenario: Job? = null
    private var cancelRequested = false
    private var counter = 0

    private fun baseStatus() = DeviceStatus(
        deviceId = "GD-001",
        vehicleId = "VH-001",
        firmware = "mock-0.1.0",
        battery = 92,
        gps = "AVAILABLE",
        latitude = 12.9716,
        longitude = 77.5946,
        gpsAccuracyM = 5.0,
        cellular = if (cellularOn) "CONNECTED" else "DISCONNECTED",
        vehicleConnected = true,
        vehicleSpeedKmh = 48.0,
        crashState = _crash.value?.state ?: CrashState.NORMAL,
        pendingEvents = history.count { !it.delivered },
        lastUpdateMs = clock(),
    )

    override fun connect() {
        if (_link.value != LinkState.DISCONNECTED) return
        scope.launch {
            _link.value = LinkState.CONNECTING
            delay(300)
            _link.value = LinkState.CONNECTED
            _status.value = baseStatus()
            history.forEach { _events.emit(it) }
            if (runTicker) {
                ticker = scope.launch {
                    while (isActive) {
                        delay(1000)
                        val s = baseStatus()
                        _status.value = s.copy(vehicleSpeedKmh = 45.0 + (clock() / 1000 % 10))
                    }
                }
            }
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        ticker?.cancel()
        _link.value = LinkState.DISCONNECTED
    }

    override suspend fun send(command: DeviceCommand): Boolean {
        if (_link.value != LinkState.CONNECTED) return false
        when (command) {
            DeviceCommand.Ping -> _status.value = baseStatus()
            DeviceCommand.Cancel -> cancelRequested = true
            DeviceCommand.GetHistory -> history.forEach { _events.emit(it) }
            DeviceCommand.CellularOff -> setCellular(false)
            DeviceCommand.CellularOn -> setCellular(true)
            is DeviceCommand.Simulate -> {
                scenario?.cancel()
                scenario = scope.launch { run(command.scenario) }
            }
        }
        return true
    }

    private suspend fun setCellular(on: Boolean) {
        cellularOn = on
        if (on) {
            history.replaceAll { if (it.delivered) it else it.copy(delivered = true, status = "EMERGENCY_ACTIVE") }
            history.forEach { _events.emit(it) }
        }
        _status.value = baseStatus()
    }

    private fun state(s: CrashState, severity: Severity? = null, confidence: Double? = null, remaining: Long? = null) {
        _crash.value = CrashStatus(s, severity, confidence, null, remaining)
        _status.value = baseStatus()
    }

    private suspend fun run(name: String) {
        cancelRequested = false
        val (severity, confidence) = when (name) {
            Scenarios.SEVERE_CRASH -> Severity.CRITICAL to 0.92
            Scenarios.ROLLOVER -> Severity.MODERATE to 0.63
            Scenarios.MINOR_IMPACT -> Severity.MINOR to 0.31
            Scenarios.POTHOLE -> Severity.NORMAL to 0.0
            else -> return // normal driving / hard braking never trigger
        }
        state(CrashState.SUSPECTED_IMPACT)
        delay(1000) // analysis window
        state(CrashState.ANALYZING)
        if (severity == Severity.NORMAL || severity == Severity.MINOR) {
            state(CrashState.FALSE_POSITIVE, severity, confidence)
            delay(500)
            state(CrashState.NORMAL)
            return
        }
        state(CrashState.PROBABLE_CRASH, severity, confidence)
        if (severity == Severity.MODERATE) {
            var remaining = VERIFY_MS
            while (remaining > 0) {
                state(CrashState.VERIFYING, severity, confidence, remaining)
                if (cancelRequested) {
                    state(CrashState.CANCELLED, severity, confidence)
                    delay(500)
                    state(CrashState.NORMAL)
                    return
                }
                delay(1000)
                remaining -= 1000
            }
        }
        state(CrashState.EMERGENCY_ACTIVE, severity, confidence)
        val event = CrashEventRecord(
            eventId = "EVT-MOCK-${clock()}-${++counter}",
            timestamp = Instant.ofEpochMilli(clock()).toString(),
            severity = severity,
            confidence = confidence,
            latitude = 12.9716,
            longitude = 77.5946,
            phoneConnected = _link.value == LinkState.CONNECTED,
            delivered = false,
            status = "QUEUED_ON_DEVICE",
            source = "MOCK",
        )
        history.add(0, event)
        _events.emit(event)
        state(CrashState.TRANSMIT_EVENT, severity, confidence)
        state(CrashState.WAIT_FOR_ACK, severity, confidence)
        if (cellularOn) {
            delay(800)
            val delivered = event.copy(delivered = true, status = "EMERGENCY_ACTIVE")
            history[history.indexOfFirst { it.eventId == event.eventId }] = delivered
            _events.emit(delivered)
            state(CrashState.COOLDOWN, severity, confidence)
        }
    }

    private companion object {
        const val VERIFY_MS = 10_000L
    }
}
