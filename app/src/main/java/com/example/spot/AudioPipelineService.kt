package com.example.spot

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import com.spotify.protocol.client.Debug

class AudioPipelineService : Service() {
    
    companion object {
        const val CHANNEL_ID = "AudioPipelineChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "AudioPipelineService"
        
        fun startService(context: Context) {
            val intent = Intent(context, AudioPipelineService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AudioPipelineService::class.java)
            context.stopService(intent)
        }
    }
    
    // Audio configuration
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // Pipeline state
    private enum class PipelineState {
        HOTWORD_DETECTION,
        COMMAND_RECOGNITION,
        STOPPED
    }
    
    private var currentState = PipelineState.STOPPED
    private var isRunning = false
    
    // Audio components
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    
    // Porcupine (Hotword Detection)
    private var porcupine: Porcupine? = null
    
    // Vosk (Command Recognition)
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private val COMMAND_GRAMMAR = """["play", "pause", "stop", "next", "previous", "skip ten", "rewind ten", "whisper"]"""
    
    // Timing for command recognition
    private var commandStartTime = 0L
    private val COMMAND_TIMEOUT_MS = 2500L // 2.5 seconds
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        DebugLogger.serviceLog(TAG, "AudioPipelineService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.serviceLog(TAG, "AudioPipelineService starting...")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Start the audio pipeline
        startAudioPipeline()
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.serviceLog(TAG, "AudioPipelineService destroyed")

        // Stop the audio pipeline
        stopAudioPipeline()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listens for voice commands"
                setSound(null, null)
                enableVibration(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Control Active")
            .setContentText("Say 'Jarvis' followed by commands")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .build()
    }
    
    private fun startAudioPipeline() {
        if (isRunning) {
            DebugLogger.w(TAG, "Audio pipeline already running")
            return
        }
        
        thread {
            try {
                initializeComponents()
                runAudioPipeline()
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Error in audio pipeline", e, showToast = true, showNotif = true)
            } finally {
                cleanup()
            }
        }
    }
    
    private fun stopAudioPipeline() {
        isRunning = false
        currentState = PipelineState.STOPPED
        DebugLogger.serviceLog(TAG, "Pipeline stop requested")
    }
    
    private fun initializeComponents() {
        DebugLogger.serviceLog(TAG, "Initializing audio components...")

        // Initialize Porcupine
        try {
            porcupine = Porcupine.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY) // Replace with actual key
                .setKeywordPath(getKeywordPath("jarvis"))
                .setSensitivity(0.9f)
                .build(this)
            DebugLogger.serviceLog(TAG, "Porcupine initialized successfully")
        } catch (e: PorcupineException) {
            DebugLogger.e(TAG, "Failed to initialize Porcupine", e, showToast = true, showNotif = true)
            throw e
        }
        
        // Initialize Vosk
        try {
            val modelPath = copyAssetFolderIfNeeded("vosk-model-small-en-us-0.15")
            voskModel = Model(modelPath)
            voskRecognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat())
            voskRecognizer?.setGrammar(COMMAND_GRAMMAR)
            DebugLogger.serviceLog(TAG, "Vosk initialized successfully")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to initialize Vosk", e, showToast = true, showNotif = true)
            throw e
        }
        
        // Initialize AudioRecord with AEC and NS (from original HotWordDetector_raw.kt)
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        DebugLogger.serviceLog(TAG, "Creating AudioRecord with buffer size: $bufferSize, min: $minBufferSize")

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
                
            val state = audioRecord?.state
            DebugLogger.serviceLog(TAG, "AudioRecord created with state: $state")

            if (state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("AudioRecord not properly initialized, state: $state")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to create AudioRecord", e, showToast = true, showNotif = true)
            throw e
        }
        
