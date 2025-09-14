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
import androidx.core.app.NotificationCompat
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

    private var isRunning = false

    // Audio components
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    // Command recognition (Vosk)
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null

    /*** Grammar setup ***/
    private fun numberToWords(n: Int): String {
        return when (n) {
            in 1..60 -> numberWords.filterValues { it == n }.keys.first()
            else -> ""
        }
    }
    
    private val numberWords: Map<String, Int> = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "twenty one" to 21, "twenty two" to 22, "twenty three" to 23, "twenty four" to 24, "twenty five" to 25,
        "twenty six" to 26, "twenty seven" to 27, "twenty eight" to 28, "twenty nine" to 29, "thirty" to 30,
        "thirty one" to 31, "thirty two" to 32, "thirty three" to 33, "thirty four" to 34, "thirty five" to 35,
        "thirty six" to 36, "thirty seven" to 37, "thirty eight" to 38, "thirty nine" to 39, "forty" to 40,
        "forty one" to 41, "forty two" to 42, "forty three" to 43, "forty four" to 44, "forty five" to 45,
        "forty six" to 46, "forty seven" to 47, "forty eight" to 48, "forty nine" to 49, "fifty" to 50,
        "fifty one" to 51, "fifty two" to 52, "fifty three" to 53, "fifty four" to 54, "fifty five" to 55,
        "fifty six" to 56, "fifty seven" to 57, "fifty eight" to 58, "fifty nine" to 59, "sixty" to 60
    )

    private val baseCommands = listOf("play","pause","stop","next","previous", "skip","rewind")

    private val numberedCommands = (1..60).map { numberToWords(it) }
        // Build skip and rewind commands with numbers
    private val skipCommands = numberedCommands.map { "skip $it" }
    private val rewindCommands = numberedCommands.map { "rewind $it" }

    private val prefixedCommands = (baseCommands + skipCommands + rewindCommands).map { "jarvis $it" }.toSet()

    private val COMMAND_GRAMMAR =
        "[${prefixedCommands.joinToString(",") { "\"$it\"" }}]"


    override fun onCreate() {
        DebugLogger.serviceLog(tag = TAG, message = "grammar is $COMMAND_GRAMMAR")
        super.onCreate()
        createNotificationChannel()
        DebugLogger.serviceLog(TAG, "AudioPipelineService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.serviceLog(TAG, "AudioPipelineService starting...")
        startForeground(NOTIFICATION_ID, createNotification())
        startAudioPipeline()
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.serviceLog(TAG, "AudioPipelineService destroyed")
        stopAudioPipeline()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /*** Notification ***/
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Voice Control Service", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listens for voice commands"
                setSound(null, null)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
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
            .setContentText("Listening for commands")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    /*** Pipeline control ***/
    private fun startAudioPipeline() {
        if (isRunning) {
            DebugLogger.w(TAG, "Audio pipeline already running")
            return
        }

        thread {
            try {
                initializeComponents()
                runAudioLoop()
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Error in audio pipeline", e, showToast = true, showNotif = true)
            } finally {
                cleanup()
            }
        }
    }

    private fun stopAudioPipeline() {
        isRunning = false
        DebugLogger.serviceLog(TAG, "Pipeline stop requested")
    }

    /*** Component initialization ***/
    private fun initializeComponents() {
        initializeVosk()
        initializeAudioRecord()
        initializeAudioEffects()
        DebugLogger.serviceLog(TAG, "Audio components initialized")
    }

    private fun initializeVosk() {
        try {
            val modelPath = copyAssetFolderIfNeeded("vosk-model-small-en-us-0.15")
            voskModel = Model(modelPath)
            voskRecognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat()).apply {
                setGrammar(COMMAND_GRAMMAR)
            }
            DebugLogger.serviceLog(TAG, "Vosk initialized")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to initialize Vosk", e, showToast = true, showNotif = true)
            throw e
        }
    }

    private fun initializeAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        DebugLogger.serviceLog(TAG, "Creating AudioRecord buffer: $bufferSize, min: $minBufferSize")

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
        if (state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("AudioRecord not initialized, state: $state")
        }
    }

    private fun initializeAudioEffects() {
        val sessionId = audioRecord?.audioSessionId ?: 0

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            DebugLogger.serviceLog(TAG, "AEC enabled")
        } else DebugLogger.w(TAG, "AEC not available")

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            DebugLogger.serviceLog(TAG, "Noise suppressor enabled")
        } else DebugLogger.w(TAG, "Noise suppressor not available")
    }

    /*** Audio loop ***/
    private fun runAudioLoop() {
        audioRecord?.startRecording()
        isRunning = true
        val buffer = ShortArray(512) // fixed buffer for simplicity

        while (isRunning) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) processCommand(buffer, read)
        }

        audioRecord?.stop()
    }

    private fun processCommand(audioBuffer: ShortArray, samplesRead: Int) {
        try {
            val byteBuffer = ByteArray(samplesRead * 2)
            for (i in 0 until samplesRead) {
                val sample = audioBuffer[i]
                byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }

            if (voskRecognizer?.acceptWaveForm(byteBuffer, byteBuffer.size) == true) {
                handleCommand(voskRecognizer?.result ?: "")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Command processing error", e)
        }
    }

    private fun handleCommand(resultJson: String) {
        try {
            val command = JSONObject(resultJson).getString("text").trim().lowercase()
            if (!command.startsWith("jarvis") || command !in prefixedCommands) return // first enhancement: only react to jarvis

            DebugLogger.serviceLog(TAG, "Recognized command: $command")

            when {
                command == "jarvis play" || command == "jarvis pause" || command == "jarvis stop" -> SpotifyController.playPause()
                command == "jarvis next" -> SpotifyController.skipToNext()
                command == "jarvis previous" -> SpotifyController.skipToPrevious()

                command.startsWith("jarvis skip") -> {
                    val numPart = command.removePrefix("jarvis skip").trim()
                    val num = numPart.toIntOrNull() ?: numberWords[numPart] ?: 10
                    SpotifyController.forward(num)
                }

                command.startsWith("jarvis rewind") -> {
                    val numPart = command.removePrefix("jarvis rewind").trim()
                    val num = numPart.toIntOrNull() ?: numberWords[numPart] ?: 10
                    SpotifyController.rewind(num)
                }

                else -> DebugLogger.serviceLog(TAG, "Unknown command: $command")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error handling command", e, showToast = true, showNotif = true)
        }
    }




    /*** Asset helpers ***/
    private fun copyAssetFolderIfNeeded(assetName: String): String {
        val outDir = File(filesDir, assetName)
        if (!outDir.exists()) outDir.mkdirs()

        val files = assets.list(assetName) ?: return outDir.absolutePath
        for (file in files) {
            val inPath = "$assetName/$file"
            val outFile = File(outDir, file)
            if (assets.list(inPath).isNullOrEmpty()) {
                assets.open(inPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            } else copyAssetFolderIfNeeded(inPath)
        }
        return outDir.absolutePath
    }

    /*** Cleanup ***/
    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
            voskRecognizer?.close()
            voskModel?.close()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Cleanup error", e, showToast = true, showNotif = true)
        }
        audioRecord = null
        echoCanceler = null
        noiseSuppressor = null
        voskRecognizer = null
        voskModel = null
        isRunning = false
        DebugLogger.serviceLog(TAG, "Pipeline cleanup completed")
    }
}
