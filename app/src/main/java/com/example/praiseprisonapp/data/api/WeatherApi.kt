package com.example.praiseprisonapp.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("1360000/VilageFcstInfoService_2.0/getVilageFcst")
    suspend fun getWeather(
        @Query("serviceKey", encoded = true) serviceKey: String,
        @Query("numOfRows") numOfRows: Int = 14,
        @Query("pageNo") pageNo: Int = 1,
        @Query("dataType") dataType: String = "JSON",
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: Int,
        @Query("ny") ny: Int
    ): WeatherResponse
} 