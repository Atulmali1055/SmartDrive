package com.example.smartdrive.presentation.ble

import android.util.Log
import com.example.smartdrive.presentation.media.MusicController

/**
 * Parses commands sent by the ESP32 over the TX characteristic.
 *
 * Known packets (from ChronosESP32::sendCommand):
 *   {AB 00 04 FF 9D 80 cmd}           music: cmd 00=play,01=pause,02=prev,03=next
 *   {AB 00 05 FF 99 80 A0 level}      set volume (level 0..100)
 *   {AB 00 04 FF 79 80 01}            capture photo (not supported)
 *   {AB 00 04 FF 7D 80 state}         find phone: state 0/1
 */
class BleCommandHandler(private val music: MusicController) {

    companion object { private const val TAG = "BleCommandHandler" }

    fun handle(data: ByteArray) {
        if (data.size < 6) return
        if (data[0] != 0xAB.toByte()) return
        if (data[3] != 0xFE.toByte() && data[3] != 0xFF.toByte()) return

        val type = data[4].toInt() and 0xFF
        val subtype = data[5].toInt() and 0xFF
        if (subtype != 0x80) return

        Log.d(TAG, "Cmd type=0x${type.toString(16)} size=${data.size}")

        when (type) {
            0x9D -> handleMusic(data)
            0x99 -> handleVolume(data)
            0x7D -> handleFindPhone(data)
            else -> Log.d(TAG, "Unknown cmd 0x${type.toString(16)}")
        }
    }

    private fun handleMusic(data: ByteArray) {
        if (data.size < 7) return
        when (data[6].toInt() and 0xFF) {
            0x00, 0x01 -> music.playPause()
            0x02       -> music.previous()
            0x03       -> music.next()
        }
    }

    private fun handleVolume(data: ByteArray) {
        if (data.size < 8) return
        val op = data[6].toInt() and 0xFF
        val level = data[7].toInt() and 0xFF
        when (op) {
            0xA0 -> music.setVolume(level)   // absolute 0..100
            0xA1 -> music.volumeUp()
            0xA2 -> music.volumeDown()
            0xA3 -> music.mute()
            else -> Log.d(TAG, "Unknown volume op 0x${op.toString(16)}")
        }
    }

    private fun handleFindPhone(data: ByteArray) {
        if (data.size < 7) return
        val state = data[6].toInt() and 0xFF
        if (state == 1) music.startRingtone() else music.stopRingtone()
    }
}