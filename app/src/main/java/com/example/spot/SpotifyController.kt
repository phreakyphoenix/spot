package com.example.spot

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.example.spot.BuildConfig

object SpotifyController {

    private var activity: Activity? = null
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI

    fun init(activity: Activity) {
        this.activity = activity
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        activity?.runOnUiThread {
            Toast.makeText(activity, message, duration).show()
        }
    }

    fun connect(onConnected: () -> Unit = {}) {
        val activity = this.activity ?: return
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("SpotifyController", "Connected to Spotify ✅")
                onConnected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyController", "Failed to connect to Spotify ❌", throwable)
                showToast("Spotify login required ❌", Toast.LENGTH_LONG)
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

    fun skipToPrevious(maxRetries: Int = 2) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            val initialTrack = state.track?.name
            Log.d("SpotifyController", "Initial track: $initialTrack")

            fun attempt(retriesLeft: Int) {
                spotifyAppRemote?.playerApi?.skipPrevious()
                Thread.sleep(200)
                spotifyAppRemote?.playerApi?.playerState?.setResultCallback { newState ->
                    val newTrack = newState.track?.name
                    Log.d("SpotifyController", "New track: $newTrack, Retries left: $retriesLeft")
                    if (newTrack == initialTrack && retriesLeft > 0) {
                        attempt(retriesLeft - 1)
                    }
                }
            }

            attempt(maxRetries)
        }
    }

    fun forward(seconds: Int) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            spotifyAppRemote?.playerApi?.seekTo(state.playbackPosition + seconds * 1000)
        }
        showToast("Forward ⏩ $seconds seconds")
    }

    fun rewind(seconds: Int) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            val newPos = (state.playbackPosition - seconds * 1000).coerceAtLeast(0)
            spotifyAppRemote?.playerApi?.seekTo(newPos)
        }
        showToast("Rewind ⏪ $seconds seconds")
    }

    fun playPlaylist(playlistUri: String) {
        spotifyAppRemote?.playerApi?.play(playlistUri)
    }

    fun isConnected() = spotifyAppRemote != null
}
