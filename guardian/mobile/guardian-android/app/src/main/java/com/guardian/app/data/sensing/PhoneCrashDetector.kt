package com.guardian.app.data.sensing

import com.guardian.app.domain.Severity
import kotlin.math.abs
import kotlin.math.sqrt

/** Result of the phone's own crash analysis. */
data class PhoneCrashResult(
    val severity: Severity,
    val confidence: Double,
    val accelPeakG: Double,
    val rotationPeakRadS: Double,
    val reasons: List<String>,
)

/**
 * Mobile-primary crash detector that runs on the phone's IMU.
 * Mirrors the vehicle crash engine: a short rolling window, a robust k-th-largest peak
 * (rejects single-sample spikes like a dropped phone), and threshold-based scoring.
 */
class PhoneCrashDetector(
    private val impactThresholdG: Double = 3.5,
    private val severeG: Double = 6.0,
    private val rotationThreshold: Double = 3.0,
    private val windowSize: Int = 50,
) {
    private val accelWindow = ArrayDeque<Double>()
    private val rotWindow = ArrayDeque<Double>()

    @Volatile var lastAccelG: Double = 1.0; private set
    @Volatile var lastRotation: Double = 0.0; private set

    /** Feed one accelerometer sample (m/s^2, gravity included). Returns a result when a crash is detected. */
    fun onAccel(ax: Double, ay: Double, az: Double): PhoneCrashResult? {
        val mag = sqrt(ax * ax + ay * ay + az * az) / 9.81
        lastAccelG = mag
        push(accelWindow, abs(mag - 1.0))
        return evaluate()
    }

    /** Feed one gyroscope sample (rad/s). */
    fun onGyro(gx: Double, gy: Double, gz: Double) {
        val rot = sqrt(gx * gx + gy * gy + gz * gz)
        lastRotation = rot
        push(rotWindow, rot)
    }

    private fun push(w: ArrayDeque<Double>, v: Double) {
        w.addLast(v)
        if (w.size > windowSize) w.removeFirst()
    }

    private fun kthLargest(w: Collection<Double>, k: Int): Double {
        if (w.isEmpty()) return 0.0
        val sorted = w.sortedDescending()
        return sorted[minOf(k, sorted.size - 1)]
    }

    private fun evaluate(): PhoneCrashResult? {
        val accelPeak = kthLargest(accelWindow, 2)
        if (accelPeak < impactThresholdG) return null
        val rotPeak = kthLargest(rotWindow, 2)
        val reasons = mutableListOf("high_deceleration")
        if (rotPeak > rotationThreshold) reasons += "rapid_rotation"
        val score = ((accelPeak - impactThresholdG) / (severeG - impactThresholdG)).coerceIn(0.0, 1.0)
        val confidence = (0.6 + 0.4 * score).coerceIn(0.0, 1.0)
        val severity = when {
            accelPeak >= severeG -> Severity.CRITICAL
            accelPeak >= impactThresholdG + 1.0 -> Severity.MODERATE
            else -> Severity.MINOR
        }
        accelWindow.clear()
        rotWindow.clear()
        return PhoneCrashResult(severity, confidence, accelPeak, rotPeak, reasons)
    }

    /** Demo trigger: we cannot really crash the phone, so inject a severe result. */
    fun forceCrash(): PhoneCrashResult {
        accelWindow.clear()
        rotWindow.clear()
        return PhoneCrashResult(Severity.CRITICAL, 0.94, 8.7, 5.2, listOf("high_deceleration", "rapid_rotation"))
    }
}
