package com.example.smartdrive.presentation.ble

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

/**
 * Chronos-compatible binary packet encoder.
 *
 * Wire format:
 *   [HEADER], [LEN_HI], [LEN_LO], [FOOTER], [TYPE], [SUBTYPE], [PAYLOAD...]
 *
 *   HEADER = 0xAB (regular) or 0xEA (weather city)
 *   FOOTER = 0xFE (regular) or 0xFF (some config)
 *   LEN    = 3 + payload.size  (footer + type + subtype + payload)
 *
 * Packets larger than 20 bytes are chunked by BleManager before writing:
 *   - First write: bytes[0..19] as-is
 *   - Next writes: [seq] + up to 19 data bytes
 */
object BlePacket {

    private const val HEADER_AB: Byte = 0xAB.toByte()
    private const val HEADER_EA: Byte = 0xEA.toByte()
    private const val FOOTER_FE: Byte = 0xFE.toByte()

    // Type codes (from ChronosESP32::dataReceived)
    private const val TYPE_NOTIFICATION  = 0x72
    private const val TYPE_PHONE_BATTERY = 0x91
    private const val TYPE_TIME          = 0x93
    private const val TYPE_APP_INFO      = 0xCA
    private const val TYPE_CHUNKED_CFG   = 0xCC
    private const val TYPE_NAV_DATA      = 0xEF
    private const val TYPE_HR24_CONFIG   = 0x7C
    private const val TYPE_WEATHER_CUR   = 0x7E
    private const val TYPE_WEATHER_WEEK  = 0x88
    private const val TYPE_WEATHER_UV    = 0x8A

    // ------------------------------------------------------------------
    // App icon IDs — mapped by ChronosESP32::appName(int id)
    // Only the IDs the library recognizes will render as distinct icons.
    // Unrecognized IDs fall back to "Message" on the device.
    // ------------------------------------------------------------------
    const val ICON_MESSAGE          = 0x03
    const val ICON_MAIL             = 0x04
    const val ICON_TENCENT          = 0x07
    const val ICON_SKYPE            = 0x08
    const val ICON_WECHAT           = 0x09
    const val ICON_WHATSAPP         = 0x0A
    const val ICON_GMAIL            = 0x0B
    const val ICON_LINE             = 0x0E
    const val ICON_TWITTER          = 0x0F
    const val ICON_FACEBOOK         = 0x10
    const val ICON_MESSENGER        = 0x11
    const val ICON_INSTAGRAM        = 0x12
    const val ICON_WEIBO            = 0x13
    const val ICON_KAKAO            = 0x14
    const val ICON_VIBER            = 0x16
    const val ICON_VKONTAKTE        = 0x17
    const val ICON_TELEGRAM         = 0x18
    const val ICON_DINGTALK         = 0x1B
    const val ICON_WHATSAPP_BUS     = 0x20
    const val ICON_WEARFIT          = 0x22
    const val ICON_CHRONOS          = 0xC0
    const val ICON_DEFAULT          = ICON_MESSAGE

    // Ringer sub-commands
    const val RINGER_START  = 0x01
    const val RINGER_CANCEL = 0x02

    // ------------------------------------------------------------------
    // NAVIGATION
    // ------------------------------------------------------------------

    /** Navigation inactive — tells the ESP32 to hide navigation and show the clock. */
    fun encodeNavigationInactive(): ByteArray =
        buildPacket(TYPE_NAV_DATA, 0x00, ByteArray(0))

    /**
     * Navigation active.
     *
     * Payload layout matches ChronosESP32::dataReceived() case 0xEF, subtype 0x80:
     *   [hasIcon:1] [isNavigation:1] [iconCRC:4] [title\0] [duration\0]
     *   [distance\0] [eta\0] [directions\0] [speed\0]
     */
    fun encodeNavigation(
        title: String,
        duration: String,
        distance: String,
        eta: String,
        directions: String,
        speed: String = ""
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // hasIcon (0), isNavigation (1), iconCRC (0)
        out.write(0)
        out.write(1)
        out.write(byteArrayOf(0, 0, 0, 0))
        out.writeNullTerminated(title)
        out.writeNullTerminated(duration)
        out.writeNullTerminated(distance)
        out.writeNullTerminated(eta)
        out.writeNullTerminated(directions)
        out.writeNullTerminated(speed)
        return buildPacket(TYPE_NAV_DATA, 0x80, out.toByteArray())
    }

