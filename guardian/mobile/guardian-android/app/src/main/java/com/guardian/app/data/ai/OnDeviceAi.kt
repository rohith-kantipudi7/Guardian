package com.guardian.app.data.ai

import com.guardian.app.domain.Severity
import kotlin.math.roundToInt

/** On-device AI that turns raw crash signals into a human incident assessment, fully offline. */
interface OnDeviceAi {
    val modelName: String
    suspend fun assess(ctx: CrashContext): AiAssessment
}

/**
 * Edge reasoner: runs entirely on the phone with no network. Composes the assessment from the
 * crash signals and any in-cabin transcript. Swappable for a downloaded LLM (Gemma/LLaMA) via
 * [LlmModelManager] without changing callers.
 */
class EdgeReasoner : OnDeviceAi {
    override val modelName = "Guardian-Edge (on-device)"

    override suspend fun assess(ctx: CrashContext): AiAssessment {
        val g = ctx.accelPeakG
        val rollover = ctx.rotationPeakRadS > 4.0
        val mechanism = when {
            rollover -> "a rollover"
            g >= 8 -> "a high-speed frontal impact"
            g >= 5 -> "a significant collision"
            else -> "a moderate impact"
        }
        val speed = ctx.speedKmh?.let { " at ~${it.roundToInt()} km/h" } ?: ""
        val headline = "Collision detected · " +
            "${(ctx.confidence * 100).roundToInt()}%"
        val whatHappened = "Detected $mechanism$speed — ${"%.1f".format(g)} g" +
            (if (rollover) ", ${"%.1f".format(ctx.rotationPeakRadS)} rad/s rotation." else " deceleration.")
        val occupantState = when {
            ctx.transcript != null -> summariseOccupant(ctx.transcript)
            ctx.hasAudio -> "In-cabin audio recorded (${ctx.audioSec}s, before + after impact) — available to responders; transcription pending."
            else -> "No in-cabin audio captured yet."
        }
        val evidence = buildList {
            if (ctx.hasAudio) add("${ctx.audioSec}s audio")
            if (ctx.imageCount > 0) add("${ctx.imageCount} photo(s)")
        }
        val recommendation = when (ctx.severity) {
            Severity.CRITICAL -> "Dispatch ambulance + police now; treat as life-threatening."
            Severity.MODERATE -> "Dispatch ambulance; confirm occupant responsiveness."
            else -> "Check on the occupant; medical help likely not required."
        }
        val headlineWithEvidence =
            if (evidence.isEmpty()) headline else "$headline · ${evidence.joinToString(" + ")} captured"
        return AiAssessment(headlineWithEvidence, whatHappened, occupantState, recommendation, modelName)
    }

    private fun summariseOccupant(transcript: String): String {
        val t = transcript.lowercase()
        return when {
            listOf("help", "can't", "hurt", "leg", "bleed", "pain").any { it in t } ->
                "Occupant conscious but distressed, asking for help."
            t.isBlank() -> "Audio captured, no speech — possible unconsciousness."
            else -> "Occupant responsive and talking."
        }
    }
}
