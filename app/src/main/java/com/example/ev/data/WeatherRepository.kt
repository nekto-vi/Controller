package com.example.ev.data

import android.content.Context
import com.example.ev.LocaleHelper
import com.example.ev.data.local.AppDatabase
import com.example.ev.data.local.toCacheEntity
import com.example.ev.data.local.toWeatherData
import com.example.ev.data.weather.WeatherData
import com.example.ev.data.weather.OpenMeteoWeatherResponse
import com.example.ev.R
import com.example.ev.network.NetworkConnectivity
import com.example.ev.network.RetrofitClient
import com.example.ev.network.GeocodingApiService
import com.example.ev.network.WeatherApiService
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.Locale
import javax.net.ssl.SSLException

class WeatherRepository(private val context: Context) {
    private data class IpLocationResponse(
        val city: String?,
        @SerializedName("country_name") val countryName: String?
    )
    private data class IpWhoIsResponse(
        val success: Boolean?,
        val city: String?,
        val country: String?
    )

    private val cacheDao = AppDatabase.getInstance(context).weatherCacheDao()
    private val apiService: WeatherApiService = RetrofitClient.weatherApiService
    private val geocodingApiService: GeocodingApiService = RetrofitClient.geocodingApiService

    companion object {
        private const val CACHE_DURATION_MS = 30 * 60 * 1000 // 30 минут кэширования
        private const val DEFAULT_CITY = "Minsk"
    }

    private fun offlineMessage(): String =
        context.getString(R.string.weather_offline_no_data)

    fun isNetworkAvailable(): Boolean = NetworkConnectivity.isNetworkAvailable(context)

    fun getOfflineUserMessage(): String = offlineMessage()

    /** Ключ кэша по названию города (поиск). */
    private fun cityCacheKey(city: String, language: String): String {
        val normalized = city.trim().lowercase(Locale.ROOT).replace("\\s+".toRegex(), " ")
        return "city|$normalized|$language"
    }

    /** Ключ кэша по координатам (быстрый выбор города). */
    private fun coordCacheKey(latitude: Double, longitude: Double, language: String): String =
        String.format(Locale.US, "coord|%.4f|%.4f|%s", latitude, longitude, language)

