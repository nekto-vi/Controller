package com.example.ev.data

import android.content.Context
import android.content.SharedPreferences
import com.example.ev.LocaleHelper
import com.example.ev.data.weather.WeatherData
import com.example.ev.data.weather.OpenMeteoWeatherResponse
import com.example.ev.network.RetrofitClient
import com.example.ev.network.GeocodingApiService
import com.example.ev.network.WeatherApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val apiService: WeatherApiService = RetrofitClient.weatherApiService
    private val geocodingApiService: GeocodingApiService = RetrofitClient.geocodingApiService

    companion object {
        private const val CACHE_DURATION_MS = 30 * 60 * 1000 // 30 минут кэширования
        private const val DEFAULT_CITY = "Moscow"
    }

    suspend fun getWeather(city: String = DEFAULT_CITY): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                // Проверяем кэш
                val cachedWeather = getCachedWeather(city)
                if (cachedWeather != null && !isCacheExpired(city)) {
                    return@withContext Result.success(cachedWeather)
                }

                val geocodingResponse = geocodingApiService.searchCity(
                    city = city,
                    language = getLanguageCode()
                )
                if (!geocodingResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Geocoding error: ${geocodingResponse.code()} - ${geocodingResponse.message()}")
                    )
                }

                val location = geocodingResponse.body()?.results?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("City not found: $city"))

                // Если кэш устарел или отсутствует, делаем запрос к API погоды
                val response = apiService.getCurrentWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    if (weatherResponse != null) {
                        val weatherData = mapToWeatherData(weatherResponse, location.name)
                        saveToCache(city, weatherData)
                        return@withContext Result.success(weatherData)
                    } else {
                        return@withContext Result.failure(Exception("Empty response"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                // Если интернет отсутствует, пробуем вернуть кэш даже если он устарел
                val cachedWeather = getCachedWeather(city)
                if (cachedWeather != null) {
                    return@withContext Result.success(cachedWeather)
                }
                return@withContext Result.failure(e)
            }
        }
    }

    private fun mapToWeatherData(response: OpenMeteoWeatherResponse, cityName: String): WeatherData {
        val conditionText = weatherCodeToCondition(response.current.weatherCode)
        return WeatherData(
            cityName = cityName,
            temperature = response.current.temperature,
            feelsLike = response.current.feelsLike,
            condition = conditionText,
            description = conditionText,
            iconCode = response.current.weatherCode.toString(),
            humidity = response.current.humidity,
            windSpeed = response.current.windSpeed,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun getCachedWeather(city: String): WeatherData? {
        val cachedJson = prefs.getString("weather_$city", null)
        if (cachedJson == null) return null

        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(cachedJson, WeatherData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToCache(city: String, weatherData: WeatherData) {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(weatherData)
        prefs.edit()
            .putString("weather_$city", json)
            .putLong("weather_${city}_timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun isCacheExpired(city: String): Boolean {
        val lastUpdate = prefs.getLong("weather_${city}_timestamp", 0)
        return System.currentTimeMillis() - lastUpdate > CACHE_DURATION_MS
    }

    private fun getLanguageCode(): String {
        return when (LocaleHelper.getLanguage(context)) {
            "ru" -> "ru"
            else -> "en"
        }
    }

    private fun weatherCodeToCondition(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }
}