package com.guardian.app.data.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Live view of the phone's primary crash sensing. */
data class PhoneSensingState(
    val active: Boolean = false,
    val accelG: Double = 1.0,
    val rotationRadS: Double = 0.0,
)

/**
 * Mobile-as-primary: the phone's own accelerometer + gyroscope run crash detection first.
 * The vehicle (IoT) device is the fallback black box if the phone is destroyed.
 */
class PhoneSensing(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val detector = PhoneCrashDetector()
    private val anomalyDetector = DrivingAnomalyDetector()

    private val _state = MutableStateFlow(PhoneSensingState())
    val state: StateFlow<PhoneSensingState> = _state

    private val _crashes = MutableSharedFlow<PhoneCrashResult>(extraBufferCapacity = 4)
    val crashes: SharedFlow<PhoneCrashResult> = _crashes

    private val _anomalies = MutableSharedFlow<AnomalyEvent>(extraBufferCapacity = 16)
    val anomalies: SharedFlow<AnomalyEvent> = _anomalies

    fun start() {
        accel?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        _state.value = _state.value.copy(active = accel != null)
    }

    fun stop() {
        sensors.unregisterListener(this)
        _state.value = _state.value.copy(active = false)
    }

    /** Demo button: raise a phone-detected crash without actually crashing. */
    fun simulateCrash() = _crashes.tryEmit(detector.forceCrash())

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val result = detector.onAccel(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                _state.value = _state.value.copy(accelG = detector.lastAccelG)
                if (result != null) {
                    _crashes.tryEmit(result)
                } else {
                    anomalyDetector.sample(detector.lastAccelG, detector.lastRotation, System.currentTimeMillis())
                        ?.let { _anomalies.tryEmit(it) }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                detector.onGyro(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                _state.value = _state.value.copy(rotationRadS = detector.lastRotation)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
