package com.guardian.app.data

import android.util.Log
import com.guardian.app.data.local.CrashEventDao
import com.guardian.app.data.local.CrashEventEntity
import com.guardian.app.data.remote.BackendEventStream
import com.guardian.app.data.remote.CrashDto
import com.guardian.app.data.remote.GuardianApi
import com.guardian.app.data.settings.SettingsStore
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Responder
import com.guardian.app.domain.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** A crash the backend received from this phone's vehicle device, with who has been alerted. */
data class LiveCrash(
    val type: String,
    val event: CrashEventRecord,
    val responders: List<Responder> = emptyList(),
    val acknowledgedBy: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GuardianRepository(
    private val device: SwitchableDeviceClient,
    private val dao: CrashEventDao,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    liveFeed: BackendEventStream = BackendEventStream(),
) {
    val history: Flow<List<CrashEventRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    private val _live = MutableSharedFlow<LiveCrash>(extraBufferCapacity = 16)
    val liveCrashes: SharedFlow<LiveCrash> = _live

    private var api: GuardianApi? = null
    private var apiUrl: String? = null

    init {
        scope.launch { device.events.collect { saveDeviceEvent(it) } }
        // Recovery: whenever the phone (re)connects, pull what the device reported on its own.
        scope.launch { device.linkState.collect { if (it == LinkState.CONNECTED) syncFromBackend() } }
        scope.launch {
            settings.settings.map { it.backendUrl }.distinctUntilChanged()
                .flatMapLatest { liveFeed.crashes(it) }
                .collect { msg ->
                    val dto = msg.event ?: return@collect
                    if (dto.deviceId != settings.settings.value.deviceId) return@collect
                    val entity = dto.toEntity()
                    dao.upsert(entity)
                    _live.emit(
                        LiveCrash(
                            msg.type,
                            entity.toRecord(),
                            dto.notifications.map { Responder(it.recipientType, it.recipientName, it.openedAt != null) },
                            dto.acknowledgedBy,
                        ),
                    )
                }
        }
    }

    private fun CrashDto.toEntity() = CrashEventEntity(
        eventId = eventId,
        timestamp = timestamp,
        severity = Severity.parse(severity).name,
        confidence = confidence,
        latitude = latitude,
        longitude = longitude,
        phoneConnected = phoneConnected,
        delivered = true,
        status = status,
        source = "BACKEND",
    )

    private fun api(): GuardianApi {
        val url = settings.settings.value.backendUrl
        if (api == null || apiUrl != url) {
            api = GuardianApi.create(url)
            apiUrl = url
        }
        return api!!
    }

    private suspend fun saveDeviceEvent(e: CrashEventRecord) {
        val existing = dao.get(e.eventId)
        if (existing != null && existing.source == "BACKEND") {
            // Backend status (ACKNOWLEDGED/CANCELLED/...) is authoritative.
            dao.upsert(existing.copy(delivered = existing.delivered || e.delivered))
        } else {
            dao.upsert(CrashEventEntity.from(e))
        }
    }

    /** Returns the number of events synced, or null if the backend is unreachable. */
    suspend fun syncFromBackend(): Int? = try {
        val events = api().crashes(settings.settings.value.deviceId)
        events.forEach { dao.upsert(it.toEntity()) }
        events.size
    } catch (e: Exception) {
        Log.w("GuardianRepo", "backend sync failed: ${e.message}")
        null
    }

    /**
     * I'M SAFE. Cancels verification on the device (if the phone is linked) and the incident
     * on the backend (if it was already transmitted). Returns a user-facing summary.
     */
    suspend fun cancelEmergency(eventId: String?): String {
        val parts = mutableListOf<String>()
        if (device.send(DeviceCommand.Cancel)) parts += "device notified"
        if (eventId != null) {
            try {
                val dto = api().cancel(eventId)
                dao.updateStatus(eventId, dto.status)
                parts += "responders: ${dto.status.lowercase()}"
            } catch (e: HttpException) {
                parts += if (e.code() == 404) "not yet at the Guardian server" else "server error ${e.code()}"
                dao.updateStatus(eventId, "CANCELLED")
            } catch (e: Exception) {
                parts += "Guardian server unreachable"
                dao.updateStatus(eventId, "CANCELLED")
            }
        }
        return if (parts.isEmpty()) "Cancel requested" else parts.joinToString(" · ")
    }

    suspend fun clearHistory() = dao.clear()
}
