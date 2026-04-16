package com.example.ev.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ev.Scenario
import com.example.ev.data.ScenarioRepository
import com.example.ev.data.WeatherRepository
import com.example.ev.data.weather.WeatherData
import com.example.ev.search.FuzzySearch
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ScenarioRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {
    enum class SortMode {
        DATE_DESC,
        DATE_ASC,
        NAME_ASC,
        NAME_DESC,
        TEMPERATURE_ASC,
        TEMPERATURE_DESC
    }

    private val _scenarios = MutableLiveData<List<Scenario>>(emptyList())
    val scenarios: LiveData<List<Scenario>> = _scenarios
    private var allScenarios: List<Scenario> = emptyList()

    private var searchQuery: String = ""
    private var selectedRoomKey: String? = null
    private var temperatureRange: IntRange? = null
    private var sortMode: SortMode = SortMode.DATE_DESC

    private val _weather = MutableLiveData<WeatherData?>()
    val weather: LiveData<WeatherData?> = _weather

    private val _isLoadingWeather = MutableLiveData(false)
    val isLoadingWeather: LiveData<Boolean> = _isLoadingWeather

    private val _weatherError = MutableLiveData<String?>()
    val weatherError: LiveData<String?> = _weatherError

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadScenarios()
    }

    fun loadWeather(city: String = "Minsk") {
        viewModelScope.launch {
            _isLoadingWeather.value = true
            try {
                val result = weatherRepository.getWeather(city)
                result.onSuccess { weatherData ->
                    _weather.value = weatherData
                    _weatherError.value =
                        if (weatherRepository.isNetworkAvailable()) null
                        else weatherRepository.getOfflineUserMessage()
                }.onFailure { exception ->
                    _weather.value = null
                    _weatherError.value = exception.message ?: "Failed to load weather"
                }
            } catch (e: Exception) {
                _weather.value = null
                _weatherError.value = e.message
            } finally {
                _isLoadingWeather.value = false
            }
        }
    }

    fun refreshWeather(city: String = "Minsk") {
        loadWeather(city)
    }

    fun loadWeatherByCoordinates(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _isLoadingWeather.value = true
            try {
                val result = weatherRepository.getWeatherByCoordinates(latitude, longitude)
                result.onSuccess { weatherData ->
                    _weather.value = weatherData
                    _weatherError.value =
                        if (weatherRepository.isNetworkAvailable()) null
                        else weatherRepository.getOfflineUserMessage()
                }.onFailure { exception ->
                    _weather.value = null
                    _weatherError.value = exception.message ?: "Failed to load weather"
                }
            } catch (e: Exception) {
                _weather.value = null
                _weatherError.value = e.message
            } finally {
                _isLoadingWeather.value = false
            }
        }
    }

    fun refreshWeatherByCoordinates(latitude: Double, longitude: Double) {
        loadWeatherByCoordinates(latitude, longitude)
    }

    fun refreshWeatherByIpOrFallback(fallbackCity: String = "Minsk") {
        viewModelScope.launch {
            _isLoadingWeather.value = true
            try {
                val result = weatherRepository.getWeatherByIp()
                result.onSuccess { weatherData ->
                    _weather.value = weatherData
                    _weatherError.value =
                        if (weatherRepository.isNetworkAvailable()) null
                        else weatherRepository.getOfflineUserMessage()
                }.onFailure {
                    loadWeather(fallbackCity)
                }
            } catch (_: Exception) {
                loadWeather(fallbackCity)
            } finally {
                _isLoadingWeather.value = false
            }
        }
    }

    fun loadScenarios() {
        viewModelScope.launch {
            try {
                allScenarios = repository.getAllScenarios()
                applySearchFilterSort()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim()
        applySearchFilterSort()
    }

    fun setRoomFilter(roomKey: String?) {
        selectedRoomKey = roomKey
        applySearchFilterSort()
    }

    fun setTemperatureFilter(range: IntRange?) {
        temperatureRange = range
        applySearchFilterSort()
    }

    fun setSortMode(mode: SortMode) {
        sortMode = mode
        applySearchFilterSort()
    }

    fun getSortMode(): SortMode = sortMode

    fun getRoomFilter(): String? = selectedRoomKey

    fun getTemperatureFilter(): IntRange? = temperatureRange

    fun hasActiveFiltersOrSearch(): Boolean {
        return searchQuery.isNotBlank() || selectedRoomKey != null || temperatureRange != null
    }

    fun addScenario(scenario: Scenario) {
        viewModelScope.launch {
            try {
                repository.saveScenario(scenario)
                loadScenarios()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateScenario(scenario: Scenario) {
        viewModelScope.launch {
            try {
                repository.updateScenario(scenario)
                loadScenarios()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteScenario(scenarioId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteScenario(scenarioId)
                loadScenarios()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearWeatherError() {
        _weatherError.value = null
    }

    fun reportNetworkLost(message: String) {
        _weatherError.value = message
    }

    private fun applySearchFilterSort() {
        val roomFilter = selectedRoomKey
        val tempFilter = temperatureRange
        val query = searchQuery

        val baseFiltered = allScenarios.asSequence()
            .filter { scenario ->
                roomFilter == null || scenario.rooms.contains(roomFilter)
            }
            .filter { scenario ->
                tempFilter == null || scenario.temperature in tempFilter
            }
            .toList()

        val result = if (query.isBlank()) {
            baseFiltered.sortedWith(sortComparator(sortMode))
        } else {
            baseFiltered
                .map { scenario ->
                    val score = FuzzySearch.bestScore(
                        query = query,
                        candidates = listOf(
                            scenario.name,
                            scenario.rooms.joinToString(" ")
                        )
                    )
                    scenario to score
                }
                .filter { (_, score) -> score >= FuzzySearch.DEFAULT_THRESHOLD }
                .sortedWith(
                    compareByDescending<Pair<Scenario, Double>> { it.second }
                        .thenComparator { a, b ->
                            sortComparator(sortMode).compare(a.first, b.first)
                        }
                )
                .map { it.first }
        }

        _scenarios.value = result
    }

    private fun sortComparator(mode: SortMode): Comparator<Scenario> {
        return when (mode) {
            SortMode.DATE_DESC -> compareByDescending { it.id }
            SortMode.DATE_ASC -> compareBy { it.id }
            SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
            SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortMode.TEMPERATURE_ASC -> compareBy { it.temperature }
            SortMode.TEMPERATURE_DESC -> compareByDescending { it.temperature }
        }
    }
}