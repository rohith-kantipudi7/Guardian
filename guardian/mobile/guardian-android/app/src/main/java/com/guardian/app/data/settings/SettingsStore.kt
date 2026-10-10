package com.guardian.app.data.settings

import android.content.Context
import com.guardian.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EmergencyContact(val name: String, val phone: String, val relationship: String = "")

/** Where the app's "vehicle device" comes from. */
enum class DeviceSource(val label: String, val detail: String) {
    NONE("No device connected", "Phone is primary; connect your Guardian IoT device as backup"),
    ESP32("Guardian device (Wi-Fi)", "Your ESP32 collision device over Wi-Fi / hotspot"),
    VIRTUAL("Simulator (demo)", "Laptop simulator standing in for the vehicle device"),
    CLOUD("Guardian device (cloud)", "Device status read through the Guardian backend"),
    BLE("Real Guardian device (Bluetooth)", "ESP32 in the vehicle, over Bluetooth Low Energy"),
    MOCK("Offline mock", "Built into the app; nothing reaches the backend"),
}

private fun defaultVirtualUrl(backend: String) = backend.replace(Regex(":\\d+/?$"), ":8100")

data class AppSettings(
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val virtualDeviceUrl: String = defaultVirtualUrl(BuildConfig.DEFAULT_BACKEND_URL),
    val deviceId: String = "GD-001",
    val deviceIp: String = "192.168.137.25",
    val vehicleName: String = "My Vehicle",
    val vehicleRegistration: String = "KA01GD0001",
    val deviceSource: DeviceSource = DeviceSource.NONE,
    val demoMode: Boolean = true,
    val notifications: Boolean = true,
    val contacts: List<EmergencyContact> = emptyList(),
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("guardian_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            backendUrl = prefs.getString("backendUrl", d.backendUrl) ?: d.backendUrl,
            virtualDeviceUrl = prefs.getString("virtualDeviceUrl", d.virtualDeviceUrl) ?: d.virtualDeviceUrl,
            deviceId = prefs.getString("deviceId", d.deviceId) ?: d.deviceId,
            deviceIp = prefs.getString("deviceIp", d.deviceIp) ?: d.deviceIp,
            vehicleName = prefs.getString("vehicleName", d.vehicleName) ?: d.vehicleName,
            vehicleRegistration = prefs.getString("vehicleRegistration", d.vehicleRegistration) ?: d.vehicleRegistration,
            deviceSource = prefs.getString("deviceSource", null)
                ?.let { v -> DeviceSource.entries.firstOrNull { it.name == v } } ?: d.deviceSource,
            demoMode = prefs.getBoolean("demoMode", d.demoMode),
            notifications = prefs.getBoolean("notifications", d.notifications),
            contacts = prefs.getString("contacts", null)
                ?.let { runCatching { json.decodeFromString<List<EmergencyContact>>(it) }.getOrNull() }
                ?: emptyList(),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("backendUrl", s.backendUrl.trim())
            .putString("virtualDeviceUrl", s.virtualDeviceUrl.trim())
            .putString("deviceId", s.deviceId.trim())
            .putString("deviceIp", s.deviceIp.trim())
            .putString("vehicleName", s.vehicleName)
            .putString("vehicleRegistration", s.vehicleRegistration)
            .putString("deviceSource", s.deviceSource.name)
            .putBoolean("demoMode", s.demoMode)
            .putBoolean("notifications", s.notifications)
            .putString("contacts", json.encodeToString(s.contacts))
            .apply()
        _settings.value = s
    }
}
