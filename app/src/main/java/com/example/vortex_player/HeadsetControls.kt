package com.example.vortex_player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * Receives commands from headphones (Bluetooth or wired) through a MediaSession.
 *
 * Bluetooth earbuds turn taps into commands themselves (play/pause, next, previous), so those
 * arrive ready. Wired headsets with a single button send raw presses, which are counted here:
 * 1 press = play/pause, 2 = next, 3 = previous. Also pauses when the headphones disconnect.
 */
class HeadsetControls(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onPlay()
        fun onPause()
        fun onNext()
        fun onPrevious()
        fun onSeek(positionMs: Long)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var presses = 0
    private val flushPresses = Runnable {
        when (presses) {
            1 -> if (playing) listener.onPause() else listener.onPlay()
            2 -> listener.onNext()
            else -> listener.onPrevious()
        }
        presses = 0
    }
    private var playing = false
    private var noisyRegistered = false

    private val session = MediaSession(context, "Vortex").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = IntentCompat.getParcelableExtra(mediaButtonIntent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    ?: return super.onMediaButtonEvent(mediaButtonIntent)
                if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK) return super.onMediaButtonEvent(mediaButtonIntent)
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    presses++
                    handler.removeCallbacks(flushPresses)
                    if (presses >= 3) flushPresses.run() else handler.postDelayed(flushPresses, MULTI_PRESS_WINDOW_MS)
                }
                return true
            }

            override fun onPlay() = listener.onPlay()
            override fun onPause() = listener.onPause()
            override fun onStop() = listener.onPause()
            override fun onSkipToNext() = listener.onNext()
            override fun onSkipToPrevious() = listener.onPrevious()
            override fun onSeekTo(pos: Long) = listener.onSeek(pos)
        })
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && playing) listener.onPause()
        }
    }

    val token: MediaSession.Token get() = session.sessionToken

    /** [art] should be small (it travels to the system UI); pass null while the cover is still loading. */
    fun setSong(song: Song, durationMs: Int, art: Bitmap?) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.toLong())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build()
        )
    }

    fun setState(isPlaying: Boolean, positionMs: Long) {
        playing = isPlaying
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs,
                    if (isPlaying) 1f else 0f,
                )
                .build()
        )
        session.isActive = true
        setNoisyReceiver(isPlaying)
    }

    fun stopped() {
        playing = false
        session.setPlaybackState(
            PlaybackState.Builder().setActions(ACTIONS).setState(PlaybackState.STATE_STOPPED, 0, 0f).build()
        )
        session.isActive = false
        setNoisyReceiver(false)
    }

    fun release() {
        handler.removeCallbacks(flushPresses)
        setNoisyReceiver(false)
        session.release()
    }

    private fun setNoisyReceiver(enabled: Boolean) {
        if (enabled == noisyRegistered) return
        if (enabled) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.unregisterReceiver(noisyReceiver)
        }
        noisyRegistered = enabled
    }

    private companion object {
        const val MULTI_PRESS_WINDOW_MS = 450L
        const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO
    }
}
