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
        val items = response.response.body?.items?.item ?: emptyList()
        
        var temperature = ""
        var humidity = ""
        var rainProbability = ""
        var windSpeed = ""
        var weatherCode = "1" // 기본값: 맑음
        var sky = "맑음"

        for (item in items) {
            when (item.category) {
                "TMP" -> temperature = "${item.fcstValue}°C"
                "REH" -> humidity = "${item.fcstValue}%"
                "POP" -> rainProbability = "${item.fcstValue}%"
                "WSD" -> windSpeed = "${item.fcstValue}m/s"
                "PTY" -> {
                    weatherCode = when (item.fcstValue) {
                        "0" -> "1" // 맑음
                        "1" -> "2" // 비
                        "2" -> "2" // 비/눈
                        "3" -> "3" // 눈
                        "4" -> "2" // 소나기
                        else -> "1"
                    }
                }
                "SKY" -> {
                    sky = when (item.fcstValue) {
                        "1" -> "맑음"
                        "3" -> "구름많음"
                        "4" -> "흐림"
                        else -> "맑음"
                    }
                    if (weatherCode == "1") { // PTY가 없을 때만 SKY 코드로 날씨 아이콘 결정
                        weatherCode = when (item.fcstValue) {
                            "1" -> "1" // 맑음
                            "3", "4" -> "4" // 흐림
                            else -> "1"
                        }
                    }
                }
            }
        }

        return WeatherInfo(
            sky = sky,
            temperature = temperature,
            humidity = humidity,
            rain = rainProbability,
            wind = windSpeed,
            weatherCode = weatherCode
        )
    }
} 