package com.guardian.app.data

import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No IoT device connected. The phone is primary and fully functional on its own; this source
 * reports an honest "disconnected / no status" instead of a stand-in device, so the dashboard
 * never shows a fake battery or vehicle until a real Guardian device is paired.
 */
class NoDeviceClient : GuardianDeviceClient {
    override val linkState: StateFlow<LinkState> = MutableStateFlow(LinkState.DISCONNECTED)
    override val status: StateFlow<DeviceStatus?> = MutableStateFlow(null)
    override val crashStatus: StateFlow<CrashStatus?> = MutableStateFlow(CrashStatus(CrashState.NORMAL))
    override val events: SharedFlow<CrashEventRecord> = MutableSharedFlow()

    override fun connect() {}
    override fun disconnect(userInitiated: Boolean) {}
    override suspend fun send(command: DeviceCommand): Boolean = false
    override suspend fun sendBlackBox(json: String): Boolean = false
}
