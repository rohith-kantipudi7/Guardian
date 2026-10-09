package com.guardian.app.data.ai

import android.util.Log

/**
 * Tries the [primary] reasoner first (cloud), falling back to [fallback] (on-device) if the
 * primary fails or is unreachable. This makes the cloud LLM the primary brain today while the
 * on-device model is still being installed, with no loss of coverage offline.
 */
class CompositeReasoner(
    private val primary: OnDeviceAi,
    private val fallback: OnDeviceAi,
) : OnDeviceAi {
    override val modelName get() = "${primary.modelName} → ${fallback.modelName}"

    override suspend fun assess(ctx: CrashContext): AiAssessment =
        try {
            primary.assess(ctx)
        } catch (e: Exception) {
            Log.i("CompositeReasoner", "cloud AI unavailable (${e.message}); using on-device model")
            fallback.assess(ctx)
        }
}
