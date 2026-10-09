package com.guardian.app

import android.app.Application
import com.guardian.app.data.GuardianRepository
import com.guardian.app.data.SwitchableDeviceClient
import com.guardian.app.data.ble.BleGuardianDeviceClient
import com.guardian.app.data.local.GuardianDatabase
import com.guardian.app.data.mock.MockGuardianDeviceClient
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.data.settings.SettingsStore
import com.guardian.app.data.virtual.VirtualDeviceClient
import com.guardian.app.ui.AlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI container: one instance per process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val device = SwitchableDeviceClient(
        clients = mapOf(
            DeviceSource.VIRTUAL to VirtualDeviceClient(scope, baseUrl = { settings.settings.value.virtualDeviceUrl }),
            DeviceSource.BLE to BleGuardianDeviceClient(app, scope),
            DeviceSource.MOCK to MockGuardianDeviceClient(scope),
        ),
        initial = settings.settings.value.deviceSource,
        scope = scope,
    )
    private val db = GuardianDatabase.create(app)
    val repository = GuardianRepository(device, db.crashEvents(), settings, scope)
    val notifier = AlertNotifier(app)
}

class GuardianApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
