package com.example.smartdrive.presentation.weather

import com.example.smartdrive.presentation.ble.BlePacket

object WeatherSender {

    /** Send current + forecast + UV/pressure to ESP32 using Chronos packet types. */
    fun send(bundle: WeatherBundle, bleSend: (ByteArray) -> Unit) {
        // Map Open-Meteo WMO codes → Chronos icon codes
        // Chronos: 0=sun_cloud,1=sunny,2=snow,3=rain,4=cloud,5=tornado,6=wind,7=haze
        fun mapIcon(wmo: Int): Int = when (wmo) {
            0 -> 1                       // Clear → sunny
            1, 2 -> 0                    // Partly cloudy
            3 -> 4                       // Overcast → cloud
            45, 48 -> 7                  // Fog → haze
            51,53,55,56,57 -> 3          // Drizzle
            61,63,65,66,67,80,81,82 -> 3 // Rain
            71,73,75,77,85,86 -> 2       // Snow
            95,96,99 -> 5                // Thunderstorm
            else -> 4
        }

        // ---- Current + daily temps (0x7E) ----
        // Format per day: [icon<<4 | signBit] [temp]
        // We send current temp as day 0, then forecast highs as days 1..N
        val days = mutableListOf<Pair<Int, Int>>()
        days += mapIcon(bundle.current.code) to bundle.current.tempC
        for (f in bundle.forecast.drop(1)) {
            days += mapIcon(f.code) to f.high
        }

        val payload76 = ByteArray(days.size * 2)
        days.forEachIndexed { i, (icon, temp) ->
            val sign = if (temp < 0) 1 else 0
            val absTemp = kotlin.math.abs(temp).coerceAtMost(127)
            payload76[i * 2] = ((icon shl 4) or sign).toByte()
            payload76[i * 2 + 1] = absTemp.toByte()
        }
        bleSend(BlePacket.buildPacket(0x7E, 0x80, payload76))

        // ---- Weekly high/low (0x88) ----
        // Format per day: [signHigh<<7 | high] [signLow<<7 | low]
        val payload88 = ByteArray(bundle.forecast.size * 2)
        bundle.forecast.forEachIndexed { i, f ->
            val highSign = if (f.high < 0) 0x80 else 0x00
            val lowSign = if (f.low < 0) 0x80 else 0x00
            payload88[i * 2] = (highSign or (kotlin.math.abs(f.high) and 0x7F)).toByte()
            payload88[i * 2 + 1] = (lowSign or (kotlin.math.abs(f.low) and 0x7F)).toByte()
        }
        bleSend(BlePacket.buildPacket(0x88, 0x80, payload88))

        // ---- UV + Pressure (0x8A) ----
        // [uv] [pressure_hi] [pressure_lo] — pressure unknown here, send 1013 (std)
        val pressure = 1013
        val payload8A = byteArrayOf(
            bundle.current.uvIndex.coerceIn(0, 15).toByte(),
            ((pressure shr 8) and 0xFF).toByte(),
            (pressure and 0xFF).toByte()
        )
        bleSend(BlePacket.buildPacket(0x8A, 0x80, payload8A))

        // ---- City name (0xEA header) ----
        val cityBytes = bundle.current.city.toByteArray(Charsets.UTF_8)
        val cityPayload = ByteArray(1 + cityBytes.size)
        cityPayload[0] = 0x00 // padding byte (data[6])
        System.arraycopy(cityBytes, 0, cityPayload, 1, cityBytes.size)
        // Special case: 0xEA header instead of 0xAB
        bleSend(BlePacket.buildEAPacket(0x7E, 0x01, cityPayload))
    }
}
