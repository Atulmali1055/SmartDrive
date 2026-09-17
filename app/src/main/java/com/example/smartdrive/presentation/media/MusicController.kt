package com.example.smartdrive.presentation.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent

class MusicController(private val context: Context) {

    companion object { private const val TAG = "MusicController" }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // A MediaSession we own, so dispatchMediaKeyEvent is accepted by the system
    private val mediaSession = MediaSession(context, "SmartDrive").apply {
        isActive = true
        setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackState.STATE_NONE, 0L, 1f)
                .build()
        )
    }

    private var ringtone: Ringtone? = null
    private var savedVolume: Int = -1

    // ---------------- Media keys ----------------

    fun playPause() = sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next()      = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous()  = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun sendKey(keyCode: Int) {
        // Method 1: transport controls (most reliable)
        try {
            val c = mediaSession.controller
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> c.transportControls.play()
                KeyEvent.KEYCODE_MEDIA_NEXT       -> c.transportControls.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS   -> c.transportControls.skipToPrevious()
            }
        } catch (e: Exception) {
            Log.w(TAG, "transportControls failed", e)
        }
        // Method 2: dispatch key event (fallback)
        try {
            val now = android.os.SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0))
        } catch (e: Exception) {
            Log.w(TAG, "dispatchMediaKeyEvent failed", e)
        }
    }

    // ---------------- Volume ----------------

    /** level is 0..100. */
    fun setVolume(level: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaled = (level.coerceIn(0, 100) * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, scaled, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeUp() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
        )
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
        )
    }

    fun mute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0
            )
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
        }
    }

    // ---------------- Find Phone ----------------

    fun startRingtone() {
        if (ringtone?.isPlaying == true) return

        // Save and raise ring volume
        savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)

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
    }

    fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0)
            savedVolume = -1
        }
    }
}