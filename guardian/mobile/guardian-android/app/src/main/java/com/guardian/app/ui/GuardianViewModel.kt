package com.guardian.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.data.GuardianRepository
import com.guardian.app.data.LiveCrash
import com.guardian.app.data.SwitchableDeviceClient
import com.guardian.app.data.ai.AiAssessment
import com.guardian.app.data.ai.CrashContext
import com.guardian.app.data.ai.OnDeviceAi
import com.guardian.app.data.ai.WhisperTranscriber
import com.guardian.app.data.alert.AlertDispatch
import com.guardian.app.data.alert.PhoneLocation
import com.guardian.app.data.alert.SmsDispatcher
import com.guardian.app.data.capture.BlackBoxBundle
import com.guardian.app.data.capture.ContinuousBlackBox
import com.guardian.app.data.capture.ConversationCapture
import com.guardian.app.data.capture.DualCameraCapture
import com.guardian.app.data.capture.toTransferJson
import com.guardian.app.data.sensing.AnomalyEvent
import com.guardian.app.data.sensing.AnomalyType
import com.guardian.app.data.sensing.PhoneCrashResult
import com.guardian.app.data.sensing.PhoneSensing
import com.guardian.app.data.sensing.PhoneSensingState
import com.guardian.app.data.settings.AppSettings
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.data.settings.SettingsStore
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashState
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Responder
import com.guardian.app.domain.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import com.guardian.app.data.capture.DualPhotos
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the emergency screen shows. Stays up until the occupant acts, even after the device cools down. */
data class EmergencyAlert(
    val state: CrashState,
    val severity: Severity?,
    val confidence: Double?,
    val verifyRemainingMs: Long?,
    val eventId: String? = null,
    val delivered: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startedAtMs: Long,
    val responders: List<Responder> = emptyList(),
    val helpComingFrom: String? = null,
    val aiAssessment: AiAssessment? = null,
    val blackBox: BlackBoxBundle? = null,
    val dispatches: List<AlertDispatch> = emptyList(),
    val calls: List<com.guardian.app.data.alert.CallEntry> = emptyList(),
    val shareText: String = "",
)

