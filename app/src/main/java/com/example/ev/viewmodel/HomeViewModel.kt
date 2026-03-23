package com.example.ev.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ev.Scenario
import com.example.ev.data.ScenarioRepository
import com.example.ev.data.WeatherRepository
import com.example.ev.data.weather.WeatherData
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ScenarioRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _scenarios = MutableLiveData<List<Scenario>>(emptyList())
    val scenarios: LiveData<List<Scenario>> = _scenarios

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
        loadWeather()
    }

    fun loadWeather(city: String = "Moscow") {
        viewModelScope.launch {
            _isLoadingWeather.value = true
            try {
                val result = weatherRepository.getWeather(city)
                result.onSuccess { weatherData ->
                    _weather.value = weatherData
                    _weatherError.value = null
                }.onFailure { exception ->
                    _weatherError.value = exception.message ?: "Failed to load weather"
                    // Если есть кэшированные данные, они уже будут в _weather
                }
            } catch (e: Exception) {
                _weatherError.value = e.message
            } finally {
                _isLoadingWeather.value = false
            }
        }
    }

    fun refreshWeather(city: String = "Moscow") {
        loadWeather(city)
    }

    fun loadScenarios() {
        viewModelScope.launch {
            try {
                _scenarios.value = repository.getAllScenarios()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
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
}