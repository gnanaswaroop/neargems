package com.neargems.android.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.neargems.platform.PostureSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Gravity along the phone's x and y axes, from the gravity sensor, or from the accelerometer when the
 * phone has no separate gravity sensor. Only the two axes that lie in the screen are needed.
 */
class AndroidPostureSource(context: Context) : PostureSource {
    private val sensors = context.getSystemService(SensorManager::class.java)

    override fun gravity(): Flow<Pair<Float, Float>> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0] to event.values[1])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensors.unregisterListener(listener) }
    }
}