class GuardianViewModel(
    private val repo: GuardianRepository,
    private val device: SwitchableDeviceClient,
    private val settingsStore: SettingsStore,
    private val notifier: AlertNotifier?,
    private val sensing: PhoneSensing,
    private val onDeviceAi: OnDeviceAi,
    private val conversationCapture: ConversationCapture,
    private val dualCameraCapture: DualCameraCapture,
    private val continuousBlackBox: ContinuousBlackBox,
    private val alarmSiren: AlarmSiren?,
    private val smsDispatcher: SmsDispatcher,
    private val callDispatcher: com.guardian.app.data.alert.CallDispatcher,
    private val phoneLocation: PhoneLocation,
    private val whisper: WhisperTranscriber,
    private val mediaServer: com.guardian.app.data.media.MediaServer,
) : ViewModel() {

    val link: StateFlow<LinkState> = device.linkState
    val status: StateFlow<DeviceStatus?> = device.status
    val crash: StateFlow<CrashStatus?> = device.crashStatus
    val settings: StateFlow<AppSettings> = settingsStore.settings
    val phoneSensing: StateFlow<PhoneSensingState> = sensing.state
    val history: StateFlow<List<CrashEventRecord>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Abnormal-driving feed (hard braking, swerves, impacts, crashes), newest first. */
    private val _anomalies = MutableStateFlow<List<AnomalyEvent>>(emptyList())
    val anomalies: StateFlow<List<AnomalyEvent>> = _anomalies.asStateFlow()

    /** Live demo: the real capture pipeline (dual camera + mic) run on demand, not from a crash. */
    private val _demoCapture = MutableStateFlow<BlackBoxBundle?>(null)
    val demoCapture: StateFlow<BlackBoxBundle?> = _demoCapture.asStateFlow()

    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    /** True when the phone's live signals are reaching the Wi-Fi device (last push succeeded). */
    private val _deviceStreaming = MutableStateFlow(false)
    val deviceStreaming: StateFlow<Boolean> = _deviceStreaming.asStateFlow()

    /** Demo: continuously transfer a live black-box snapshot to the device to show the pipeline live. */
    private val _liveTransfer = MutableStateFlow(false)
    val liveTransfer: StateFlow<Boolean> = _liveTransfer.asStateFlow()

    private val _alert = MutableStateFlow<EmergencyAlert?>(null)
    val alert: StateFlow<EmergencyAlert?> = _alert.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { device.crashStatus.collect { it?.let(::onCrashStatus) } }
        viewModelScope.launch { device.events.collect(::onDeviceEvent) }
        viewModelScope.launch { repo.liveCrashes.collect(::onLiveCrash) }
        viewModelScope.launch { sensing.crashes.collect(::onPhoneCrash) }
        viewModelScope.launch { sensing.anomalies.collect(::addAnomaly) }
        // Stream the phone's live signals to the Wi-Fi device ~1/s so it collects phone data
        // alongside its own and can show both on its web UI. No-op for non-Wi-Fi sources.
        viewModelScope.launch {
            while (true) {
                val st = sensing.state.value
                val emergency = _alert.value != null
                val json = """{"accelG":${"%.2f".format(st.accelG)},""" +
                    """"rotationRadS":${"%.2f".format(st.rotationRadS)},""" +
                    """"detection":${st.active},"state":"${if (emergency) "EMERGENCY" else "NORMAL"}"}"""
                _deviceStreaming.value = runCatching { device.pushPhoneSignals(json) }.getOrDefault(false)
                delay(1000)
            }
        }
        // Live black-box transfer demo: push an evolving snapshot to the device every 3s so the
        // dashboard shows the black box filling + transferring in real time.
        viewModelScope.launch {
            while (true) {
                if (_liveTransfer.value && !crashStored) runCatching { sendBox { liveBlackBoxJson(it) } }
                delay(3000)
            }
        }
        // Continuous capture checkpoint: every 10 minutes the latest audio + photos go to the black box.
        viewModelScope.launch {
            delay(15_000)
            while (true) {
                if (!crashStored) {
                    val seg = conversationCapture.latestSegment()
                    mediaServer.preAudioPath = seg?.path
                    checkpointAudioSec = seg?.durationSec ?: 0
                    checkpointAtMs = System.currentTimeMillis()
                    runCatching { sendBox { liveBlackBoxJson(it) } }
                }
                delay(10 * 60_000L)
            }
        }
        if (device.source != DeviceSource.BLE) device.connect()
    }

    @Volatile private var thumbsOk = true
    @Volatile private var checkpointAtMs = 0L
    @Volatile private var checkpointAudioSec = 0
    // Once a crash record is on the device, live/checkpoint snapshots must not overwrite it.
    @Volatile private var crashStored = false

    /** Sends with last-frame thumbnails; if only the thumbnail-free body is accepted, stops sending them. */
    private suspend fun sendBox(build: (withThumbs: Boolean) -> String): Boolean {
        if (thumbsOk && device.sendBlackBox(build(true))) return true
        val ok = device.sendBlackBox(build(false))
        if (ok) thumbsOk = false
        return ok
    }

    private fun liveBlackBoxJson(withThumbs: Boolean = true): String {
        val st = sensing.state.value
        val photos = continuousBlackBox.latest()
        mediaServer.cabinPath = photos.cabinPath
        mediaServer.roadPath = photos.roadPath
        val images = listOfNotNull(photos.cabinPath, photos.roadPath).size
        val frames = continuousBlackBox.capturedCount
        val loc = phoneLocation.lastKnown()
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val thumbs = if (withThumbs) {
            """"thumbCabin":"${com.guardian.app.data.media.ThumbnailEncoder.encode(photos.cabinPath)}",""" +
                """"thumbRoad":"${com.guardian.app.data.media.ThumbnailEncoder.encode(photos.roadPath)}","""
        } else ""
        return """{"eventId":"LIVE","severity":"MONITORING","confidence":1.0,""" + thumbs +
            """"sensorLog":"live ${"%.1f".format(st.accelG)} g · ${"%.1f".format(st.rotationRadS)} rad/s",""" +
            """"audioSec":0,"preAudioSec":$checkpointAudioSec,"checkpointAt":$checkpointAtMs,"images":$images,"frames":$frames,"media":"${mediaServer.baseUrl() ?: ""}","location":"${loc ?: ""}","smsSent":0,""" +
            """"assessment":"Live black box streaming · $frames frames buffered · updated $ts · ready to transfer on impact."}"""
    }

    fun setLiveTransfer(on: Boolean) {
        _liveTransfer.value = on
        _message.value = if (on) "Live black box transfer started" else "Live black box transfer stopped"
    }

    private fun addAnomaly(a: AnomalyEvent) {
        _anomalies.value = (listOf(a) + _anomalies.value).take(50)
        // Warn the driver on abnormal driving (a crash raises the full emergency instead).
        if (a.type != AnomalyType.CRASH) {
            _message.value = "⚠ ${a.type.label} · ${a.detail}"
            if (settings.value.notifications) notifier?.warn("Abnormal driving", "${a.type.label} · ${a.detail}")
        }
    }

    /** Runs the real dual-camera + microphone capture now so the pipeline can be demoed live. */
    fun runCaptureDemo() = viewModelScope.launch {
        if (_capturing.value) return@launch
        _capturing.value = true
        _message.value = "Opening cameras…"
        val photos = dualCameraCapture.capture()
        // Show the photos right away, then fill in the audio once recorded.
        _demoCapture.value = BlackBoxBundle(
            capturedAtMs = System.currentTimeMillis(),
            audioPath = null,
            audioDurationSec = 0,
            cabinImagePath = photos.cabinPath,
            roadImagePath = photos.roadPath,
            sensorLog = "capture test",
            transcript = null,
            assessment = null,
        )
        _message.value = "Recording voice (before + after)…"
        val audio = conversationCapture.captureAroundCrash(postSeconds = 5)
        _demoCapture.value = _demoCapture.value?.copy(
            audioPath = audio.postPath,
            audioDurationSec = audio.postDurationSec,
            preAudioPath = audio.prePath,
            preAudioDurationSec = audio.preDurationSec,
        )
        _capturing.value = false
        conversationCapture.startPreBuffer(viewModelScope) // resume the rolling pre-impact buffer
        val n = listOfNotNull(photos.cabinPath, photos.roadPath).size
        val secs = audio.preDurationSec + audio.postDurationSec
        _message.value = "Captured $n photo(s)" + (if (secs > 0) " + ${secs}s audio (before + after)" else "")
    }

    fun clearDemoCapture() {
        _demoCapture.value = null
    }

    /** Manually push a black box to the connected device over Wi-Fi, to test the transfer path. */
    fun sendBlackBoxToDevice() = viewModelScope.launch {
        val bb = _demoCapture.value
        val json = bb?.toTransferJson("SNAP-${System.currentTimeMillis()}", "SNAPSHOT", 1.0, phoneLocation.lastKnown(), 0)
            ?: liveBlackBoxJson()
        _message.value = if (device.sendBlackBox(json)) {
            "Black box snapshot sent to device ✓"
        } else {
            "Device didn't accept it — pick the Wi-Fi device + check the IP"
        }
    }

    /** Backend view of this vehicle's crashes: who was alerted and how far help has got. */
    private fun onLiveCrash(c: LiveCrash) {
        val e = c.event
        val current = _alert.value
        val closed = e.status in setOf("CANCELLED", "RESOLVED")
        when {
            current != null && current.eventId == e.eventId && closed -> {
                _alert.value = null
                alarmSiren?.stop()
                callDispatcher.stop()
                _message.value = if (e.status == "RESOLVED") "Incident closed" else "Incident cancelled - responders stood down"
            }
            current != null && (current.eventId == e.eventId || (current.eventId == null && c.type == "CRASH_DETECTED")) ->
                _alert.value = current.copy(
                    eventId = e.eventId,
                    delivered = true,
                    latitude = e.latitude ?: current.latitude,
                    longitude = e.longitude ?: current.longitude,
                    responders = c.responders,
                    helpComingFrom = c.acknowledgedBy,
                )
            c.type == "CRASH_DETECTED" && e.phoneConnected && e.status == "EMERGENCY_ACTIVE" -> {
                // Phone was linked at crash time: same alert the device's BLE notification would raise.
                _alert.value = EmergencyAlert(
                    state = CrashState.EMERGENCY_ACTIVE,
                    severity = e.severity,
                    confidence = e.confidence,
                    verifyRemainingMs = null,
                    eventId = e.eventId,
                    delivered = true,
                    latitude = e.latitude,
                    longitude = e.longitude,
                    startedAtMs = System.currentTimeMillis(),
                    responders = c.responders,
                )
                if (settings.value.notifications) notifier?.crash("COLLISION DETECTED", "Police, ambulance & family alerted")
                alarmSiren?.start()
            }
            c.type == "CRASH_DETECTED" ->
                _message.value = "A collision was reported by your vehicle device while this phone was disconnected"
        }
    }

    private fun onCrashStatus(c: CrashStatus) {
        val current = _alert.value
        if (c.state.isAlert) {
            val startedNew = current == null
            _alert.value = (current ?: EmergencyAlert(c.state, c.severity, c.confidence, null, startedAtMs = System.currentTimeMillis()))
                .copy(state = c.state, severity = c.severity ?: current?.severity, confidence = c.confidence ?: current?.confidence,
                    verifyRemainingMs = c.verifyRemainingMs)
            if (startedNew && settings.value.notifications) {
                notifier?.crash("COLLISION DETECTED", "Tap to respond")
            }
            if (startedNew) alarmSiren?.start()
            if (c.state == CrashState.EMERGENCY_ACTIVE) startCalls()
        } else if (current != null) {
            _alert.value = current.copy(state = c.state)
        }
    }

    private fun onDeviceEvent(e: CrashEventRecord) {
        val a = _alert.value ?: return
        // Attach the event created for the active alert (ignore history replays of older events).
        if (a.eventId == null || a.eventId == e.eventId) {
            _alert.value = a.copy(eventId = e.eventId, delivered = e.delivered, latitude = e.latitude, longitude = e.longitude)
        }
    }

    /** The phone's own sensors detected a crash. Mobile is primary. */
    private fun onPhoneCrash(r: PhoneCrashResult) {
        // A dropped phone or a small jolt is NOT an accident — record it as abnormal activity
        // (a warning), never the full emergency with siren.
        if (r.dropped || r.severity == Severity.MINOR) {
            addAnomaly(
                AnomalyEvent(
                    AnomalyType.IMPACT,
                    if (r.dropped) "Phone dropped · ${"%.1f".format(r.accelPeakG)} g (free-fall)"
                    else "Minor impact · ${"%.1f".format(r.accelPeakG)} g",
                    r.accelPeakG,
                    System.currentTimeMillis(),
                    critical = false,
                ),
            )
            return
        }
        _alert.value = EmergencyAlert(
            state = CrashState.EMERGENCY_ACTIVE,
            severity = r.severity,
            confidence = r.confidence,
            verifyRemainingMs = null,
            startedAtMs = System.currentTimeMillis(),
        )
        alarmSiren?.start()
        startCalls()
        addAnomaly(
            AnomalyEvent(
                AnomalyType.CRASH,
                "Collision detected · ${(r.confidence * 100).toInt()}%",
                r.accelPeakG,
                System.currentTimeMillis(),
                critical = r.severity == Severity.CRITICAL,
            ),
        )
        if (settings.value.notifications) {
            notifier?.crash("COLLISION DETECTED", "Impact detected on your phone")
        }
        // Real dispatch: text the occupant's emergency contacts over cellular SMS (no internet needed).
        viewModelScope.launch {
            val s = settings.value
            val dispatches = smsDispatcher.dispatch(
                r.severity,
                r.confidence,
                phoneLocation.lastKnown(),
                "${s.vehicleName} (${s.vehicleRegistration})",
                s.contacts,
            )
            _alert.value = _alert.value?.copy(dispatches = dispatches)
            val sent = dispatches.count { it.sent }
            if (sent > 0) _message.value = "Alert SMS sent to $sent contact(s)"
        }
        // Mobile is primary: capture the scene first, then let the AI reason over what we actually have.
        viewModelScope.launch {
            // Use the frames the black box already buffered (every ~10s) so a crash has images
            // instantly; only fall back to a live capture if the buffer is still warming up.
            var photos = continuousBlackBox.latest()
            if (photos.cabinPath == null && photos.roadPath == null) photos = dualCameraCapture.capture()
            val imageCount = listOfNotNull(photos.cabinPath, photos.roadPath).size
            // Show the captured photos immediately, before the (longer) audio clip finishes recording.
            val photoOnly = BlackBoxBundle(
                capturedAtMs = System.currentTimeMillis(),
                audioPath = null,
                audioDurationSec = 0,
                cabinImagePath = photos.cabinPath,
                roadImagePath = photos.roadPath,
                sensorLog = "peak ${"%.1f".format(r.accelPeakG)} g · rot ${"%.1f".format(r.rotationPeakRadS)} rad/s",
                transcript = null,
                assessment = null,
            )
            _alert.value = _alert.value?.copy(blackBox = photoOnly)
            // Then a proper burst from both cameras, filled in as soon as it finishes.
            val burst = async { dualCameraCapture.captureBurst(CRASH_SHOTS) }
            launch {
                val b = burst.await()
                _alert.value = _alert.value?.copy(blackBox = (_alert.value?.blackBox ?: photoOnly).withShots(b))
            }
            val assessment = onDeviceAi.assess(
                CrashContext(
                    severity = r.severity,
                    confidence = r.confidence,
                    accelPeakG = r.accelPeakG,
                    rotationPeakRadS = r.rotationPeakRadS,
                    hasAudio = true,
                    audioSec = conversationCapture.expectedCrashAudioSec,
                    imageCount = imageCount,
                ),
            )
            _alert.value = _alert.value?.copy(
                aiAssessment = assessment,
                blackBox = _alert.value?.blackBox?.copy(assessment = assessment),
            )
            // Before + after in-cabin audio (the pre-buffer already holds the seconds before impact).
            val audio = conversationCapture.captureAroundCrash()
            // On-device transcription of the in-cabin audio (whisper.cpp; null until the model ships).
            val transcript = whisper.transcribe(audio.postPath)
            val bundle = photoOnly.withShots(burst.await()).copy(
                audioPath = audio.postPath,
                audioDurationSec = audio.postDurationSec,
                preAudioPath = audio.prePath,
                preAudioDurationSec = audio.preDurationSec,
                transcript = transcript,
                assessment = assessment,
            )
            _alert.value = _alert.value?.copy(blackBox = bundle)
            // Hand the black box to the vehicle device over BLE/Wi-Fi so it survives phone loss.
            // Mint a local id if the phone never got a backend/device one, so it's never empty.
            val eventId = _alert.value?.eventId
                ?: "PH-${settingsStore.settings.value.deviceId}-${System.currentTimeMillis()}"
            val smsSent = _alert.value?.dispatches?.count { it.sent } ?: 0
            mediaServer.cabinPath = bundle.cabinImagePath
            mediaServer.roadPath = bundle.roadImagePath
            mediaServer.afterAudioPath = bundle.audioPath
            mediaServer.preAudioPath = bundle.preAudioPath
            val mediaBase = mediaServer.baseUrl()
            val frames = continuousBlackBox.capturedCount
            val ok = sendBox { thumbs ->
                bundle.toTransferJson(eventId, r.severity.name, r.confidence, phoneLocation.lastKnown(), smsSent, frames, mediaBase, thumbs)
            }
            if (ok) {
                crashStored = true
                _alert.value = _alert.value?.copy(blackBox = bundle.copy(syncedToDevice = true))
            }
        }
    }

    fun simulatePhoneCrash() = sensing.simulateCrash()

    private fun BlackBoxBundle.withShots(b: DualPhotos) = if (b.cabinAll.isEmpty() && b.roadAll.isEmpty()) this else copy(
        cabinImagePath = b.cabinPath ?: cabinImagePath,
        roadImagePath = b.roadPath ?: roadImagePath,
        cabinImages = b.cabinAll,
        roadImages = b.roadAll,
    )

    /** Dials the emergency contacts over the SIM, one after another, once per alert. */
    private fun startCalls() {
        val a = _alert.value ?: return
        if (a.calls.isNotEmpty()) return
        val s = settings.value
        val where = phoneLocation.lastKnown()?.let { "https://maps.google.com/?q=$it" } ?: "location unavailable"
        _alert.value = a.copy(shareText = "GUARDIAN alert: collision detected on ${s.vehicleName} (${s.vehicleRegistration}). Location: $where")
        callDispatcher.start(s.contacts) { list -> _alert.value = _alert.value?.copy(calls = list) }
    }

    private companion object {
        const val CRASH_SHOTS = 5
    }

    fun imSafe() = viewModelScope.launch {
        alarmSiren?.stop()
        callDispatcher.stop()
        crashStored = false
        val result = repo.cancelEmergency(_alert.value?.eventId)
        _message.value = "I'M SAFE: $result"
        _alert.value = null
    }

    fun dismissAlert() {
        alarmSiren?.stop()
        _alert.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---- device link ----
    fun connect() = device.connect()
    fun forcePhoneDisconnected() {
        device.disconnect(userInitiated = true)
        _message.value = "Phone link dropped. The device still reports crashes over its own cellular link."
    }

    fun syncHistory() = viewModelScope.launch {
        val n = repo.syncFromBackend()
        _message.value = if (n == null) "Backend unreachable (${settings.value.backendUrl})" else "Synced $n events"
    }

    // ---- developer / demo controls ----
    fun simulate(scenario: String) = command(DeviceCommand.Simulate(scenario), "Simulating $scenario")
    fun cellularOff() = command(DeviceCommand.CellularOff, "Device cellular forced OFF")
    fun cellularOn() = command(DeviceCommand.CellularOn, "Device cellular restored")

    private fun command(c: DeviceCommand, ok: String) = viewModelScope.launch {
        _message.value = if (device.send(c)) ok else "Device not connected"
    }

    // ---- settings ----
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        val source = settingsStore.settings.value.deviceSource
        if (source != device.source) {
            device.select(source)
            if (source != DeviceSource.BLE) device.connect()
        }
    }

    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }
}