    private fun isLikelyNetworkFailure(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            when (t) {
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException,
                is SocketException,
                is SSLException -> return true
            }
            t = t.cause
        }
        return false
    }

    private suspend fun isCacheExpired(cacheKey: String): Boolean {
        val entity = cacheDao.getByKey(cacheKey) ?: return true
        return System.currentTimeMillis() - entity.cachedAtMillis > CACHE_DURATION_MS
    }

    suspend fun getWeather(city: String = DEFAULT_CITY): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                val languageCode = getLanguageCode()
                val cacheKey = cityCacheKey(city, languageCode)
                val cachedWeather = cacheDao.getByKey(cacheKey)?.toWeatherData()
                if (cachedWeather != null && !isCacheExpired(cacheKey)) {
                    return@withContext Result.success(cachedWeather)
                }

                if (!NetworkConnectivity.isNetworkAvailable(context)) {
                    val stale = cacheDao.getByKey(cacheKey)?.toWeatherData()
                    if (stale != null) {
                        return@withContext Result.success(stale)
                    }
                    return@withContext Result.failure(Exception(offlineMessage()))
                }

                val geocodingResponse = geocodingApiService.searchCity(
                    city = city,
                    language = languageCode
                )
                if (!geocodingResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Geocoding error: ${geocodingResponse.code()} - ${geocodingResponse.message()}")
                    )
                }

                val location = geocodingResponse.body()?.results?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("City not found: $city"))

                val response = apiService.getCurrentWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    if (weatherResponse != null) {
                        val cityLabel = listOfNotNull(location.name, location.country).joinToString(", ")
                        val weatherData = mapToWeatherData(
                            response = weatherResponse,
                            cityLabel = cityLabel.ifBlank { location.name }
                        )
                        cacheDao.insert(weatherData.toCacheEntity(cacheKey))
                        return@withContext Result.success(weatherData)
                    } else {
                        return@withContext Result.failure(Exception("Empty response"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                val languageCode = getLanguageCode()
                val cacheKey = cityCacheKey(city, languageCode)
                val cachedWeather = cacheDao.getByKey(cacheKey)?.toWeatherData()
                if (cachedWeather != null) {
                    return@withContext Result.success(cachedWeather)
                }
                val message = if (isLikelyNetworkFailure(e)) {
                    offlineMessage()
                } else {
                    e.message ?: context.getString(R.string.weather_error)
                }
                return@withContext Result.failure(Exception(message))
            }
        }
    }

    suspend fun getWeatherByCoordinates(latitude: Double, longitude: Double): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                val languageCode = getLanguageCode()
                val cacheKey = coordCacheKey(latitude, longitude, languageCode)
                val cached = cacheDao.getByKey(cacheKey)?.toWeatherData()
                if (cached != null && !isCacheExpired(cacheKey)) {
                    return@withContext Result.success(cached)
                }

                if (!NetworkConnectivity.isNetworkAvailable(context)) {
                    val stale = cacheDao.getByKey(cacheKey)?.toWeatherData()
                    if (stale != null) {
                        return@withContext Result.success(stale)
                    }
                    return@withContext Result.failure(Exception(offlineMessage()))
                }

                val weatherResponse = apiService.getCurrentWeather(
                    latitude = latitude,
                    longitude = longitude
                )
                if (!weatherResponse.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Error: ${weatherResponse.code()} - ${weatherResponse.message()}")
                    )
                }

                val weatherBody = weatherResponse.body()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val reverseResponse = geocodingApiService.reverseGeocode(
                    latitude = latitude,
                    longitude = longitude,
                    language = languageCode
                )
                val location = reverseResponse.body()?.results?.firstOrNull()
                val cityLabel = if (location != null) {
                    listOfNotNull(location.name, location.country).joinToString(", ")
                } else {
                    "Current location"
                }

                val weatherData = mapToWeatherData(
                    response = weatherBody,
                    cityLabel = cityLabel.ifBlank { "Current location" }
                )
                cacheDao.insert(weatherData.toCacheEntity(cacheKey))
                return@withContext Result.success(weatherData)
            } catch (e: Exception) {
                val languageCode = getLanguageCode()
                val cacheKey = coordCacheKey(latitude, longitude, languageCode)
                val stale = cacheDao.getByKey(cacheKey)?.toWeatherData()
                if (stale != null) {
                    return@withContext Result.success(stale)
                }
                val message = if (isLikelyNetworkFailure(e)) {
                    offlineMessage()
                } else {
                    e.message ?: context.getString(R.string.weather_error)
                }
                Result.failure(Exception(message))
            }
        }
    }

    suspend fun getWeatherByIp(): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            try {
                if (!NetworkConnectivity.isNetworkAvailable(context)) {
                    return@withContext Result.failure(Exception(offlineMessage()))
                }

                val gson = com.google.gson.Gson()

                val ipApiJson = URL("https://ipapi.co/json/").readText()
                val ipApiLocation = gson.fromJson(ipApiJson, IpLocationResponse::class.java)
                val ipApiCity = ipApiLocation.city?.trim().orEmpty()
                if (ipApiCity.isNotEmpty()) {
                    return@withContext getWeather(ipApiCity)
                }

                val ipWhoIsJson = URL("https://ipwho.is/").readText()
                val ipWhoIsLocation = gson.fromJson(ipWhoIsJson, IpWhoIsResponse::class.java)
                val ipWhoIsCity = ipWhoIsLocation.city?.trim().orEmpty()
                if (ipWhoIsLocation.success == true && ipWhoIsCity.isNotEmpty()) {
                    return@withContext getWeather(ipWhoIsCity)
                }

                Result.failure(Exception("City not found from IP providers"))
            } catch (_: Exception) {
                Result.failure(Exception("City not found from IP providers"))
            }
        }
    }

    private fun mapToWeatherData(response: OpenMeteoWeatherResponse, cityLabel: String): WeatherData {
        val conditionText = weatherCodeToCondition(response.current.weatherCode)
        return WeatherData(
            cityName = cityLabel,
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

    private fun getLanguageCode(): String {
        return when (LocaleHelper.getLanguage(context)) {
            "ru" -> "ru"
            else -> "en"
        }
    }

    private fun weatherCodeToCondition(code: Int): String {
        val isRu = getLanguageCode() == "ru"
        return when (code) {
            0 -> if (isRu) "Ясно" else "Clear sky"
            1 -> if (isRu) "Преимущественно ясно" else "Mainly clear"
            2 -> if (isRu) "Переменная облачность" else "Partly cloudy"
            3 -> if (isRu) "Пасмурно" else "Overcast"
            45, 48 -> if (isRu) "Туман" else "Fog"
            51, 53, 55 -> if (isRu) "Морось" else "Drizzle"
            56, 57 -> if (isRu) "Ледяная морось" else "Freezing drizzle"
            61, 63, 65 -> if (isRu) "Дождь" else "Rain"
            66, 67 -> if (isRu) "Ледяной дождь" else "Freezing rain"
            71, 73, 75, 77 -> if (isRu) "Снег" else "Snow"
            80, 81, 82 -> if (isRu) "Ливни" else "Rain showers"
            85, 86 -> if (isRu) "Снегопад" else "Snow showers"
            95 -> if (isRu) "Гроза" else "Thunderstorm"
            96, 99 -> if (isRu) "Гроза с градом" else "Thunderstorm with hail"
            else -> if (isRu) "Неизвестно" else "Unknown"
        }
    }
}
