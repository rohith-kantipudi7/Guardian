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
    val cabinImages: List<String> = emptyList(),
    val roadImages: List<String> = emptyList(),
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
    frames: Int = 0,
    mediaUrl: String? = null,
    withThumbs: Boolean = true,
): String {
    val a = assessment
    val text = if (a != null) "${a.whatHappened} ${a.occupantState} ${a.recommendation}" else ""
    val images = maxOf(listOfNotNull(cabinImagePath, roadImagePath).size, cabinImages.size + roadImages.size)
    val tx = (transcript ?: "").replace("\\", " ").replace("\"", "'").replace("\n", " ")
    // Last frames travel inside the record so the device keeps them if the phone is lost.
    val thumbs = if (withThumbs) {
        """"thumbCabin":"${com.guardian.app.data.media.ThumbnailEncoder.encode(cabinImagePath)}",""" +
            """"thumbRoad":"${com.guardian.app.data.media.ThumbnailEncoder.encode(roadImagePath)}","""
    } else ""
    return """{"eventId":"${eventId ?: ""}","severity":"$severity","confidence":$confidence,""" +
        thumbs +
        """"sensorLog":"$sensorLog","audioSec":$audioDurationSec,"preAudioSec":$preAudioDurationSec,""" +
        """"images":$images,"frames":$frames,"location":"${location ?: ""}","smsSent":$smsSent,""" +
        """"media":"${mediaUrl ?: ""}","transcript":"$tx","assessment":"$text"}"""
}
