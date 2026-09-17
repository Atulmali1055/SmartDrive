package com.example.smartdrive.presentation.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

/**
 * Chronos-compatible binary packet encoder.
 *
 * Wire format:
 *   0xAB | LEN_HI | LEN_LO | 0xFE | TYPE | SUBTYPE | PAYLOAD...
 *
 * LEN = 3 + payload.size  (footer + type + subtype + payload)
 *
 * Packets > 20 bytes must be chunked when sent over BLE:
 *   - First write:  bytes[0..19]  (20 bytes, as-is)
 *   - Next writes:  [seq][bytes[20+seq*19 ... 38+seq*19]]  (max 19 data bytes + 1 seq byte)
 */
object BlePacket {

    private const val HEADER: Byte = 0xAB.toByte()
    private const val FOOTER: Byte = 0xFE.toByte()

    // Type codes (from ChronosESP32::dataReceived)
    private const val TYPE_NOTIFICATION  = 0x72
    private const val TYPE_PHONE_BATTERY = 0x91
    private const val TYPE_TIME          = 0x93
    private const val TYPE_APP_INFO      = 0xCA
    private const val TYPE_CHUNKED_CFG   = 0xCC
    private const val TYPE_NAV_DATA      = 0xEF

    // App icon IDs (from ChronosESP32::appName)
    const val ICON_CHRONOS         = 0xC0
    const val ICON_WHATSAPP        = 0x0A
    const val ICON_GMAIL           = 0x0B
    const val ICON_MESSENGER       = 0x11
    const val ICON_INSTAGRAM       = 0x12
    const val ICON_TELEGRAM        = 0x18
    const val ICON_SKYPE           = 0x08
    const val ICON_MESSAGE         = 0x03
    const val ICON_DEFAULT         = 0x03

    // Ringer
    const val RINGER_START  = 0x01
    const val RINGER_CANCEL = 0x02

    // ---------------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------------

    /** Navigation inactive */
    fun encodeNavigationInactive(): ByteArray {
        return buildPacket(TYPE_NAV_DATA, 0x00, ByteArray(0))
    }

    /** Navigation active */
    fun encodeNavigation(
        title: String,       // distance to next turn e.g. "350 m"
        duration: String,    // remaining time e.g. "18 min"
        distance: String,    // total distance (may be empty)
        eta: String,         // e.g. "2:55 PM"
        directions: String,  // instruction e.g. "Turn right onto MG Road"
        speed: String = ""   // usually empty
    ): ByteArray {
        // hasIcon=0, isNavigation=1, CRC=0
        val payload = ByteBuffer.allocate(6 + 6 + 512).order(ByteOrder.BIG_ENDIAN)
        payload.put(0)                               // hasIcon
        payload.put(1)                               // isNavigation
        payload.putInt(0)                            // iconCRC
        payload.put(title.toByteArray(Charsets.UTF_8)); payload.put(0)
        payload.put(duration.toByteArray(Charsets.UTF_8)); payload.put(0)
        payload.put(distance.toByteArray(Charsets.UTF_8)); payload.put(0)
        payload.put(eta.toByteArray(Charsets.UTF_8)); payload.put(0)
        payload.put(directions.toByteArray(Charsets.UTF_8)); payload.put(0)
        payload.put(speed.toByteArray(Charsets.UTF_8)); payload.put(0)

        // Trim to actual used size
        val actual = payload.array().copyOf(payload.position())
        return buildPacket(TYPE_NAV_DATA, 0x80, actual)
    }

    // ---------------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------------

    fun encodeNotification(icon: Int, message: String): ByteArray {
        // icon(1) + state(0x02 = new) + message bytes
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + msgBytes.size)
        payload[0] = icon.toByte()
        payload[1] = 0x02
        System.arraycopy(msgBytes, 0, payload, 2, msgBytes.size)
        return buildPacket(TYPE_NOTIFICATION, 0x00, payload)
    }

    fun encodeNotificationIcon(icon: Int, title: String, message: String): ByteArray {
        return encodeNotification(icon, "$title: $message")
    }

    // ---------------------------------------------------------------
    // RINGER (incoming call)
    // ---------------------------------------------------------------

    fun encodeRinger(callerName: String, ringing: Boolean): ByteArray {
        val icon = if (ringing) RINGER_START else RINGER_CANCEL
        val msgBytes = callerName.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + msgBytes.size)
        payload[0] = icon.toByte()
        payload[1] = 0x00
        System.arraycopy(msgBytes, 0, payload, 2, msgBytes.size)
        return buildPacket(TYPE_NOTIFICATION, 0x00, payload)
    }

    // ---------------------------------------------------------------
    // PHONE BATTERY
    // ---------------------------------------------------------------

    fun encodePhoneBattery(level: Int, charging: Boolean): ByteArray {
        val payload = byteArrayOf(
            if (charging) 1 else 0,
            level.toByte()
        )
        return buildPacket(TYPE_PHONE_BATTERY, 0x80, payload)
    }

    // ---------------------------------------------------------------
    // TIME
    // ---------------------------------------------------------------

    fun encodeTime(): ByteArray {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH) + 1
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val second = c.get(Calendar.SECOND)

        // 7 bytes: year_hi, year_lo, month, day, hour, minute, second
        val payload = byteArrayOf(
            ((year shr 8) and 0xFF).toByte(),
            (year and 0xFF).toByte(),
            month.toByte(),
            day.toByte(),
            hour.toByte(),
            minute.toByte(),
            second.toByte()
        )
        return buildPacket(TYPE_TIME, 0x80, payload)
    }

    // ---------------------------------------------------------------
    // APP INFO (sent on connect)
    // ---------------------------------------------------------------

    fun encodeAppInfo(appCode: Int, appVersion: String): ByteArray {
        val verBytes = appVersion.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + verBytes.size)
        payload[0] = ((appCode shr 8) and 0xFF).toByte()
        payload[1] = (appCode and 0xFF).toByte()
        System.arraycopy(verBytes, 0, payload, 2, verBytes.size)
        return buildPacket(TYPE_APP_INFO, 0x80, payload)
    }

    // ---------------------------------------------------------------
    // CHUNKED TRANSFER CONFIG
    // ---------------------------------------------------------------

    fun encodeChunkedConfig(enabled: Boolean): ByteArray {
        return buildPacket(TYPE_CHUNKED_CFG, 0x80, byteArrayOf(if (enabled) 1 else 0))
    }

    // ---------------------------------------------------------------
    // LOW-LEVEL BUILDERS
    // ---------------------------------------------------------------

    /** Public so WeatherSender can use it. */
    fun buildPacket(type: Int, subtype: Int, payload: ByteArray): ByteArray {
        val len = 3 + payload.size
        val total = 6 + payload.size
        return ByteBuffer.allocate(total)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(HEADER)
                putShort(len.toShort())
                put(FOOTER)
                put(type.toByte())
                put(subtype.toByte())
                put(payload)
            }.array()
    }

    /** Build a packet with 0xEA header (weather city). */
    fun buildEAPacket(type: Int, subtype: Int, payload: ByteArray): ByteArray {
        val len = 3 + payload.size
        val total = 6 + payload.size
        return ByteBuffer.allocate(total)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(0xEA.toByte())
                putShort(len.toShort())
                put(FOOTER)
                put(type.toByte())
                put(subtype.toByte())
                put(payload)
            }.array()
    }
}
