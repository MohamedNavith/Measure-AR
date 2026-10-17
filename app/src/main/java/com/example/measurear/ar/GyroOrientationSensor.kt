package com.example.measurear.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DeviceLevelData(
    val pitchDegrees: Int = 0,
    val rollDegrees: Int = 0,
    val inclinationDegrees: Int = 0,
    val isLevel: Boolean = false,
    val isVertical: Boolean = false,
    val isHorizontal: Boolean = false
)

class GyroOrientationSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _levelState = MutableStateFlow(DeviceLevelData())
    val levelState: StateFlow<DeviceLevelData> = _levelState.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    fun startListening() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            computeOrientation(rotationMatrix)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            hasAccelerometer = true
            if (hasMagnetometer) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                    computeOrientation(rotationMatrix)
                }
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            hasMagnetometer = true
            if (hasAccelerometer) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                    computeOrientation(rotationMatrix)
                }
            }
        }
    }

    private fun computeOrientation(rMatrix: FloatArray) {
        SensorManager.getOrientation(rMatrix, orientationAngles)

        // Pitch is rotation around X-axis (-pi to pi)
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        // Roll is rotation around Y-axis (-pi/2 to pi/2)
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Total inclination from flat horizontal (0 degrees = flat on table)
        val zComponent = rMatrix[8] // R[2][2] is cosine of angle with gravity Z
        val inclinationRad = Math.acos(zComponent.toDouble().coerceIn(-1.0, 1.0))
        val inclinationDeg = Math.toDegrees(inclinationRad).toFloat()

        val isHorizontal = inclinationDeg <= 1.5f || inclinationDeg >= 178.5f
        val isVertical = abs(inclinationDeg - 90f) <= 1.5f
        val isLevel = isHorizontal || isVertical || (abs(rollDeg) <= 1.0f && abs(pitchDeg) <= 1.0f)

        _levelState.value = DeviceLevelData(
            pitchDegrees = pitchDeg.roundToInt(),
            rollDegrees = rollDeg.roundToInt(),
            inclinationDegrees = inclinationDeg.roundToInt(),
            isLevel = isLevel,
            isVertical = isVertical,
            isHorizontal = isHorizontal
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
