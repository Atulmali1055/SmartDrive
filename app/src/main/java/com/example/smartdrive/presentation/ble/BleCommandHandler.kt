package com.example.smartdrive.presentation.ble

import android.util.Log
import com.example.smartdrive.presentation.media.MusicController

/**
 * Parses commands sent by the ESP32 over the TX characteristic (notifications).
 *
 * Known packets (from ChronosESP32::sendCommand):
 *
 *   Music control:   {AB 00 04 FF 9D 80 cmd}
 *                    cmd 0x00 = play
 *                    cmd 0x01 = pause
 *                    cmd 0x02 = previous
 *                    cmd 0x03 = next
 *
 *   Set volume:      {AB 00 05 FF 99 80 A0 level}    level 0..100
 *
 *   Find phone:      {AB 00 04 FF 7D 80 state}       state 0=stop, 1=start
 *
 *   Capture photo:   {AB 00 04 FF 79 80 01}          (not supported)
 */
class BleCommandHandler(private val music: MusicController) {

    companion object {
        private const val TAG = "BleCommandHandler"

        // Packet type codes
        private const val TYPE_MUSIC   = 0x9D
        private const val TYPE_VOLUME  = 0x99
        private const val TYPE_FIND    = 0x7D
        private const val TYPE_CAPTURE = 0x79

        // Music sub-commands
        private const val MUSIC_PLAY     = 0x00
        private const val MUSIC_PAUSE    = 0x01
        private const val MUSIC_PREVIOUS = 0x02
        private const val MUSIC_NEXT     = 0x03

        // Volume op
        private const val VOLUME_ABS = 0xA0

        // Header bytes
        private const val HEADER_1 = 0xAB.toByte()
        private const val FOOTER_1 = 0xFE.toByte()
        private const val FOOTER_2 = 0xFF.toByte()
        private const val SUBTYPE  = 0x80
    }

    /**
     * Entry point — called from BleManager.onCharacteristicChanged when a
     * packet arrives on the TX characteristic.
     */
    fun handle(data: ByteArray) {
        if (data.size < 6) return
        if (data[0] != HEADER_1) return
        if (data[3] != FOOTER_1 && data[3] != FOOTER_2) return

        val type    = data[4].toInt() and 0xFF
        val subtype = data[5].toInt() and 0xFF

        if (subtype != SUBTYPE) {
            Log.d(TAG, "Ignoring unknown subtype 0x${subtype.toString(16)}")
            return
        }

        Log.d(TAG, "Command type=0x${type.toString(16)} len=${data.size}")

        when (type) {
            TYPE_MUSIC   -> handleMusic(data)
            TYPE_VOLUME  -> handleVolume(data)
            TYPE_FIND    -> handleFindPhone(data)
            TYPE_CAPTURE -> Log.d(TAG, "Camera capture command received (not supported)")
            else         -> Log.d(TAG, "Unknown command 0x${type.toString(16)}")
        }
    }

    // ---------------------------------------------------------------
    // Music
    // ---------------------------------------------------------------

    private fun handleMusic(data: ByteArray) {
        if (data.size < 7) {
            Log.w(TAG, "Music packet too short: ${data.size}")
            return
        }
        val cmd = data[6].toInt() and 0xFF
        when (cmd) {
            MUSIC_PLAY     -> { music.play();     Log.d(TAG, "Music → play") }
            MUSIC_PAUSE    -> { music.pause();    Log.d(TAG, "Music → pause") }
            MUSIC_PREVIOUS -> { music.previous(); Log.d(TAG, "Music → previous") }
            MUSIC_NEXT     -> { music.next();     Log.d(TAG, "Music → next") }
            else           -> Log.d(TAG, "Unknown music cmd 0x${cmd.toString(16)}")
        }
    }

    // ---------------------------------------------------------------
    // Volume
    // ---------------------------------------------------------------

    private fun handleVolume(data: ByteArray) {
        if (data.size < 8) {
            Log.w(TAG, "Volume packet too short: ${data.size}")
            return
        }
        val op    = data[6].toInt() and 0xFF
        val level = data[7].toInt() and 0xFF

        when (op) {
            VOLUME_ABS -> {
                music.setVolume(level)
                Log.d(TAG, "Volume → set $level")
            }
            else -> Log.d(TAG, "Unknown volume op 0x${op.toString(16)} level=$level")
        }
    }

    // ---------------------------------------------------------------
    // Find Phone
    // ---------------------------------------------------------------

    private fun handleFindPhone(data: ByteArray) {
        if (data.size < 7) {
            Log.w(TAG, "Find-phone packet too short: ${data.size}")
            return
        }
        val state = data[6].toInt() and 0xFF
        if (state == 1) {
            music.startRingtone()
            Log.d(TAG, "Find phone → start")
        } else {
            music.stopRingtone()
            Log.d(TAG, "Find phone → stop")
        }
    }
}