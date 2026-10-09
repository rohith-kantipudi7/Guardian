package com.guardian.app.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Companion link to the Guardian vehicle device.
 *
 * Implementations: [com.guardian.app.data.mock.MockGuardianDeviceClient] (no hardware) and
 * [com.guardian.app.data.ble.BleGuardianDeviceClient] (ESP32 over BLE).
 *
 * The phone is NOT on the emergency path: losing this link never stops the device from
 * reporting a crash over its own cellular connection.
 */
interface GuardianDeviceClient {
    val linkState: StateFlow<LinkState>
    val status: StateFlow<DeviceStatus?>
    val crashStatus: StateFlow<CrashStatus?>
    /** Crash events pushed by the device (live and history replay). */
    val events: SharedFlow<CrashEventRecord>

    fun connect()
    /** [userInitiated] = true suppresses auto-reconnect (demo: "force phone disconnected"). */
    fun disconnect(userInitiated: Boolean = true)
    suspend fun send(command: DeviceCommand): Boolean
}
