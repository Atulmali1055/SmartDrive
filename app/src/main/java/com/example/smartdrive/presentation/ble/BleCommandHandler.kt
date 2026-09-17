package com.example.smartdrive.presentation.ble

import android.util.Log
import com.example.smartdrive.presentation.media.MusicController

/**
 * Parses commands sent by the ESP32 over the TX characteristic (notifications).
 *
 * Commands seen in ChronosESP32::sendCommand():
 *   {AB 00 04 FF 9D 80 cmd}          → music control (cmd = 00/01/02/03)
 *   {AB 00 05 FF 99 80 A0 level}     → set volume
 *   {AB 00 04 FF 79 80 01}           → capture photo (ignore)
 *   {AB 00 04 FF 7D 80 state}        → find phone on/off
 */
class BleCommandHandler(private val music: MusicController) {

    companion object {
        private const val TAG = "BleCommandHandler"
    }

    fun handle(data: ByteArray) {
        if (data.size < 6) return
        if (data[0] != 0xAB.toByte()) return
        if (data[3] != 0xFE.toByte() && data[3] != 0xFF.toByte()) return

        val type = data[4].toInt() and 0xFF
        val subtype = data[5].toInt() and 0xFF
        if (subtype != 0x80) return

        Log.d(TAG, "Command type=0x${type.toString(16)} size=${data.size}")

        when (type) {
            0x9D -> handleMusic(data)
            0x99 -> handleVolume(data)
            0x7D -> handleFindPhone(data)
            else -> Log.d(TAG, "Unknown command 0x${type.toString(16)}")
        }
    }

    private fun handleMusic(data: ByteArray) {
        if (data.size < 7) return
        // Music control: byte[6] is the command low byte
        when (data[6].toInt() and 0xFF) {
            0x00 -> { music.playPause(); Log.d(TAG, "Music play") }
            0x01 -> { music.playPause(); Log.d(TAG, "Music pause") }
            0x02 -> { music.previous(); Log.d(TAG, "Music prev") }
            0x03 -> { music.next(); Log.d(TAG, "Music next") }
        }
    }

    private fun handleVolume(data: ByteArray) {
        // Volume command format: AB 00 05 FF 99 80 A0 level
        if (data.size < 8) return
        val op = data[6].toInt() and 0xFF
        val level = data[7].toInt() and 0xFF
        when (op) {
            0xA1 -> music.volumeUp()
            0xA2 -> music.volumeDown()
            0xA3 -> music.mute()
            else -> Log.d(TAG, "Volume op=0x${op.toString(16)} level=$level")
        }
    }

    private fun handleFindPhone(data: ByteArray) {
        if (data.size < 7) return
        val state = data[6].toInt() and 0xFF
        if (state == 1) music.startRingtone() else music.stopRingtone()
        Log.d(TAG, "Find phone state=$state")
    }
}
