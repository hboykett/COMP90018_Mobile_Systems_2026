package com.comp90018.flashcards.ui.play

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.collection.intSetOf
import kotlin.math.abs

/**
 * Using similar framework to TiltFlipDetector
 * sensor set up has similar implementation but will use a gyro instead
 */
class DuoFlipDetector(
    context: Context,
    private val onGesture: (TiltGesture) -> Unit,
) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var armed = true
    private var angle = 0f            // radians rotated since start(); + is UP
    private var lastTimestampNs = 0L  // from the sensor event itself

    /**
     * Registers the sensor listener.
     */
    fun start() {
        armed = true
        angle = 0f
        lastTimestampNs = 0L
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Unregisters the sensor listener to save battery.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // The first event only sets the time reference.
        if (event == null) return
        if (lastTimestampNs == 0L) {
            lastTimestampNs = event.timestamp
            return
        }
        val dt = (event.timestamp - lastTimestampNs) * NS_TO_S
        lastTimestampNs = event.timestamp

        // rad/s about the device's Y axis. Flip AXIS_SIGN if up/down feel swapped.
        val rate = event.values[1] * AXIS_SIGN
        if (abs(rate) > RATE_DEADBAND) angle += rate * dt   // deadband limits drift

        if (!armed) {
            if (abs(angle) < NEUTRAL_ANGLE) armed = true
            return
        }

        when {
            angle > ANGLE_THRESHOLD -> fire(TiltGesture.UP)
            angle < -ANGLE_THRESHOLD -> fire(TiltGesture.DOWN)
        }
    }

    private fun fire(gesture: TiltGesture) {
        armed = false
        onGesture(gesture)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val ANGLE_THRESHOLD = 0.5f   // rad (about 30°). Lower = more sensitive
        const val NEUTRAL_ANGLE = 0.15f    // rad (about 9°). Must return inside this to re-arm
        const val RATE_DEADBAND = 0.05f    // rad/s; ignores sensor noise when the phone is still
        const val AXIS_SIGN = 1f
        const val NS_TO_S = 1e-9f
    }
}
