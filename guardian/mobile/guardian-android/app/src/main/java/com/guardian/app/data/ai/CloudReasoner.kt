package com.guardian.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class AssessRequest(
    val severity: String,
    val confidence: Double,
    val accelPeakG: Double,
    val rotationPeakRadS: Double,
    val speedKmh: Double? = null,
    val transcript: String? = null,
    val hasAudio: Boolean = false,
    val audioSec: Int = 0,
    val imageCount: Int = 0,
)

@Serializable
private data class AssessResponse(
    val headline: String,
    val whatHappened: String,
    val occupantState: String,
    val recommendation: String,
    val model: String,
)

/**
 * Cloud reasoner: the PRIMARY crash-understanding path for now. Posts the crash signals to the
 * backend, which runs a hosted LLM (Azure OpenAI / Claude) when configured. Throws on any failure
 * so the caller can fall back to the on-device model until the local LLM is installed.
 */
class CloudReasoner(private val baseUrl: () -> String) : OnDeviceAi {
    override val modelName = "Guardian-Cloud"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun assess(ctx: CrashContext): AiAssessment = withContext(Dispatchers.IO) {
        val url = baseUrl().trimEnd('/') + "/api/ai/assess"
        val body = json.encodeToString(
            AssessRequest.serializer(),
            AssessRequest(
                ctx.severity.name, ctx.confidence, ctx.accelPeakG, ctx.rotationPeakRadS, ctx.speedKmh, ctx.transcript,
                ctx.hasAudio, ctx.audioSec, ctx.imageCount,
            ),
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("cloud AI HTTP ${resp.code}")
            val payload = resp.body?.string() ?: error("empty cloud AI response")
            val r = json.decodeFromString(AssessResponse.serializer(), payload)
            AiAssessment(r.headline, r.whatHappened, r.occupantState, r.recommendation, r.model, onDevice = false)
        }
    }
}
