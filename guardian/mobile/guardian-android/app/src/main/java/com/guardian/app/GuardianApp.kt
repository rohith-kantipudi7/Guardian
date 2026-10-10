package com.guardian.app

import android.app.Application
import com.guardian.app.data.GuardianRepository
import com.guardian.app.data.NoDeviceClient
import com.guardian.app.data.SwitchableDeviceClient
import com.guardian.app.data.ai.CloudReasoner
import com.guardian.app.data.ai.CompositeReasoner
import com.guardian.app.data.ai.EdgeReasoner
import com.guardian.app.data.ai.LlmModelManager
import com.guardian.app.data.ai.OnDeviceAi
import com.guardian.app.data.ai.WhisperCppTranscriber
import com.guardian.app.data.ai.WhisperTranscriber
import com.guardian.app.data.media.MediaServer
import com.guardian.app.data.alert.PhoneLocation
import com.guardian.app.data.alert.SmsDispatcher
import com.guardian.app.data.ble.BleGuardianDeviceClient
import com.guardian.app.data.capture.ConversationCapture
import com.guardian.app.data.capture.ContinuousBlackBox
import com.guardian.app.data.capture.DualCameraCapture
import com.guardian.app.data.cloud.CloudDeviceClient
import com.guardian.app.data.esp32.Esp32DeviceClient
import com.guardian.app.data.local.GuardianDatabase
import com.guardian.app.data.mock.MockGuardianDeviceClient
import com.guardian.app.data.sensing.PhoneSensing
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.data.settings.SettingsStore
import com.guardian.app.data.virtual.VirtualDeviceClient
import com.guardian.app.ui.AlarmSiren
import com.guardian.app.ui.AlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI container: one instance per process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    private val bleClient = BleGuardianDeviceClient(app, scope)
    val device = SwitchableDeviceClient(
        backup = bleClient,
        clients = mapOf(
            DeviceSource.NONE to NoDeviceClient(),
            DeviceSource.ESP32 to Esp32DeviceClient(
                scope,
                baseUrl = { "http://" + settings.settings.value.deviceIp.trim() },
                deviceId = { settings.settings.value.deviceId },
            ),
            DeviceSource.VIRTUAL to VirtualDeviceClient(scope, baseUrl = { settings.settings.value.virtualDeviceUrl }),
            DeviceSource.CLOUD to CloudDeviceClient(
                scope,
                baseUrl = { settings.settings.value.backendUrl },
                deviceId = { settings.settings.value.deviceId },
            ),
            DeviceSource.BLE to bleClient,
            DeviceSource.MOCK to MockGuardianDeviceClient(scope),
        ),
        initial = settings.settings.value.deviceSource,
        scope = scope,
    )
    private val db = GuardianDatabase.create(app)
    val repository = GuardianRepository(device, db.crashEvents(), settings, scope)
    val notifier = AlertNotifier(app)
    val alarmSiren = AlarmSiren(app)
    val smsDispatcher = SmsDispatcher(app)
    val phoneLocation = PhoneLocation(app)
    // Mobile is primary: the phone's own IMU runs crash detection.
    val phoneSensing = PhoneSensing(app).also { it.start() }
    // Cloud AI is the primary reasoner for now; the on-device model is the offline fallback
    // (and becomes primary once the local LLM is installed via LlmModelManager).
    val onDeviceAi: OnDeviceAi = CompositeReasoner(
        primary = CloudReasoner(baseUrl = { settings.settings.value.backendUrl }),
        fallback = EdgeReasoner(),
    )
    val modelManager = LlmModelManager(app)
    // On-device transcription of the in-cabin audio black box (whisper.cpp + ggml tiny.en).
    val whisper: WhisperTranscriber = WhisperCppTranscriber(app)
    val conversationCapture = ConversationCapture(app).also { it.startPreBuffer(scope) }
    val dualCameraCapture = DualCameraCapture(app)
    // Always-on vehicle black box: buffers recent cabin/road frames so a crash has images instantly.
    val continuousBlackBox = ContinuousBlackBox(app, dualCameraCapture).also { it.start(scope) }
    // Serves the REAL captured photos/audio to the dashboard (the ESP32 can't store media).
    val mediaServer = MediaServer().also { it.start(scope) }
}

class GuardianApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
