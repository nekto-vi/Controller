package com.example.ev.data.weather

import com.google.gson.annotations.SerializedName

data class GeocodingResponse(
    val results: List<GeocodingResult>?
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class OpenMeteoWeatherResponse(
    val current: CurrentWeather
)

data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("apparent_temperature") val feelsLike: Double,
    @SerializedName("relative_humidity_2m") val humidity: Int,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    val time: String
)

data class WeatherData(
    val cityName: String,
    val temperature: Double,
    val feelsLike: Double,
    val condition: String,
    val description: String,
    val iconCode: String,
    val humidity: Int,
    val windSpeed: Double,
    val timestamp: Long
)