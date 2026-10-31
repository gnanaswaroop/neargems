package com.neargems.android.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.neargems.platform.HeadingSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Heading from the rotation-vector sensor, which fuses the magnetometer, accelerometer and gyroscope.
 * The value is relative to magnetic north; magnetic declination is not corrected, so the pointer can be
 * off by a few degrees in some places. If the phone has no such sensor, nothing is emitted.
 */
class AndroidHeadingSource(context: Context) : HeadingSource {
    private val sensors = context.getSystemService(SensorManager::class.java)

    override fun headings(): Flow<Float> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)
            private var last = Float.NaN

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val degrees = headingFromRotation(rotation)
                // Skip tiny jitter so the pointer does not shake while the phone is still.
                if (last.isNaN() || angleBetween(last, degrees) >= MIN_CHANGE_DEGREES) {
                    last = degrees
                    trySend(degrees)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    private companion object {
        const val MIN_CHANGE_DEGREES = 2f
    }
}

/**
 * Which way the phone faces, in degrees clockwise from north, from its rotation matrix (device to
 * world, row-major). Lying flat, the top edge points where the phone faces. Held upright, in a hand
 * or mounted in a car, the back camera points where the user faces. Only one of the two is used: the
 * one with the longer horizontal part. Adding them would bend the heading when a mount is rolled.
 */
internal fun headingFromRotation(r: FloatArray): Float {
    // Horizontal parts as east and north. The top edge is device Y; the back camera is device -Z.
    val topEast = r[1]
    val topNorth = r[4]
    val cameraEast = -r[2]
    val cameraNorth = -r[5]
    val useCamera = hypot(cameraEast.toDouble(), cameraNorth.toDouble()) >= hypot(topEast.toDouble(), topNorth.toDouble())
    val east = if (useCamera) cameraEast else topEast
    val north = if (useCamera) cameraNorth else topNorth
    return ((Math.toDegrees(atan2(east.toDouble(), north.toDouble())) + 360) % 360).toFloat()
}
