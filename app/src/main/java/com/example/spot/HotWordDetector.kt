package com.example.spot

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.example.spot.BuildConfig
import kotlin.concurrent.thread
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HotWordDetector(private val context: Context) {

    companion object {
        const val ACTION_WAKEWORD_DETECTED = "com.example.spot.WAKEWORD_DETECTED"
        const val EXTRA_KEYWORD = "keyword"
        const val EXTRA_KEYWORD_INDEX = "keywordIndex"
    }

    fun startListening() {
        startService()
    }

    fun stopListening() {
        stopService()
    }

    fun startService() {
        val intent = Intent(context, PorcupineForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService() {
        val intent = Intent(context, PorcupineForegroundService::class.java)
        context.stopService(intent)
    }
}

class PorcupineForegroundService : Service() {

    private val TAG = "PorcupineFgService"
    private val NOTIF_CHANNEL_ID = "porcupine_fg_channel"
    private val NOTIF_ID = 12345
    private val PICO_ACCESS_KEY = BuildConfig.PICOVOICE_ACCESS_KEY

    private var porcupineManager: PorcupineManager? = null
    private var running = false

    private val KEYWORDS_ASSETS = arrayOf("jarvis_android.ppn", "skip_ten.ppn")
    private val MODEL_ASSET = "porcupine_params.pv"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Hotword listening", "Listening for wake words")
        startForeground(NOTIF_ID, notification)

        thread {
            try {
                initAndStartPorcupineManager()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start PorcupineManager", t)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun initAndStartPorcupineManager() {
        val filesDir = filesDir
        val keywordPaths = KEYWORDS_ASSETS.map { copyAssetToFileIfNeeded(filesDir, it) }.toTypedArray()

        val porc_callback = PorcupineManagerCallback { keywordIndex ->
            val kw = when (keywordIndex) {
                0 -> "jarvis"
                1 -> "skip_ten"
                else -> "keyword_$keywordIndex"
            }
            Log.d(TAG, "Detected keyword: $kw (index $keywordIndex)")

            if (kw == "skip_ten") {
                SpotifyController.getInstance()?.forward(10)
                Log.d(TAG, "Spotify forward 10s triggered by hotword")
            }

            val n = buildNotification("Wake word detected", "Detected '$kw'")
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, n)
        }
        
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(PICO_ACCESS_KEY)
            .setKeywordPaths(keywordPaths)
            .setSensitivities(floatArrayOf(0.9f, 0.9f))
            .build(applicationContext, porc_callback)

        porcupineManager?.start()
        running = true
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping PorcupineManager", e)
        }
        porcupineManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun copyAssetToFileIfNeeded(baseDir: File, assetName: String): String {
        val outFile = File(baseDir, assetName)
        if (outFile.exists()) return outFile.absolutePath
        try {
            applicationContext.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed copying asset [$assetName] to ${outFile.absolutePath}", e)
            throw e
        }
        return outFile.absolutePath
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Porcupine Hotword Service",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
