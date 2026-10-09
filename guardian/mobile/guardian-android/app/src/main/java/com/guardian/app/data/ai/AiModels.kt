package com.guardian.app.data.ai

import com.guardian.app.domain.Severity

/** Everything the on-device AI reasons over for one crash. */
data class CrashContext(
    val severity: Severity,
    val confidence: Double,
    val accelPeakG: Double,
    val rotationPeakRadS: Double,
    val speedKmh: Double? = null,
    val transcript: String? = null,
    val hasAudio: Boolean = false,
    val audioSec: Int = 0,
    val imageCount: Int = 0,
)

/** Structured, post-accident assessment produced on the phone. */
data class AiAssessment(
    val headline: String,
    val whatHappened: String,
    val occupantState: String,
    val recommendation: String,
    val model: String,
    val onDevice: Boolean = true,
)
