package com.guardian.app.data.cloud

import com.guardian.app.data.remote.DeviceDto
import com.guardian.app.data.remote.GuardianApi
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun DeviceDto.toStatus(now: Long) = DeviceStatus(
    deviceId = id,
    vehicleId = "",
    firmware = firmwareVersion ?: "",
    battery = batteryLevel,
    gps = gpsStatus ?: "UNAVAILABLE",
    latitude = lastLatitude,
    longitude = lastLongitude,
    gpsAccuracyM = null,
    cellular = cellularStatus ?: if (online) "CONNECTED" else "DISCONNECTED",
    vehicleConnected = vehicleConnected,
    vehicleSpeedKmh = null,
    crashState = CrashState.NORMAL,
    pendingEvents = 0,
    lastUpdateMs = now,
)

/**
 * Connects to the real Guardian device over Wi-Fi through the backend: the device heartbeats to
 * the cloud (shown in-app as the mobile link / LTE) and the phone reads its live status. No
 * Bluetooth. Crashes arrive via the backend event stream in GuardianRepository.
 */
class CloudDeviceClient(
    private val scope: CoroutineScope,
    private val baseUrl: () -> String,
    private val deviceId: () -> String,
) : GuardianDeviceClient {

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    private val _status = MutableStateFlow<DeviceStatus?>(null)
    private val _crash = MutableStateFlow<CrashStatus?>(CrashStatus(CrashState.NORMAL))
    private val _events = MutableSharedFlow<CrashEventRecord>(extraBufferCapacity = 64)

    override val linkState: StateFlow<LinkState> = _link
    override val status: StateFlow<DeviceStatus?> = _status
    override val crashStatus: StateFlow<CrashStatus?> = _crash
    override val events: SharedFlow<CrashEventRecord> = _events

    private var api: GuardianApi? = null
    private var apiUrl: String? = null
    private var poller: Job? = null

    private fun api(): GuardianApi {
        val url = baseUrl()
        if (api == null || apiUrl != url) {
            api = GuardianApi.create(url)
            apiUrl = url
        }
        return api!!
    }

    override fun connect() {
        if (_link.value == LinkState.DISCONNECTED) _link.value = LinkState.CONNECTING
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                try {
                    val d = api().device(deviceId())
                    _status.value = d.toStatus(System.currentTimeMillis())
                    _link.value = if (d.online) LinkState.CONNECTED else LinkState.CONNECTING
                } catch (e: Exception) {
                    _link.value = LinkState.CONNECTING
                }
                delay(POLL_MS)
            }
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        poller?.cancel()
        poller = null
        _link.value = LinkState.DISCONNECTED
    }

    override suspend fun send(command: DeviceCommand): Boolean = false

    private companion object {
        const val POLL_MS = 3000L
    }
}
