package com.bikash.weather.network

import com.bikash.weather.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {

    @GET("api.openweathermap.org/data/2.5/weather")
    fun getWeather(
        @Query("lat") lat:Double,
        @Query("lon") lon:Double,
        @Query("units") units:String?,
        @Query("appid") appid:String?

    ): Call<WeatherResponse>
}