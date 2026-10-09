package com.guardian.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.data.GuardianRepository
import com.guardian.app.data.LiveCrash
import com.guardian.app.data.SwitchableDeviceClient
import com.guardian.app.data.ai.AiAssessment
import com.guardian.app.data.ai.CrashContext
import com.guardian.app.data.ai.OnDeviceAi
import com.guardian.app.data.capture.BlackBoxBundle
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
    private val alarmSiren: AlarmSiren?,
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
        if (device.source != DeviceSource.BLE) device.connect()
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
        _message.value = "Capturing cabin + road photos and voice…"
        val photos = dualCameraCapture.capture()
        val clip = conversationCapture.capture()
        _demoCapture.value = BlackBoxBundle(
            capturedAtMs = System.currentTimeMillis(),
            audioPath = clip?.path,
            audioDurationSec = clip?.durationSec ?: 0,
            cabinImagePath = photos.cabinPath,
            roadImagePath = photos.roadPath,
            sensorLog = "capture test",
            transcript = null,
            assessment = null,
        )
        _capturing.value = false
        val n = listOfNotNull(photos.cabinPath, photos.roadPath).size
        _message.value = "Captured $n photo(s)" + (clip?.let { " + ${it.durationSec}s audio" } ?: "")
    }

    fun clearDemoCapture() {
        _demoCapture.value = null
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
                if (settings.value.notifications) notifier?.crash("CRASH DETECTED", "${e.severity} - police, ambulance & family alerted")
                alarmSiren?.start()
            }
            c.type == "CRASH_DETECTED" ->
                _message.value = "${e.severity} crash was reported by your vehicle device while this phone was disconnected"
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
                notifier?.crash("CRASH DETECTED", "${c.severity ?: ""} - tap to respond")
            }
            if (startedNew) alarmSiren?.start()
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
        _alert.value = EmergencyAlert(
            state = CrashState.EMERGENCY_ACTIVE,
            severity = r.severity,
            confidence = r.confidence,
            verifyRemainingMs = null,
            startedAtMs = System.currentTimeMillis(),
        )
        alarmSiren?.start()
        addAnomaly(
            AnomalyEvent(
                AnomalyType.CRASH,
                "${r.severity.name} · ${(r.confidence * 100).toInt()}%",
                r.accelPeakG,
                System.currentTimeMillis(),
                critical = r.severity == Severity.CRITICAL,
            ),
        )
        if (settings.value.notifications) {
            notifier?.crash("CRASH DETECTED", "${r.severity} impact detected on your phone")
        }
        // Mobile is primary: run the on-device AI, then capture the in-cabin conversation for the black box.
        viewModelScope.launch {
            val assessment = onDeviceAi.assess(
                CrashContext(r.severity, r.confidence, r.accelPeakG, r.rotationPeakRadS),
            )
            _alert.value = _alert.value?.copy(aiAssessment = assessment)
            val photos = dualCameraCapture.capture()
            val clip = conversationCapture.capture()
            val bundle = BlackBoxBundle(
                capturedAtMs = System.currentTimeMillis(),
                audioPath = clip?.path,
                audioDurationSec = clip?.durationSec ?: 0,
                cabinImagePath = photos.cabinPath,
                roadImagePath = photos.roadPath,
                sensorLog = "peak ${"%.1f".format(r.accelPeakG)} g · rot ${"%.1f".format(r.rotationPeakRadS)} rad/s",
                transcript = null,
                assessment = assessment,
            )
            _alert.value = _alert.value?.copy(blackBox = bundle)
            // Hand the black box to the vehicle device over BLE so it survives phone loss.
            val json = bundle.toTransferJson(_alert.value?.eventId, r.severity.name, r.confidence)
            if (device.sendBlackBox(json)) {
                _alert.value = _alert.value?.copy(blackBox = bundle.copy(syncedToDevice = true))
            }
        }
    }

    fun simulatePhoneCrash() = sensing.simulateCrash()

    fun imSafe() = viewModelScope.launch {
        alarmSiren?.stop()
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
