package com.guardian.app.data.esp32

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Talks to the real XIAO ESP32-C3 collision device over Wi-Fi (the phone hosts the hotspot, so it
 * can reach the board directly). Polls `GET /data` for live motion + its own collision events, and
 * streams the phone's black box to `POST /blackbox` so the record survives phone loss. No Bluetooth.
 *
 * Expected `/data` shape (tolerant to missing fields):
 *   {"accel":1.0,"gyro":3.2,"count":2,"uptime":12345,
 *    "events":[{"count":2,"accel":9.1,"jerk":5.0,"gyro":420,"uptime":12000}, ...]}
 */
class Esp32DeviceClient(
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

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private var poller: Job? = null
    private var lastCount = -1

    private fun base() = baseUrl().trim().trimEnd('/')

    override fun connect() {
        if (_link.value == LinkState.DISCONNECTED) _link.value = LinkState.CONNECTING
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                poll()
                delay(1500)
            }
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        poller?.cancel()
        poller = null
        _link.value = LinkState.DISCONNECTED
        _status.value = null
        lastCount = -1
    }

    private suspend fun poll() = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${base()}/data").get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful || body == null) {
                    markOffline()
                    return@withContext
                }
                val json = JSONObject(body)
                val count = json.optInt("count", 0)
                val accel = json.optDouble("accel", 1.0)
                val gyro = json.optDouble("gyro", 0.0)
                Log.i("Esp32", "GET ${base()}/data -> 200 count=$count accel=$accel")
                _link.value = LinkState.CONNECTED
                _status.value = DeviceStatus(
                    deviceId = deviceId(),
                    vehicleId = "",
                    firmware = "esp32-c3",
                    battery = null,
                    gps = "UNAVAILABLE",
                    cellular = "CONNECTED", // reachable over Wi-Fi
                    vehicleConnected = true,
                    crashState = _crash.value?.state ?: CrashState.NORMAL,
                    pendingEvents = 0,
                    lastUpdateMs = System.currentTimeMillis(),
                )
                // A new collision counted by the device = a backup crash it detected itself.
                if (lastCount in 0 until count) onDeviceCollision(count, latestEvent(json, accel, gyro))
                lastCount = count
            }
        } catch (e: Exception) {
            Log.w("Esp32", "GET ${base()}/data FAILED: ${e.message}")
            markOffline()
        }
    }

    private fun latestEvent(json: JSONObject, liveAccel: Double, liveGyro: Double): Pair<Double, Double> {
        val events = json.optJSONArray("events")
        val first = if (events != null && events.length() > 0) events.optJSONObject(0) else null
        val accel = first?.optDouble("accel", liveAccel) ?: liveAccel
        val gyro = first?.optDouble("gyro", liveGyro) ?: liveGyro
        return accel to gyro
    }

    private suspend fun onDeviceCollision(count: Int, motion: Pair<Double, Double>) {
        val (accelG, gyroDps) = motion
        val severity = when {
            accelG >= 6.0 || gyroDps >= 400.0 -> Severity.CRITICAL
            accelG >= 3.5 -> Severity.MODERATE
            else -> Severity.MINOR
        }
        _crash.value = CrashStatus(CrashState.EMERGENCY_ACTIVE, severity, confidence = 0.9)
        _events.emit(
            CrashEventRecord(
                eventId = "ESP32-${deviceId()}-$count",
                timestamp = Instant.now().toString(),
                severity = severity,
                confidence = 0.9,
                latitude = null,
                longitude = null,
                phoneConnected = true,
                delivered = true,
                status = "EMERGENCY_ACTIVE",
                source = "DEVICE",
            ),
        )
    }

    private fun markOffline() {
        _link.value = LinkState.DISCONNECTED
        _status.value = null
    }

    override suspend fun send(command: DeviceCommand): Boolean = false

    /** Streams the compact black-box bundle to the device over Wi-Fi. */
    override suspend fun sendBlackBox(json: String): Boolean = post("/blackbox", json)

    /** Streams the phone's live signals to the device so it can show them on its web UI. */
    override suspend fun pushPhoneSignals(json: String): Boolean = post("/phone", json)

    private suspend fun post(path: String, json: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${base()}$path"
        try {
            val req = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use {
                Log.i("Esp32", "POST $url -> ${it.code}")
                it.isSuccessful
            }
        } catch (e: Exception) {
            Log.w("Esp32", "POST $url FAILED: ${e.message}")
            false
        }
    }
}
