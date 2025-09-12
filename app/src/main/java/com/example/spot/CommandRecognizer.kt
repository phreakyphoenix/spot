package com.example.spot

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

object CommandRecognizer {

    private const val TAG = "CommandRecognizer"
    private var recognizer: Recognizer? = null
    private var model: Model? = null

    // Restricted grammar commands
    private val COMMAND_GRAMMAR = """["play", "pause", "stop", "next", "previous", "skip ten", "rewind ten"]"""

    fun startListening(context: Context) {
        thread {
            try {
                if (model == null) {
                    val modelPath = copyAssetFolderIfNeeded(context, "vosk-model-small-en-us-0.15")
                    model = Model(modelPath)
                }

                if (recognizer == null) {
                    recognizer = Recognizer(model, 16000.0f)
                    recognizer?.setGrammar(COMMAND_GRAMMAR)
                }

                val bufferSize = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                audioRecord.startRecording()
                Log.d(TAG, "CommandRecognizer started recording")

                val audioBuffer = ByteArray(bufferSize)
                val timeout = System.currentTimeMillis() + 5000 // 5s max listening

                while (System.currentTimeMillis() < timeout) {
                    val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0 && recognizer!!.acceptWaveForm(audioBuffer, read)) {
                        val resultJson = recognizer!!.result
                        handleCommand(resultJson)
                        break
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                Log.d(TAG, "CommandRecognizer stopped recording")

            } catch (e: Exception) {
                Log.e(TAG, "Error in CommandRecognizer", e)
            } finally {
                recognizer?.reset()
            }
        }
    }

    private fun handleCommand(resultJson: String) {
        try {
            val command = JSONObject(resultJson).getString("text")
            Log.d(TAG, "Recognized command: $command")

            when (command.lowercase()) {
                "play", "pause", "stop" -> SpotifyController.getInstance()?.playPause()
                "next" -> SpotifyController.getInstance()?.skipToNext()
                "previous" -> SpotifyController.getInstance()?.skipToPrevious()
                "skip ten" -> SpotifyController.getInstance()?.forward(10)
                "rewind ten" -> SpotifyController.getInstance()?.rewind(10)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle command", e)
        }
    }
    private fun copyAssetFolderIfNeeded(context: Context, assetName: String): String {
        val outDir = File(context.filesDir, assetName)
        if (outDir.exists()) return outDir.absolutePath

        outDir.mkdirs()

        val assetManager = context.assets
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
                copyAssetFolderIfNeeded(context, inPath)
            }
        }

        return outDir.absolutePath
    }

}
