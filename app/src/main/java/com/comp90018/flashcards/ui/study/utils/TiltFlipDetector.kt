package com.comp90018.flashcards.ui.study.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Detects a "flip" gesture using the accelerometer.
 * A flip is detected when the phone is turned face down (negative Z-axis gravity).
 */
class TiltFlipDetector(
    context: Context,
    private val onFlipDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isFaceDown = false
    private val flipThreshold = -7.0f // Gravity on Z axis when face down (approx -9.8 when flat)

    /**
     * Registers the sensor listener.
     */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    /**
     * Unregisters the sensor listener to save battery.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]

            // If the Z axis value becomes negative enough, consider it "face down"
            if (z < flipThreshold) {
                if (!isFaceDown) {
                    isFaceDown = true
                    onFlipDetected()
                }
            } else if (z > 0) {
                // Reset when turned back face up
                isFaceDown = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
