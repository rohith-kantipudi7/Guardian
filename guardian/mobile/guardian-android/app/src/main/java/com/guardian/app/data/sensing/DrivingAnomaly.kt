package com.guardian.app.data.sensing

import kotlin.math.abs

/** Kinds of abnormal driving the phone flags, from mild to a full crash. */
enum class AnomalyType(val label: String) {
    HARD_BRAKE("Hard braking"),
    SHARP_SWERVE("Sharp swerve"),
    IMPACT("Sudden impact"),
    CRASH("Crash detected"),
    DEVICE_OFFLINE("Device offline"),
    SOS("Manual SOS"),
}

/** One abnormal action, shown in the activity feed instead of a plain history list. */
data class AnomalyEvent(
    val type: AnomalyType,
    val detail: String,
    val magnitude: Double,
    val timestampMs: Long,
    val critical: Boolean = false,
)

/**
 * Flags sub-crash abnormal driving (hard braking, sharp swerves, jolts) from the phone's IMU.
 * Conservative thresholds + a debounce keep the activity feed meaningful rather than noisy.
 * A true crash is handled separately by [PhoneCrashDetector].
 */
class DrivingAnomalyDetector(
    private val hardBrakeJoltG: Double = 0.75,
    private val impactJoltG: Double = 1.6,
    private val swerveRotationRadS: Double = 1.8,
    private val crashJoltG: Double = 3.5,
    private val debounceMs: Long = 4000,
) {
    private var lastEmitMs = 0L

    /** Returns an anomaly for this sample, or null. [accelG] includes gravity; [rotationRadS] rad/s. */
    fun sample(accelG: Double, rotationRadS: Double, now: Long): AnomalyEvent? {
        if (now - lastEmitMs < debounceMs) return null
        val jolt = abs(accelG - 1.0)
        if (jolt >= crashJoltG) return null // a crash, not a sub-crash anomaly
        val event = when {
            jolt >= impactJoltG ->
                AnomalyEvent(AnomalyType.IMPACT, "${"%.1f".format(jolt + 1.0)} g jolt", jolt, now)
            rotationRadS >= swerveRotationRadS && jolt >= 0.5 ->
                AnomalyEvent(AnomalyType.SHARP_SWERVE, "${"%.1f".format(rotationRadS)} rad/s turn", rotationRadS, now)
            jolt >= hardBrakeJoltG ->
                AnomalyEvent(AnomalyType.HARD_BRAKE, "${"%.1f".format(jolt)} g deceleration", jolt, now)
            else -> null
        } ?: return null
        lastEmitMs = now
        return event
    }
}
