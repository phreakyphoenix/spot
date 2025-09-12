package com.example.spot

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState

// create helper function for toast and vibration feedback
fun Activity.feedback(message: String, duration: Long = 100) {
    runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        val vibrator = getSystemService(Activity.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}


class SpotifyController(private val activity: Activity) {

    companion object {
        private var instance: SpotifyController? = null
        fun getInstance(): SpotifyController? = instance
    }

    init {
        instance = this
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null

    fun connect(clientId: String, redirectUri: String, onConnected: () -> Unit = {}) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true) // forces login UI
            .build()

        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("SpotifyController", "Connected to Spotify ✅")
                onConnected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyController", "Failed to connect to Spotify ❌", throwable)
                Toast.makeText(activity, "Spotify login required ❌", Toast.LENGTH_LONG).show()
            }
        })
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
            spotifyAppRemote = null
        }
    }

    fun playPause() {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            if (state.isPaused) spotifyAppRemote?.playerApi?.resume()
            else spotifyAppRemote?.playerApi?.pause()
        }
    }

    fun skipToNext() = spotifyAppRemote?.playerApi?.skipNext()
    fun skipToPrevious() = spotifyAppRemote?.playerApi?.skipPrevious()
    
    fun forward(seconds: Long) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            spotifyAppRemote?.playerApi?.seekTo(state.playbackPosition + seconds * 1000)
        }
        activity.feedback("Forward ⏩ $seconds seconds")
    }

    fun rewind(seconds: Long) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            val newPos = (state.playbackPosition - seconds * 1000).coerceAtLeast(0)
            spotifyAppRemote?.playerApi?.seekTo(newPos)
            activity.feedback("Rewind ⏪ $seconds seconds")
        }
    }

    fun playPlaylist(playlistUri: String) {
        spotifyAppRemote?.playerApi?.play(playlistUri)
    }


    fun isConnected() = spotifyAppRemote != null
}
