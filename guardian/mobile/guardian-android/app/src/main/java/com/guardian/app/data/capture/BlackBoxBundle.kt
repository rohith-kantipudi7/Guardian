package com.guardian.app.data.capture

import com.guardian.app.data.ai.AiAssessment

/** The post-accident record the phone assembles and hands to the IoT device's black box. */
data class BlackBoxBundle(
    val capturedAtMs: Long,
    val audioPath: String?,
    val audioDurationSec: Int,
    val sensorLog: String,
    val transcript: String?,
    val assessment: AiAssessment?,
)
