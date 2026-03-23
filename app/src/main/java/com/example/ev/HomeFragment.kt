package com.example.ev

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ev.data.ScenarioRepository
import com.example.ev.data.WeatherRepository
import com.example.ev.viewmodel.HomeViewModel
import com.example.ev.viewmodel.HomeViewModelFactory
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addScenarioButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var scenarioAdapter: ScenarioAdapter
    private lateinit var viewModel: HomeViewModel

    // Weather UI elements
    private lateinit var weatherContainer: View
    private lateinit var weatherIcon: ImageView
    private lateinit var temperatureText: TextView
    private lateinit var conditionText: TextView
    private lateinit var weatherDetailsText: TextView
    private lateinit var weatherLoadingText: TextView
    private lateinit var weatherErrorText: TextView
    private lateinit var refreshWeatherButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views
        recyclerView = view.findViewById(R.id.scenariosRecyclerView)
        addScenarioButton = view.findViewById(R.id.addScenarioButton)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        // Weather views
        weatherContainer = view.findViewById(R.id.weatherContainer)
        weatherIcon = view.findViewById(R.id.weatherIcon)
        temperatureText = view.findViewById(R.id.temperatureText)
        conditionText = view.findViewById(R.id.conditionText)
        weatherDetailsText = view.findViewById(R.id.weatherDetailsText)
        weatherLoadingText = view.findViewById(R.id.weatherLoadingText)
        weatherErrorText = view.findViewById(R.id.weatherErrorText)
        refreshWeatherButton = view.findViewById(R.id.refreshWeatherButton)

        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupAddButton()
        setupRefreshButton()

        return view
    }

    private fun setupViewModel() {
        val repository = ScenarioRepository(requireContext())
        val weatherRepository = WeatherRepository(requireContext())
        val factory = HomeViewModelFactory(requireContext(), repository, weatherRepository)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    private fun setupRecyclerView() {
        scenarioAdapter = ScenarioAdapter(emptyList()) { scenario ->
            showEditScenarioDialog(scenario)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = scenarioAdapter
    }

    private fun setupObservers() {
        viewModel.scenarios.observe(viewLifecycleOwner) { scenarios ->
            scenarioAdapter.updateScenarios(scenarios)
            updateEmptyState(scenarios.isEmpty())
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        // Weather observers
        viewModel.weather.observe(viewLifecycleOwner) { weatherData ->
            weatherData?.let {
                updateWeatherUI(it)
            }
        }

        viewModel.isLoadingWeather.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showWeatherLoading()
            }
        }

        viewModel.weatherError.observe(viewLifecycleOwner) { error ->
            if (error != null && viewModel.weather.value == null) {
                showWeatherError(error)
            }
        }
    }

    private fun updateWeatherUI(weather: com.example.ev.data.weather.WeatherData) {
        weatherContainer.isVisible = true
        weatherLoadingText.isVisible = false
        weatherErrorText.isVisible = false

        temperatureText.text = getString(R.string.temperature_format, weather.temperature.toInt())

        // Исправляем deprecated capitalize()
        val condition = weather.description.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        conditionText.text = condition

        weatherDetailsText.text = getString(R.string.weather_details_format, weather.humidity, weather.windSpeed.toInt())

        // Load weather icon using Glide
        try {
            val iconUrl = "https://openweathermap.org/img/wn/${weather.iconCode}@2x.png"
            // Временно используем просто установку иконки из ресурсов, если Glide не работает
            // Glide.with(this).load(iconUrl).into(weatherIcon)
            // Пока закомментируем Glide, чтобы приложение запустилось
            weatherIcon.setImageResource(android.R.drawable.ic_dialog_info)
        } catch (e: Exception) {
            weatherIcon.setImageResource(android.R.drawable.ic_dialog_info)
        }
    }

    private fun showWeatherLoading() {
        weatherContainer.isVisible = false
        weatherLoadingText.isVisible = true
        weatherErrorText.isVisible = false
    }

    private fun showWeatherError(error: String) {
        weatherContainer.isVisible = false
        weatherLoadingText.isVisible = false
        weatherErrorText.isVisible = true
        weatherErrorText.text = getString(R.string.weather_error_format, error)
    }

    private fun setupRefreshButton() {
        refreshWeatherButton.setOnClickListener {
            viewModel.refreshWeather()
            Toast.makeText(requireContext(), getString(R.string.refreshing_weather), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAddButton() {
        addScenarioButton.setOnClickListener {
            val dialog = AddScenarioDialog()
            dialog.setOnScenarioAddedListener(object : AddScenarioDialog.OnScenarioAddedListener {
                override fun onScenarioAdded(scenario: Scenario) {
                    viewModel.addScenario(scenario)
                }
            })
            dialog.show(parentFragmentManager, "AddScenarioDialog")
        }
    }

    private fun showEditScenarioDialog(scenario: Scenario) {
        val options = arrayOf(
            getString(R.string.edit),
            getString(R.string.delete),
            getString(R.string.cancel)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(scenario.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editScenario(scenario)
                    1 -> deleteScenario(scenario)
                }
            }
            .show()
    }

    private fun editScenario(scenario: Scenario) {
        val dialog = EditScenarioDialog()
        dialog.setScenario(scenario)
        dialog.setOnScenarioEditedListener(object : EditScenarioDialog.OnScenarioEditedListener {
            override fun onScenarioEdited(updatedScenario: Scenario) {
                viewModel.updateScenario(updatedScenario)
                Toast.makeText(requireContext(), getString(R.string.scenario_updated), Toast.LENGTH_SHORT).show()
            }

            override fun onScenarioDeleted(deletedScenario: Scenario) {
                // Not used in edit flow
            }
        })
        dialog.show(parentFragmentManager, "EditScenarioDialog")
    }

    private fun deleteScenario(scenario: Scenario) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_scenario_title))
            .setMessage(getString(R.string.delete_scenario_message, scenario.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteScenario(scenario.id)
                Toast.makeText(requireContext(), getString(R.string.scenario_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = getString(R.string.no_scenarios_yet)
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
    }
}