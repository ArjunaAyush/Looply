package com.arjunaayush.looply.core.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects device shake gestures using the accelerometer sensor.
 * Uses a g-force threshold and debounce window to prevent accidental triggers.
 */
class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        // G-force threshold required to register a deliberate shake
        private const val SHAKE_THRESHOLD_G_FORCE = 2.4f

        // Minimum time gap between shake events to debounce
        private const val SHAKE_DEBOUNCE_MS = 1000L
    }

    private var lastShakeTime = 0L

    fun start(sensorManager: SensorManager?) {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop(sensorManager: SensorManager?) {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_G_FORCE) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime >= SHAKE_DEBOUNCE_MS) {
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
