package com.example.spot

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    // SpotifyController instance
    private lateinit var spotify: SpotifyController
    // KnockDetector instance
    private lateinit var knockDetector: KnockDetector

    // HotwordDetector instance (now a thin wrapper that starts/stops the service)
    private lateinit var hotwordDetector: HotWordDetector

    // Toast instance
    private var toast: Toast? = null


    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.entries.all { it.value }
            if (allGranted) {
                startHotwordService()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spotify = SpotifyController(this)
        knockDetector = KnockDetector(this)
        hotwordDetector = HotWordDetector(this) 

        // Connect to Spotify
        spotify.connect() {
            Log.d("MainActivity", "Connected to Spotify ✅")
            showToast("Connected to Spotify ✅")

            // Enable buttons after Spotify is connected
            findViewById<Button>(R.id.btn_playpause).isEnabled = true
            findViewById<Button>(R.id.btn_next).isEnabled = true
            findViewById<Button>(R.id.btn_prev).isEnabled = true
            findViewById<Button>(R.id.btn_forward).isEnabled = true
            findViewById<Button>(R.id.btn_rewind).isEnabled = true

            // Start detectors
            knockDetector.start()
            // Start the hotword foreground service (requests permissions first if needed)
            ensurePermissionsAndStartService()
        }

        // Buttons (direct Spotify calls)
        findViewById<Button>(R.id.btn_next).setOnClickListener {
            spotify.skipToNext()
            Log.d("MainActivity", "Next track pressed")
            showToast("Next track ▶️")
        }

        findViewById<Button>(R.id.btn_prev).setOnClickListener {
            spotify.skipToPrevious()
            Log.d("MainActivity", "Previous track pressed")
            showToast("Previous track ⏪")
        }

        findViewById<Button>(R.id.btn_playpause).setOnClickListener {
            spotify.playPause()
            Log.d("MainActivity", "Play/Pause pressed")
            showToast("Play/Pause ⏯️")
        }

        findViewById<Button>(R.id.btn_forward).setOnClickListener {
            spotify.forward(10)
            Log.d("MainActivity", "Forward 10s pressed")
            showToast("Forward 10s ⏩")
        }

        findViewById<Button>(R.id.btn_rewind).setOnClickListener {
            spotify.rewind(10)
            Log.d("MainActivity", "Rewind 10s pressed")
            showToast("Rewind 10s ⏪")
        }

        findViewById<Button>(R.id.btn_hi).setOnClickListener {
            Log.d("MainActivity", "Hi")
            showToast("Hi")
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        knockDetector.stop()
        // Stop the service when activity is destroyed (optional; if you want it persistent, remove this)
        stopHotwordService()
        spotify.disconnect()
    }

    private fun ensurePermissionsAndStartService() {
        val permsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permsToRequest.isEmpty()) {
            startHotwordService()
        } else {
            requestPermissionsLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    private fun startHotwordService() {
        try {
            hotwordDetector.startService()
            showToast("Hotword service starting")
        } catch (t: Throwable) {
            Log.e("MainActivity", "Failed to start hotword service", t)
            showToast("Failed to start hotword service: ${t.message}")
        }
    }

    private fun stopHotwordService() {
        try {
            hotwordDetector.stopService()
            showToast("Hotword service stopped")
        } catch (t: Throwable) {
            Log.e("MainActivity", "Failed to stop hotword service", t)
        }
    }

    fun onFailure(throwable: Throwable) {
        Log.e("SpotifyController", "Failed to connect", throwable)
    }
}
