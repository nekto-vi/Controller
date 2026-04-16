package com.example.ev.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ev.data.weather.WeatherData

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cacheKey: String,
    val cityName: String,
    val temperature: Double,
    val feelsLike: Double,
    val condition: String,
    val description: String,
    val iconCode: String,
    val humidity: Int,
    val windSpeed: Double,
    val cachedAtMillis: Long
)

fun WeatherCacheEntity.toWeatherData(): WeatherData =
    WeatherData(
        cityName = cityName,
        temperature = temperature,
        feelsLike = feelsLike,
        condition = condition,
        description = description,
        iconCode = iconCode,
        humidity = humidity,
        windSpeed = windSpeed,
        timestamp = cachedAtMillis
    )

fun WeatherData.toCacheEntity(cacheKey: String, cachedAtMillis: Long = System.currentTimeMillis()): WeatherCacheEntity =
    WeatherCacheEntity(
        cacheKey = cacheKey,
        cityName = cityName,
        temperature = temperature,
        feelsLike = feelsLike,
        condition = condition,
        description = description,
        iconCode = iconCode,
        humidity = humidity,
        windSpeed = windSpeed,
        cachedAtMillis = cachedAtMillis
    )
