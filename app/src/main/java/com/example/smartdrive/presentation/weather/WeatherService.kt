package com.example.smartdrive.presentation.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CurrentWeather(
    val city: String,
    val tempC: Int,
    val code: Int,        // Open-Meteo WMO code
    val humidity: Int,
    val windKph: Int,
    val uvIndex: Int
)

data class DailyForecast(
    val dayOfWeek: Int,   // 0=Sun..6=Sat
    val high: Int,
    val low: Int,
    val code: Int
)

data class WeatherBundle(
    val current: CurrentWeather,
    val forecast: List<DailyForecast> // up to 7
)

object WeatherService {

    private const val TAG = "WeatherService"

    /** Fetch weather for coordinates. Uses Open-Meteo (free, no key). */
    suspend fun fetch(lat: Double, lon: Double, city: String): WeatherBundle? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                        "&timezone=auto&forecast_days=7"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body, city)
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch failed", e)
                null
            }
        }

    private fun parse(json: String, city: String): WeatherBundle? = try {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val uvs = daily.optJSONArray("uv_index_max")

        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1

        val forecast = (0 until minOf(7, codes.length())).map { i ->
            DailyForecast(
                dayOfWeek = (today + i) % 7,
                high = highs.getDouble(i).toInt(),
                low = lows.getDouble(i).toInt(),
                code = codes.getInt(i)
            )
        }

        WeatherBundle(
            current = CurrentWeather(
                city = city,
                tempC = current.getDouble("temperature_2m").toInt(),
                code = current.getInt("weather_code"),
                humidity = current.getInt("relative_humidity_2m"),
                windKph = current.getDouble("wind_speed_10m").toInt(),
                uvIndex = uvs?.optDouble(0, 0.0)?.toInt() ?: 0
            ),
            forecast = forecast
        )
    } catch (e: Exception) {
        Log.e(TAG, "parse failed", e)
        null
    }
}
