package com.guardian.app.data.capture

import com.guardian.app.data.ai.AiAssessment

/** The post-accident record the phone assembles and hands to the IoT device's black box. */
data class BlackBoxBundle(
    val capturedAtMs: Long,
    val audioPath: String?,
    val audioDurationSec: Int,
    val preAudioPath: String? = null,
    val preAudioDurationSec: Int = 0,
    val cabinImagePath: String? = null,
    val roadImagePath: String? = null,
    val sensorLog: String,
    val transcript: String?,
    val assessment: AiAssessment?,
    val syncedToDevice: Boolean = false,
)

/** Compact JSON the phone streams to the vehicle device's black box over BLE. */
fun BlackBoxBundle.toTransferJson(
    eventId: String?,
    severity: String,
    confidence: Double,
    location: String? = null,
    smsSent: Int = 0,
): String {
    val a = assessment
    val text = if (a != null) "${a.whatHappened} ${a.occupantState} ${a.recommendation}" else ""
    val images = listOfNotNull(cabinImagePath, roadImagePath).size
    val tx = (transcript ?: "").replace("\\", " ").replace("\"", "'").replace("\n", " ")
    return """{"eventId":"${eventId ?: ""}","severity":"$severity","confidence":$confidence,""" +
        """"sensorLog":"$sensorLog","audioSec":$audioDurationSec,"preAudioSec":$preAudioDurationSec,""" +
        """"images":$images,"location":"${location ?: ""}","smsSent":$smsSent,""" +
        """"transcript":"$tx","assessment":"$text"}"""
}
