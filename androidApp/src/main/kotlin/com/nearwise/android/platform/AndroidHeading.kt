package com.nearwise.android.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nearwise.platform.HeadingSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

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
                val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
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
