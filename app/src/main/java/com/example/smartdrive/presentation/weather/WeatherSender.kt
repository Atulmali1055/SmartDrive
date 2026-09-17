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
        val days = mutableListOf<Pair<Int, Int>>()
        days += mapIcon(bundle.current.code) to bundle.current.tempC
        for (f in bundle.forecast.drop(1)) {
            days += mapIcon(f.code) to f.high
        }
        bleSend(BlePacket.encodeWeatherCurrent(days))

        // ---- Weekly high/low (0x88) ----
        bleSend(BlePacket.encodeWeatherWeekly(
            highs = bundle.forecast.map { it.high },
            lows = bundle.forecast.map { it.low }
        ))

        // ---- UV + Pressure (0x8A) ----
        val pressure = 1013
        bleSend(BlePacket.encodeWeatherUvPressure(bundle.current.uvIndex, pressure))

        // ---- City name (0xEA header) ----
        bleSend(BlePacket.encodeWeatherCity(bundle.current.city))
    }
}
