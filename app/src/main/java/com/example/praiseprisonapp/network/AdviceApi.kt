package com.example.praiseprisonapp.network

import com.example.praiseprisonapp.data.model.Advice
import retrofit2.Call
import retrofit2.http.GET

interface AdviceApi {
    @GET("api/advice")
    fun getRandomAdvice(): Call<Advice>
}