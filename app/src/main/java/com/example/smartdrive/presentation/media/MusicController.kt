package com.example.smartdrive.presentation.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Media + ringtone controller for SmartDrive.
 *
 * Media keys are dispatched via AudioManager.dispatchMediaKeyEvent(), which
 * routes to whichever app currently owns the media session. We do NOT create
 * our own MediaSession — doing so steals key routing from the user's player.
 */
class MusicController(private val context: Context) {

    companion object {
        private const val TAG = "MusicController"
    }

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var ringtone: Ringtone? = null
    private var savedVolume: Int = -1

    // ---------------------------------------------------------------
    // Media transport keys
    // ---------------------------------------------------------------

    fun play()      = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun pause()     = sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun playPause() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next()      = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous()  = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun stop()      = sendKey(KeyEvent.KEYCODE_MEDIA_STOP)

    private fun sendKey(keyCode: Int) {
        try {
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "dispatchMediaKeyEvent failed for key=$keyCode", e)
        }
    }

    // ---------------------------------------------------------------
    // Volume
    // ---------------------------------------------------------------

    /** Set absolute volume level, 0..100 (scaled to system max). */
    fun setVolume(level: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaled = (level.coerceIn(0, 100) * max) / 100
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            scaled,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun volumeUp() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun mute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
        }
    }

    // ---------------------------------------------------------------
    // Find Phone (ringtone)
    // ---------------------------------------------------------------

    fun startRingtone() {
        if (ringtone?.isPlaying == true) return

        // Save current ring volume, then force max so the user hears it
        savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
            if (ringtone == null) {
                Log.w(TAG, "No default ringtone available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRingtone failed", e)
        }
    }

    fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopRingtone failed", e)
        }
        ringtone = null

        // Restore previous ring volume
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0)
            savedVolume = -1
        }
    }
}