        // Enable AEC and NS if available (from original HotWordDetector_raw.kt)
        val audioSessionId = audioRecord?.audioSessionId ?: 0
        DebugLogger.serviceLog(TAG, "Audio session ID: $audioSessionId")

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                echoCanceler?.enabled = true
                DebugLogger.serviceLog(TAG, "AEC enabled successfully")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to enable AEC", e, showToast = true, showNotif = true)
            }
        } else {
            DebugLogger.w(TAG, "AEC not available on this device")
        }
        
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true
                DebugLogger.serviceLog(TAG, "Noise suppressor enabled successfully")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to enable noise suppressor", e, showToast = true, showNotif = true)
            }
        } else {
            DebugLogger.w(TAG, "Noise suppressor not available on this device")
        }

        DebugLogger.serviceLog(TAG, "Audio components initialized successfully")
    }
    
    private fun runAudioPipeline() {
        audioRecord?.startRecording()
        isRunning = true
        currentState = PipelineState.HOTWORD_DETECTION

        DebugLogger.serviceLog(TAG, "Audio pipeline started - listening for hotword")

        val porcupineFrameLength = porcupine?.frameLength ?: return
        val audioBuffer = ShortArray(porcupineFrameLength)
        
        while (isRunning) {
            val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
            
            if (bytesRead > 0) {
                when (currentState) {
                    PipelineState.HOTWORD_DETECTION -> {
                        processHotwordDetection(audioBuffer)
                    }
                    PipelineState.COMMAND_RECOGNITION -> {
                        processCommandRecognition(audioBuffer, bytesRead)
                    }
                    PipelineState.STOPPED -> {
                        break
                    }
                }
            }
        }
        
        audioRecord?.stop()
        DebugLogger.serviceLog(TAG, "Audio pipeline stopped")
    }
    
    private fun processHotwordDetection(audioBuffer: ShortArray) {
        try {
            val keywordIndex = porcupine?.process(audioBuffer) ?: -1
            
            if (keywordIndex >= 0) {
                DebugLogger.serviceLog(TAG, "Hotword 'Jarvis' detected! Switching to command recognition")
                switchToCommandRecognition()
            }
        } catch (e: PorcupineException) {
            DebugLogger.e(TAG, "Error processing hotword detection", e)
        }
    }
    
    private fun processCommandRecognition(audioBuffer: ShortArray, samplesRead: Int) {
        try {
            // Check timeout
            if (System.currentTimeMillis() - commandStartTime > COMMAND_TIMEOUT_MS) {
                DebugLogger.serviceLog(TAG, "Command recognition timeout - switching back to hotword detection")
                switchToHotwordDetection()
                return
            }
            
            // Convert ShortArray to ByteArray for Vosk
            val byteBuffer = ByteArray(samplesRead * 2)
            for (i in 0 until samplesRead) {
                val sample = audioBuffer[i]
                byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            
            if (voskRecognizer?.acceptWaveForm(byteBuffer, byteBuffer.size) == true) {
                val resultJson = voskRecognizer?.result ?: ""
                handleCommand(resultJson)
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error processing command recognition", e)
            switchToHotwordDetection()
        }
    }
    
    private fun switchToCommandRecognition() {
        currentState = PipelineState.COMMAND_RECOGNITION
        commandStartTime = System.currentTimeMillis()
        voskRecognizer?.reset()
        DebugLogger.serviceLog(TAG, "Switched to command recognition mode")
    }
    
    private fun switchToHotwordDetection() {
        currentState = PipelineState.HOTWORD_DETECTION
        DebugLogger.serviceLog(TAG, "Switched to hotword detection mode")
    }
    
    private fun handleCommand(resultJson: String) {
        try {
            val command = JSONObject(resultJson).getString("text").trim()
            
            if (command.isNotEmpty()) {
                DebugLogger.serviceLog(TAG, "Recognized command: $command")
                
                when (command.lowercase()) {
                    "play", "pause", "stop" -> {
                        SpotifyController.getInstance()?.playPause()
                        switchToHotwordDetection()
                    }
                    "next" -> {
                        SpotifyController.getInstance()?.skipToNext()
                        switchToHotwordDetection()
                    }
                    "previous" -> {
                        SpotifyController.getInstance()?.skipToPrevious()
                        switchToHotwordDetection()
                    }
                    "skip ten" -> {
                        SpotifyController.getInstance()?.forward(10)
                        switchToHotwordDetection()
                    }
                    "rewind ten" -> {
                        SpotifyController.getInstance()?.rewind(10)
                        switchToHotwordDetection()
                    }
                    "whisper" -> {
                        DebugLogger.serviceLog(TAG, "Whisper command detected - future extension point")
                        // Future: switchToWhisperMode()
                        switchToHotwordDetection()
                    }
                    else -> {
                        DebugLogger.serviceLog(TAG, "Unknown command: $command")
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error handling command", e, showToast = true, showNotif = true)
            switchToHotwordDetection()
        }
    }
    
    private fun getKeywordPath(keywordName: String): String {
        // This should return the path to your Jarvis keyword file
        return copyKeywordFileIfNeeded("${keywordName}_android.ppn")
    }
    
    private fun copyKeywordFileIfNeeded(fileName: String): String {
        val outFile = File(filesDir, fileName)
        if (outFile.exists()) return outFile.absolutePath
        
        try {
            assets.open(fileName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to copy keyword file: $fileName", e)
        }
        
        return outFile.absolutePath
    }
    
    private fun copyAssetFolderIfNeeded(assetName: String): String {
        val outDir = File(filesDir, assetName)
        if (outDir.exists()) return outDir.absolutePath

        outDir.mkdirs()

        val assetManager = assets
        val files = assetManager.list(assetName) ?: return outDir.absolutePath

        for (file in files) {
            val inPath = "$assetName/$file"
            val outPath = File(outDir, file)

            val subFiles = assetManager.list(inPath)
            if (subFiles.isNullOrEmpty()) {
                // It's a file
                assetManager.open(inPath).use { input ->
                    FileOutputStream(outPath).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // It's a directory → recurse
                copyAssetFolderIfNeeded(inPath)
            }
        }

        return outDir.absolutePath
    }
    
    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
            porcupine?.delete()
            voskRecognizer?.close()
            voskModel?.close()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error during cleanup", e, showToast = true, showNotif = true)
        }
        
        audioRecord = null
        echoCanceler = null
        noiseSuppressor = null
        porcupine = null
        voskRecognizer = null
        voskModel = null
        
        isRunning = false
        currentState = PipelineState.STOPPED
        DebugLogger.serviceLog(TAG, "Pipeline cleanup completed")
    }
}