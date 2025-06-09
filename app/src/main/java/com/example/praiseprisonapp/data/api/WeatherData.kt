package com.example.praiseprisonapp.data.api

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class WeatherData {
    fun getCurrentDate(): String {
        val cal = Calendar.getInstance()
        
        // API 제공 시간이 매 3시간 단위이므로 현재 시간에 맞는 base_date를 계산
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        
        // 23시 이후면서 다음 날의 02시 데이터가 아직 없는 경우
        if (hour >= 23 || (hour == 0 && minute < 10)) {
            cal.add(Calendar.DATE, -1)
        }
        
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return dateFormat.format(cal.time)
    }

    fun getCurrentTime(): String {
        val cal = Calendar.getInstance()
        
        // API 제공 시간이 매 3시간 단위이므로 현재 시간에 맞는 base_time을 계산
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        
        // 각 시간대의 관측 데이터는 10분 후에 생성됨
        val baseTime = when {
            hour <= 0 || (hour == 1 && minute < 10) -> "2300"
            hour <= 2 || (hour == 3 && minute < 10) -> "0200"
            hour <= 5 || (hour == 6 && minute < 10) -> "0500"
            hour <= 8 || (hour == 9 && minute < 10) -> "0800"
            hour <= 11 || (hour == 12 && minute < 10) -> "1100"
            hour <= 14 || (hour == 15 && minute < 10) -> "1400"
            hour <= 17 || (hour == 18 && minute < 10) -> "1700"
            hour <= 20 || (hour == 21 && minute < 10) -> "2000"
            else -> "2300"
        }
        return baseTime
    }

    fun parseWeatherData(response: WeatherResponse): WeatherInfo {
        var sky = "1"    // Default sky condition: Clear
        var pty = "0"    // Default precipitation type: None
        var tmp = "0°C"  // Default temperature
        var humidity = ""
        var precipitation = "None"
        var snowfall = "None"

        val items = response.response.body?.items?.item ?: return getDefaultWeatherInfo()

        // Log weather information details
        Log.d("PraisePrison", "📊 Weather Details:")
        items.forEach { item ->
            Log.d("PraisePrison", "- ${item.category}: ${item.fcstValue}")
            when (item.category) {
                "SKY" -> {
                    sky = item.fcstValue
                    Log.d("PraisePrison", "☁️ Sky Condition: ${
                        when(sky) {
                            "1" -> "Clear"
                            "3" -> "Mostly Cloudy"
                            "4" -> "Cloudy"
                            else -> "Unknown"
                        }
                    }")
                }
                "PTY" -> {
                    pty = item.fcstValue
                    Log.d("PraisePrison", "🌧️ Precipitation Type: ${
                        when(pty) {
                            "0" -> "None"
                            "1" -> "Rain"
                            "2" -> "Rain/Snow"
                            "3" -> "Snow"
                            "4" -> "Shower"
                            "5" -> "Drizzle"
                            "6" -> "Drizzle/Snow"
                            "7" -> "Snow Flurries"
                            else -> "Unknown"
                        }
                    }")
                }
                "TMP" -> {
                    tmp = "${item.fcstValue}°C"
                    Log.d("PraisePrison", "🌡️ Temperature: $tmp")
                }
                "REH" -> {
                    humidity = "${item.fcstValue}%"
                }
                "PCP" -> {
                    precipitation = when (item.fcstValue) {
                        "강수없음" -> "No Precipitation"
                        else -> item.fcstValue
                    }
                }
                "SNO" -> {
                    snowfall = when (item.fcstValue) {
                        "적설없음" -> "No Snowfall"
                        else -> item.fcstValue
                    }
                }
            }
        }

        val weatherCode = mapSkyToWeatherCode(sky, pty)
        val weatherDescription = when (weatherCode) {
            "1" -> "Clear"
            "2" -> "Cloudy"
            "3" -> "Rain"
            "4" -> "Snow"
            "5" -> "Thunderstorm"
            else -> "Clear"
        }
        Log.d("PraisePrison", "\uD83C\uDF24 Final Weather: $weatherDescription ($tmp)")

        return WeatherInfo(
            sky = weatherDescription,
            temperature = tmp,
            humidity = humidity,
            rain = precipitation,
            wind = "",
            weatherCode = weatherCode
        )
    }

    private fun getDefaultWeatherInfo(): WeatherInfo {
        return WeatherInfo(
            sky = "Clear",
            temperature = "0°C",
            humidity = "",
            rain = "",
            wind = "",
            weatherCode = "1"
        )
    }

    private fun mapSkyToWeatherCode(sky: String, pty: String): String {
        // Apply PTY first if present
        return when (pty) {
            "0" -> when (sky) {  // No precipitation
                "1" -> "1"  // Clear
                "3" -> "2"  // Mostly Cloudy
                "4" -> "2"  // Cloudy
                else -> "1" // Default to Clear
            }
            "1", "4", "5" -> "3"  // Rain, Shower, Drizzle
            "2", "6" -> "3"       // Rain/Snow, Drizzle/Snow
            "3", "7" -> "4"       // Snow, Snow Flurries
            else -> "1"           // Default to Clear
        }
    }
} 