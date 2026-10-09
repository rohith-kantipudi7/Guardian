package com.guardian.app.data

import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** Routes to the virtual, BLE or mock device; switchable at runtime from Settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableDeviceClient(
    private val clients: Map<DeviceSource, GuardianDeviceClient>,
    initial: DeviceSource,
    scope: CoroutineScope,
) : GuardianDeviceClient {

    private val selected = MutableStateFlow(clients.getValue(initial))

    var source: DeviceSource = initial
        private set

    fun select(source: DeviceSource) {
        val next = clients.getValue(source)
        if (next !== selected.value) {
            selected.value.disconnect(userInitiated = true)
            selected.value = next
            this.source = source
        }
    }

    override val linkState: StateFlow<LinkState> =
        selected.flatMapLatest { it.linkState }.stateIn(scope, SharingStarted.Eagerly, LinkState.DISCONNECTED)
    override val status: StateFlow<DeviceStatus?> =
        selected.flatMapLatest { it.status }.stateIn(scope, SharingStarted.Eagerly, null)
    override val crashStatus: StateFlow<CrashStatus?> =
        selected.flatMapLatest { it.crashStatus }.stateIn(scope, SharingStarted.Eagerly, null)
    override val events: SharedFlow<CrashEventRecord> =
        selected.flatMapLatest { it.events }.shareIn(scope, SharingStarted.Eagerly)

    override fun connect() = selected.value.connect()
    override fun disconnect(userInitiated: Boolean) = selected.value.disconnect(userInitiated)
    override suspend fun send(command: DeviceCommand): Boolean = selected.value.send(command)
    override suspend fun sendBlackBox(json: String): Boolean = selected.value.sendBlackBox(json)
}
