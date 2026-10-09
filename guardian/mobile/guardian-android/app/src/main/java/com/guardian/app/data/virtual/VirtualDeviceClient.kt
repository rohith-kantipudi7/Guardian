package com.guardian.app.data.virtual

import android.util.Log
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

@Serializable
data class SimOutcome(
    val scenario: String,
    val emergency: Boolean,
    val delivered: Boolean,
    val eventId: String? = null,
    val severity: String? = null,
    val confidence: Double? = null,
    val reasons: List<String> = emptyList(),
    val transitions: List<String> = emptyList(),
)

@Serializable
data class SimState(
    val deviceId: String,
    val vehicleId: String? = null,
    val phoneConnected: Boolean,
    val network: String,
    val battery: Int? = null,
    val pendingEvents: Int = 0,
    val lastHeartbeatOk: Boolean? = null,
    val lastOutcome: SimOutcome? = null,
    val seq: Int = 0,
)

/** Default demo location, matches the simulator's GPS fix. */
private const val LAT = 12.97189
private const val LON = 77.59489

internal fun SimState.toDeviceStatus(crashState: CrashState, now: Long) = DeviceStatus(
    deviceId = deviceId,
    vehicleId = vehicleId.orEmpty(),
    firmware = "virtual-device",
    battery = battery,
    gps = "AVAILABLE",
    latitude = LAT,
    longitude = LON,
    gpsAccuracyM = 5.0,
    cellular = when (network) {
        "ONLINE" -> "CONNECTED"
        "DELAYED" -> "DELAYED"
        else -> "DISCONNECTED"
    },
    vehicleConnected = true,
    vehicleSpeedKmh = null,
    crashState = crashState,
    pendingEvents = pendingEvents,
    lastUpdateMs = now,
)

/** What the device's crash engine would push to a linked phone for this outcome. */
internal fun SimOutcome.toCrashStatus(): CrashStatus {
    val sev = severity?.let(Severity::parse)
    val state = when {
        emergency && delivered -> CrashState.EMERGENCY_ACTIVE
        emergency -> CrashState.WAIT_FOR_ACK
        transitions.isNotEmpty() -> CrashState.FALSE_POSITIVE
        else -> CrashState.NORMAL
    }
    return CrashStatus(state, sev, confidence, reasons.joinToString(",").ifEmpty { null })
}

internal fun SimOutcome.toEvent(now: Long, delivered: Boolean = this.delivered) = CrashEventRecord(
    eventId = eventId ?: "EVT-VIRTUAL-$now",
    timestamp = Instant.ofEpochMilli(now).toString(),
    severity = Severity.parse(severity),
    confidence = confidence ?: 0.0,
    latitude = LAT,
    longitude = LON,
    phoneConnected = true,
    delivered = delivered,
    status = if (delivered) "EMERGENCY_ACTIVE" else "QUEUED_ON_DEVICE",
    source = "DEVICE",
)

/**
 * Companion link to the **virtual** Guardian device (`python simulator.py serve`), standing in for
 * Bluetooth when there is no ESP32. It is the same device the web showcase drives, so
 * "Phone gone" there drops the link here, and a crash on the device pops up on this phone.
 *
 * Like BLE, this is companion-only: the virtual device reports emergencies to the backend itself.
 */
class VirtualDeviceClient(
    private val scope: CoroutineScope,
    private val baseUrl: () -> String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) : GuardianDeviceClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    private val _status = MutableStateFlow<DeviceStatus?>(null)
    private val _crash = MutableStateFlow<CrashStatus?>(CrashStatus(CrashState.NORMAL))
    private val _events = MutableSharedFlow<CrashEventRecord>(extraBufferCapacity = 64)

    override val linkState: StateFlow<LinkState> = _link
    override val status: StateFlow<DeviceStatus?> = _status
    override val crashStatus: StateFlow<CrashStatus?> = _crash
    override val events: SharedFlow<CrashEventRecord> = _events

    private var poller: Job? = null
    private var lastSeq: Int? = null
    private var awaitingDelivery: SimOutcome? = null
    private var linked = false

    override fun connect() {
        if (_link.value == LinkState.DISCONNECTED) _link.value = LinkState.CONNECTING
        scope.launch {
            post("/phone/connect")?.let(::apply)
            startPolling()
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        scope.launch { post("/phone/disconnect")?.let(::apply) }
    }

    override suspend fun send(command: DeviceCommand): Boolean {
        if (!linked) return false
        val path = when (command) {
            is DeviceCommand.Simulate -> "/scenario/${command.scenario}"
            DeviceCommand.CellularOff -> "/network/offline"
            DeviceCommand.CellularOn -> "/network/online"
            DeviceCommand.Cancel, DeviceCommand.Ping, DeviceCommand.GetHistory -> return true
        }
        return post(path)?.also(::apply) != null
    }

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                val s = get()
                if (s == null) {
                    linked = false
                    _link.value = LinkState.DISCONNECTED
                } else {
                    apply(s)
                }
                delay(POLL_MS)
            }
        }
    }

    private fun apply(s: SimState) {
        linked = s.phoneConnected
        _link.value = if (s.phoneConnected) LinkState.CONNECTED else LinkState.DISCONNECTED
        val now = clock()
        val previous = lastSeq
        lastSeq = s.seq
        val outcome = s.lastOutcome
        // Only react to outcomes produced while we are watching, and only if the phone is linked (BLE is up).
        if (previous != null && s.seq != previous && outcome != null && s.phoneConnected) {
            _crash.value = outcome.toCrashStatus()
            if (outcome.emergency) {
                _events.tryEmit(outcome.toEvent(now))
                awaitingDelivery = outcome.takeIf { !it.delivered }
            }
        }
        val queued = awaitingDelivery
        if (queued != null && s.pendingEvents == 0 && s.network != "OFFLINE") {
            awaitingDelivery = null
            _crash.value = queued.toCrashStatus().copy(state = CrashState.EMERGENCY_ACTIVE)
            _events.tryEmit(queued.toEvent(now, delivered = true))
        }
        _status.value = s.toDeviceStatus(_crash.value?.state ?: CrashState.NORMAL, now)
    }

    private suspend fun get(): SimState? = call(Request.Builder().url(url("/state")).get().build())

    private suspend fun post(path: String): SimState? =
        call(Request.Builder().url(url(path)).post("{}".toRequestBody(JSON)).build())

    private fun url(path: String) = baseUrl().trimEnd('/') + path

    private suspend fun call(req: Request): SimState? = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else json.decodeFromString<SimState>(r.body!!.string())
            }
        } catch (e: Exception) {
            Log.d("GuardianVirtual", "virtual device unreachable at ${req.url}: ${e.message}")
            null
        }
    }

    private companion object {
        const val POLL_MS = 1000L
        val JSON = "application/json".toMediaType()
    }
}
