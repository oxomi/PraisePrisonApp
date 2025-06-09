package com.example.praiseprisonapp.data.api

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WeatherApiClient {
    private const val BASE_URL = "https://apis.data.go.kr/"
    private const val TAG = "WeatherApiClient"
    
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request()
            try {
                chain.proceed(request)
            } catch (e: Exception) {
                Log.e(TAG, "Network error: ${e.message}", e)
                throw e
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val weatherApi: WeatherApi = retrofit.create(WeatherApi::class.java)

    fun mapSkyToWeatherCode(sky: String, pty: String): String {
        // PTY 코드 (강수형태)
        // 0: 없음, 1: 비, 2: 비/눈, 3: 눈, 4: 소나기, 5: 빗방울, 6: 빗방울/눈날림, 7: 눈날림
        return when {
            pty == "0" -> when (sky) {
                "1" -> "1"  // 맑음
                "2", "3" -> "2"  // 구름조금, 구름많음 -> 흐림
                "4" -> "2"  // 흐림
                else -> "2" // 기본값을 흐림으로 변경
            }
            pty in listOf("1", "4") -> "3"  // 비, 소나기 -> 비
            pty == "5" -> "2"  // 빗방울 -> 흐림
            pty in listOf("2", "3") -> "4"  // 비/눈, 눈 -> 눈
            pty in listOf("6", "7") -> "2"  // 빗방울/눈날림, 눈날림 -> 흐림
            else -> "2"  // 기본값을 흐림으로 변경
        }
    }
} 