    // ------------------------------------------------------------------
    // NOTIFICATION
    // ------------------------------------------------------------------

    /**
     * Notification packet.
     * Payload: [icon:1] [state:1 = 0x02 = new] [message bytes]
     */
    fun encodeNotification(icon: Int, message: String): ByteArray {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + msgBytes.size)
        payload[0] = icon.toByte()
        payload[1] = 0x02
        System.arraycopy(msgBytes, 0, payload, 2, msgBytes.size)
        return buildPacket(TYPE_NOTIFICATION, 0x00, payload)
    }

    /** Convenience overload: prepends "title: " to the message. */
    fun encodeNotificationIcon(icon: Int, title: String, message: String): ByteArray =
        encodeNotification(icon, "$title: $message")

    // ------------------------------------------------------------------
    // RINGER (incoming call)
    // ------------------------------------------------------------------

    fun encodeRinger(callerName: String, ringing: Boolean): ByteArray {
        val icon = if (ringing) RINGER_START else RINGER_CANCEL
        val msgBytes = callerName.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + msgBytes.size)
        payload[0] = icon.toByte()
        payload[1] = 0x00
        System.arraycopy(msgBytes, 0, payload, 2, msgBytes.size)
        return buildPacket(TYPE_NOTIFICATION, 0x00, payload)
    }

    // ------------------------------------------------------------------
    // PHONE BATTERY
    // ------------------------------------------------------------------

    fun encodePhoneBattery(level: Int, charging: Boolean): ByteArray {
        val payload = byteArrayOf(
            if (charging) 1 else 0,
            level.coerceIn(0, 100).toByte()
        )
        return buildPacket(TYPE_PHONE_BATTERY, 0x80, payload)
    }

    // ------------------------------------------------------------------
    // TIME
    // ------------------------------------------------------------------

    fun encodeTime(): ByteArray {
        val c = Calendar.getInstance()
        val year   = c.get(Calendar.YEAR)
        val month  = c.get(Calendar.MONTH) + 1
        val day    = c.get(Calendar.DAY_OF_MONTH)
        val hour   = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val second = c.get(Calendar.SECOND)

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

    // ------------------------------------------------------------------
    // APP INFO
    // ------------------------------------------------------------------

    fun encodeAppInfo(appCode: Int, appVersion: String): ByteArray {
        val verBytes = appVersion.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + verBytes.size)
        payload[0] = ((appCode shr 8) and 0xFF).toByte()
        payload[1] = (appCode and 0xFF).toByte()
        System.arraycopy(verBytes, 0, payload, 2, verBytes.size)
        return buildPacket(TYPE_APP_INFO, 0x80, payload)
    }

    // ------------------------------------------------------------------
    // CHUNKED TRANSFER CONFIG
    // ------------------------------------------------------------------

    fun encodeChunkedConfig(enabled: Boolean): ByteArray =
        buildPacket(TYPE_CHUNKED_CFG, 0x80, byteArrayOf(if (enabled) 1 else 0))

    // ------------------------------------------------------------------
    // 24-HOUR / 12-HOUR CONFIG
    // ------------------------------------------------------------------

    /**
     * CF_HR24 configuration.
     *
     * Chronos decodes: `_hour24 = (data[6] == 0)`.
     *   mode 0x00 → 24-hour mode ON
     *   mode 0x01 → 12-hour mode (AM/PM)
     */
    fun encodeHour24Config(use24Hour: Boolean): ByteArray {
        val mode: Byte = if (use24Hour) 0x00 else 0x01
        return buildPacket(TYPE_HR24_CONFIG, 0x80, byteArrayOf(mode))
    }

    // ------------------------------------------------------------------
    // WEATHER
    // ------------------------------------------------------------------

    /**
     * Current + daily temperatures.
     *
     * Payload: for each day N:
     *   byte[2N]   = (iconCode << 4) | signBit   — signBit=1 means negative temp
     *   byte[2N+1] = abs(temp°C)  (0..127)
     *
     * Day 0 = current temperature.
     * Days 1..N = forecast highs.
     */
    fun encodeWeatherCurrent(entries: List<Pair<Int, Int>>): ByteArray {
        val payload = ByteArray(entries.size * 2)
        entries.forEachIndexed { i, (icon, temp) ->
            val sign = if (temp < 0) 1 else 0
            val absTemp = kotlin.math.abs(temp).coerceAtMost(127)
            payload[i * 2]     = ((icon shl 4) or sign).toByte()
            payload[i * 2 + 1] = absTemp.toByte()
        }
        return buildPacket(TYPE_WEATHER_CUR, 0x80, payload)
    }

    /**
     * Weekly high/low forecast.
     *
     * Payload: for each day N:
     *   byte[2N]   = (signHigh << 7) | abs(high)
     *   byte[2N+1] = (signLow  << 7) | abs(low)
     */
    fun encodeWeatherWeekly(
        highs: List<Int>,
        lows: List<Int>
    ): ByteArray {
        val count = minOf(highs.size, lows.size)
        val payload = ByteArray(count * 2)
        for (i in 0 until count) {
            val h = highs[i]; val l = lows[i]
            payload[i * 2]     = (((if (h < 0) 0x80 else 0)) or (kotlin.math.abs(h) and 0x7F)).toByte()
            payload[i * 2 + 1] = (((if (l < 0) 0x80 else 0)) or (kotlin.math.abs(l) and 0x7F)).toByte()
        }
        return buildPacket(TYPE_WEATHER_WEEK, 0x80, payload)
    }

    /**
     * UV index + pressure.
     * Payload: [uv:1] [pressureHi:1] [pressureLo:1]
     */
    fun encodeWeatherUvPressure(uvIndex: Int, pressureHpa: Int): ByteArray {
        val payload = byteArrayOf(
            uvIndex.coerceIn(0, 15).toByte(),
            ((pressureHpa shr 8) and 0xFF).toByte(),
            (pressureHpa and 0xFF).toByte()
        )
        return buildPacket(TYPE_WEATHER_UV, 0x80, payload)
    }

    /**
     * City name — sent with 0xEA header (not 0xAB).
     * Payload: [0x00 padding] [city name bytes]
     */
    fun encodeWeatherCity(city: String): ByteArray {
        val cityBytes = city.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + cityBytes.size)
        payload[0] = 0x00
        System.arraycopy(cityBytes, 0, payload, 1, cityBytes.size)
        return buildPacketWithHeader(HEADER_EA, TYPE_WEATHER_CUR, 0x01, payload)
    }

    // ------------------------------------------------------------------
    // LOW-LEVEL BUILDERS
    // ------------------------------------------------------------------

    /** Regular packet — 0xAB header. */
    private fun buildPacket(type: Int, subtype: Int, payload: ByteArray): ByteArray =
        buildPacketWithHeader(HEADER_AB, type, subtype, payload)

    /**
     * Generic packet builder.
     * Layout: [header] [len_hi] [len_lo] [0xFE] [type] [subtype] [payload]
     * LEN = 3 + payload.size
     */
    private fun buildPacketWithHeader(
        header: Byte,
        type: Int,
        subtype: Int,
        payload: ByteArray
    ): ByteArray {
        val len = 3 + payload.size
        return ByteBuffer.allocate(6 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(header)
                putShort(len.toShort())
                put(FOOTER_FE)
                put(type.toByte())
                put(subtype.toByte())
                put(payload)
            }
            .array()
    }

    /** Write a UTF-8 string followed by a null terminator to the stream. */
    private fun ByteArrayOutputStream.writeNullTerminated(s: String) {
        write(s.toByteArray(Charsets.UTF_8))
        write(0)
    }
}