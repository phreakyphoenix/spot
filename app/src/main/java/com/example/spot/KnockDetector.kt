package com.example.spot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class KnockDetector(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastTapTime: Long = 0
    private var tapCount = 0

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values[2]  // axis pointing out of the back
        val now = System.currentTimeMillis()

        if (z > 12f) { // Threshold (tune as needed)
            if (now - lastTapTime < 300) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = now

            if (tapCount == 2) {
                Log.d("KnockDetector", "Double knock detected ✅")
                // Call Spotify forward using singleton
                // SpotifyController.getInstance()?.forward(10)
                tapCount = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
