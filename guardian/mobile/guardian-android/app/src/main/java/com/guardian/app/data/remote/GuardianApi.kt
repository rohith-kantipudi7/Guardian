package com.guardian.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class CrashDto(
    val eventId: String,
    val deviceId: String,
    val vehicleId: String? = null,
    val timestamp: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val confidence: Double,
    val severity: String,
    val phoneConnected: Boolean,
    val status: String,
    val acknowledgedBy: String? = null,
    val notifications: List<NotificationDto> = emptyList(),
)

@Serializable
data class NotificationDto(
    val recipientType: String,
    val recipientName: String,
    val openedAt: String? = null,
)

@Serializable
data class DeviceDto(
    val id: String,
    val online: Boolean = false,
    val phoneConnected: Boolean = false,
    val cellularStatus: String? = null,
    val lastHeartbeat: String? = null,
)

/** Subset of the backend contract the phone needs (docs/api.md). */
interface GuardianApi {
    @GET("api/crashes")
    suspend fun crashes(@Query("deviceId") deviceId: String?, @Query("limit") limit: Int = 100): List<CrashDto>

    @POST("api/crashes/{eventId}/cancel")
    suspend fun cancel(@Path("eventId") eventId: String): CrashDto

    @GET("api/devices/{deviceId}")
    suspend fun device(@Path("deviceId") deviceId: String): DeviceDto

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String): GuardianApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl.trimEnd('/') + "/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GuardianApi::class.java)
        }
    }
}
