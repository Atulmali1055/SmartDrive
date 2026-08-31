package com.example.smartdrive.presentation.ble

/**
 * Simple text‑based packet for development.
 * Format: TYPE|field1|field2|...
 */
object BlePacket {
    fun encodeTime(hour: Int, minute: Int, second: Int): String {
        return "TIME|${String.format("%02d:%02d:%02d", hour, minute, second)}"
    }

    fun encodeNavigation(
        active: Boolean,
        maneuver: String,
        distance: Int,
        street: String,
        instruction: String,
        eta: String,
        duration: String,
        destination: String = ""
    ): String {
        return "NAV|${if (active) 1 else 0}|$maneuver|$distance|$street|$instruction|$eta|$duration|$destination"
    }

    fun encodeNotification(app: String, title: String, message: String): String {
        return "NOTIF|$app|$title|$message"
    }
}