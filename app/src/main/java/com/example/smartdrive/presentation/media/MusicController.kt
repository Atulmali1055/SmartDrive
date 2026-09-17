package com.example.smartdrive.presentation.media

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.view.KeyEvent

class MusicController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ringtone: Ringtone? = null

    fun playPause() {
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
    fun next() {
        sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }
    fun previous() {
        sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
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
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0
        )
    }

    private fun sendKey(keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }

    // ---- Find Phone ----
    fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri).apply {
            play()
        }
    }

    fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }
}
