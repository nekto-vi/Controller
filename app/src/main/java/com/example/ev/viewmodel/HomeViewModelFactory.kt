package com.example.ev.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ev.data.ScenarioRepository
import com.example.ev.data.WeatherRepository

class HomeViewModelFactory(
    private val context: Context,
    private val repository: ScenarioRepository,
    private val weatherRepository: WeatherRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository, weatherRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}