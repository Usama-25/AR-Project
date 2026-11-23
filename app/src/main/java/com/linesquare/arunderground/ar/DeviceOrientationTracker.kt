package com.linesquare.arunderground.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Real-time orientation and pose data from device hardware sensors.
 *
 * @param azimuthDeg Compass heading in degrees (0° = North, 90° = East, 180° = South, 270° = West)
 * @param pitchDeg Tilt angle looking up/down in degrees (-90° = Nadir/down, 0° = Horizon, +90° = Zenith/up)
 * @param rollDeg Tilt angle rolling left/right in degrees (-180° to +180°)
 * @param relativeAzimuthDeg Orientation relative to the initial forward camera room view
 * @param isSensorActive True if receiving valid hardware sensor updates
 */
data class DeviceOrientation(
    val azimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val relativeAzimuthDeg: Float = 0f,
    val rotationMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f },
    val isSensorActive: Boolean = false
) {
    val compassDirection: String
        get() = try {
            if (!azimuthDeg.isFinite()) "N"
            else when ((((azimuthDeg % 360f + 360f) % 360f)).toInt()) {
                in 338..360, in 0..22 -> "N"
                in 23..67 -> "NE"
                in 68..112 -> "E"
                in 113..157 -> "SE"
                in 158..202 -> "S"
                in 203..247 -> "SW"
                in 248..292 -> "W"
                in 293..337 -> "NW"
                else -> "N"
            }
        } catch (e: Exception) {
            "N"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceOrientation) return false
        return azimuthDeg == other.azimuthDeg && pitchDeg == other.pitchDeg && rollDeg == other.rollDeg
    }

    override fun hashCode(): Int {
        var result = azimuthDeg.hashCode()
        result = 31 * result + pitchDeg.hashCode()
        result = 31 * result + rollDeg.hashCode()
        return result
    }
}

/**
 * High-performance, low-latency device motion tracker using SensorManager.
 * Uses Sensor.TYPE_ROTATION_VECTOR & TYPE_GAME_ROTATION_VECTOR for immediate 60 FPS gyro-fusion,
 * with graceful fallback to Accelerometer + Magnetometer.
 */
class DeviceOrientationTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _orientationState = MutableStateFlow(DeviceOrientation())
    val orientationState: StateFlow<DeviceOrientation> = _orientationState.asStateFlow()

    private val rawRotationMatrix = FloatArray(16)
    private val remappedMatrix = FloatArray(16)
    private val orientationAngles = FloatArray(3)

    private val accelReading = FloatArray(3)
    private val magnetReading = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false

    private val alpha = 0.35f
    private var smoothedAzimuth = 0f
    private var smoothedPitch = 0f
    private var smoothedRoll = 0f

    // Room Reference heading (anchors the 3D models straight in front of user on start)
    private var referenceHeading: Float? = null

    fun start() {
        if (sensorManager == null) return

        // 1. Try Game Rotation Vector (uncalibrated gyro-fusion, instant 60 FPS indoors on all Android 14 devices)
        val gameRotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (gameRotSensor != null) {
            sensorManager.registerListener(this, gameRotSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // 2. Try Standard Rotation Vector (with compass reference)
        val rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotVectorSensor != null) {
            sensorManager.registerListener(this, rotVectorSensor, SensorManager.SENSOR_DELAY_UI)
        }

        // 3. Fallback: Accelerometer + Magnetometer
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Calibrates forward camera view as 0° reference so 3D models are centered in the room.
     */
    fun calibrateRoomForward() {
        referenceHeading = smoothedAzimuth
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rawRotationMatrix, event.values)
                    processRotationMatrix(rawRotationMatrix)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, accelReading, 0, 3)
                    hasAccel = true
                    if (hasMagnet) {
                        if (SensorManager.getRotationMatrix(rawRotationMatrix, null, accelReading, magnetReading)) {
                            processRotationMatrix(rawRotationMatrix)
                        }
                    } else {
                        // Estimate pitch & roll from gravity alone
                        val ax = accelReading[0]
                        val ay = accelReading[1]
                        val az = accelReading[2]
                        val pitch = Math.toDegrees(atan2(-ay.toDouble(), kotlin.math.sqrt((ax * ax + az * az).toDouble()))).toFloat()
                        val roll = Math.toDegrees(atan2(ax.toDouble(), az.toDouble())).toFloat()
                        updateOrientationValues(0f, pitch, roll, FloatArray(16) { if (it % 5 == 0) 1f else 0f })
                    }
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magnetReading, 0, 3)
                    hasMagnet = true
                    if (hasAccel) {
                        if (SensorManager.getRotationMatrix(rawRotationMatrix, null, accelReading, magnetReading)) {
                            processRotationMatrix(rawRotationMatrix)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Safe fallback
        }
    }

    private fun processRotationMatrix(matrix: FloatArray) {
        // Remap coordinates for Android Portrait Camera View:
        // Screen X is X, Screen Y is aligned with world Z (looking forward)
        SensorManager.remapCoordinateSystem(
            matrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remappedMatrix
        )

        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        val rawAzimuthDeg = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
        val rawPitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        if (rawAzimuthDeg.isNaN() || rawPitchDeg.isNaN() || rawRollDeg.isNaN() ||
            !rawAzimuthDeg.isFinite() || !rawPitchDeg.isFinite() || !rawRollDeg.isFinite()) {
            return
        }

        updateOrientationValues(rawAzimuthDeg, rawPitchDeg, rawRollDeg, remappedMatrix)
    }

    private fun updateOrientationValues(azimuth: Float, pitch: Float, roll: Float, matrix: FloatArray) {
        if (referenceHeading == null) {
            referenceHeading = azimuth
        }

        // Circular Azimuth Smoothing
        val diffAzimuth = ((azimuth - smoothedAzimuth + 540f) % 360f) - 180f
        smoothedAzimuth = (smoothedAzimuth + alpha * diffAzimuth + 360f) % 360f

        smoothedPitch = smoothedPitch + alpha * (pitch - smoothedPitch)
        smoothedRoll = smoothedRoll + alpha * (roll - smoothedRoll)

        val ref = referenceHeading ?: smoothedAzimuth
        val relAzimuth = ((smoothedAzimuth - ref + 540f) % 360f) - 180f

        _orientationState.value = DeviceOrientation(
            azimuthDeg = smoothedAzimuth,
            pitchDeg = smoothedPitch,
            rollDeg = smoothedRoll,
            relativeAzimuthDeg = relAzimuth,
            rotationMatrix = matrix.clone(),
            isSensorActive = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
