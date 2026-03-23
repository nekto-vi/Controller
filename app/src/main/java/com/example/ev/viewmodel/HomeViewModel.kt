package com.example.ev.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ev.Scenario
import com.example.ev.data.ScenarioRepository
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ScenarioRepository) : ViewModel() {

    private val _scenarios = MutableLiveData<List<Scenario>>(emptyList())
    val scenarios: LiveData<List<Scenario>> = _scenarios

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    init {
        loadScenarios()
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
}