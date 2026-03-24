package com.example.ev

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Button
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
import com.example.ev.network.NetworkConnectivity
import com.example.ev.viewmodel.HomeViewModel
import com.example.ev.viewmodel.HomeViewModelFactory
import java.util.Locale

class HomeFragment : Fragment() {
    companion object {
        private const val FALLBACK_CITY = "Minsk"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var addScenarioButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var scenarioAdapter: ScenarioAdapter
    private lateinit var viewModel: HomeViewModel

    // Weather UI elements
    private lateinit var weatherContainer: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherLocationText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var conditionText: TextView
    private lateinit var weatherDetailsText: TextView
    private lateinit var weatherLoadingText: TextView
    private lateinit var weatherErrorText: TextView
    private lateinit var refreshWeatherButton: Button
    private var selectedCityOverride: String? = FALLBACK_CITY
    private var selectedCoordinatesOverride: Pair<Double, Double>? = null

    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    /** null — ещё не инициализировали; false/true — последнее известное состояние сети */
    private var lastNetworkAvailable: Boolean? = null

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
        weatherLocationText = view.findViewById(R.id.weatherLocationText)
        temperatureText = view.findViewById(R.id.temperatureText)
        conditionText = view.findViewById(R.id.conditionText)
        weatherDetailsText = view.findViewById(R.id.weatherDetailsText)
        weatherLoadingText = view.findViewById(R.id.weatherLoadingText)
        weatherErrorText = view.findViewById(R.id.weatherErrorText)
        refreshWeatherButton = view.findViewById(R.id.refreshWeatherButton)
        setWeatherPlaceholders()

        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupAddButton()
        setupLocationPicker()
        setupRefreshButton()

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshWeatherForCurrentSelection()
    }

    override fun onStart() {
        super.onStart()
        registerConnectivityCallback()
    }

    override fun onStop() {
        unregisterConnectivityCallback()
        super.onStop()
    }

    private fun registerConnectivityCallback() {
        if (connectivityCallback != null) return
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = lastNetworkAvailable == false
                lastNetworkAvailable = true
                view?.post {
                    if (!isAdded) return@post
                    viewModel.clearWeatherError()
                    weatherErrorText.isVisible = false
                    if (wasOffline) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.weather_back_online_refreshing),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshWeatherForCurrentSelection()
                    }
                }
            }

            override fun onLost(network: Network) {
                lastNetworkAvailable = false
                view?.post {
                    if (!isAdded) return@post
                    val msg = getString(R.string.weather_offline_no_data)
                    viewModel.reportNetworkLost(msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
        connectivityCallback = callback
        cm.registerDefaultNetworkCallback(callback)

        lastNetworkAvailable = NetworkConnectivity.isNetworkAvailable(requireContext())
        if (lastNetworkAvailable == false) {
            viewModel.reportNetworkLost(getString(R.string.weather_offline_no_data))
        }
    }

    private fun unregisterConnectivityCallback() {
        val cb = connectivityCallback ?: return
        val ctx = context ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { cm.unregisterNetworkCallback(cb) }
        connectivityCallback = null
        lastNetworkAvailable = null
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
            if (error == null) {
                weatherErrorText.isVisible = false
                return@observe
            }
            weatherErrorText.text = error
            weatherErrorText.isVisible = true
            if (viewModel.weather.value == null) {
                setWeatherPlaceholders()
            }
        }
    }

    private fun updateWeatherUI(weather: com.example.ev.data.weather.WeatherData) {
        weatherContainer.isVisible = true
        weatherLoadingText.isVisible = false
        // Сообщение об офлайне (кэш без сети) управляется weatherError

        val selectedCoordinates = selectedCoordinatesOverride
        weatherLocationText.text = if (selectedCoordinates != null && !selectedCityOverride.isNullOrBlank()) {
            selectedCityOverride
        } else {
            weather.cityName
        }
        temperatureText.text = getString(R.string.temperature_format, weather.temperature.toInt())

        // Исправляем deprecated capitalize()
        val condition = weather.description.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        conditionText.text = condition

        weatherDetailsText.text = getString(R.string.weather_details_format, weather.humidity, weather.windSpeed.toInt())

        weatherIcon.text = mapWeatherCodeToEmoji(weather.iconCode)
    }

    private fun showWeatherLoading() {
        weatherContainer.isVisible = true
        weatherLoadingText.isVisible = true
        weatherErrorText.isVisible = false
    }

    private fun setupRefreshButton() {
        refreshWeatherButton.setOnClickListener {
            val selectedCoordinates = selectedCoordinatesOverride
            if (selectedCoordinates != null) {
                viewModel.refreshWeatherByCoordinates(selectedCoordinates.first, selectedCoordinates.second)
            } else {
                val selectedCity = selectedCityOverride
                viewModel.refreshWeather(selectedCity ?: FALLBACK_CITY)
            }
            Toast.makeText(requireContext(), getString(R.string.refreshing_weather), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLocationPicker() {
        weatherLocationText.setOnClickListener {
            val cityLabels = arrayOf(
                getString(R.string.weather_city_minsk),
                getString(R.string.weather_city_brest),
                getString(R.string.weather_city_grodno),
                getString(R.string.weather_city_vitebsk),
                getString(R.string.weather_city_mogilev)
            )
            val cityCoordinates = arrayOf(
                53.9045 to 27.5615, // Minsk
                52.0976 to 23.7341, // Brest
                53.6694 to 23.8131, // Grodno
                55.1904 to 30.2049, // Vitebsk
                53.9006 to 30.3317  // Mogilev
            )
            val options = arrayOf(
                cityLabels[0],
                cityLabels[1],
                cityLabels[2],
                cityLabels[3],
                cityLabels[4],
                getString(R.string.weather_city_other)
            )

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.weather_choose_city_title))
                .setItems(options) { _, which ->
                    when (which) {
                        0, 1, 2, 3, 4 -> {
                            selectedCoordinatesOverride = cityCoordinates[which]
                            selectedCityOverride = getString(
                                R.string.weather_city_with_country_format,
                                cityLabels[which]
                            )
                            viewModel.refreshWeatherByCoordinates(
                                cityCoordinates[which].first,
                                cityCoordinates[which].second
                            )
                        }
                        5 -> showCustomCityDialog()
                    }
                }
                .show()
        }
    }

    private fun showCustomCityDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.weather_city_input_hint)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.weather_choose_city_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) {
                    selectedCoordinatesOverride = null
                    selectedCityOverride = city
                    viewModel.refreshWeather(city)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setWeatherPlaceholders() {
        if (weatherLocationText.text.isNullOrBlank() || weatherLocationText.text == "--") {
            weatherLocationText.text = getString(R.string.weather_city_minsk)
        }
        temperatureText.text = "--°C"
        conditionText.text = "--"
        weatherDetailsText.text = getString(R.string.weather_details_placeholder)
        weatherIcon.text = "☁️"
    }

    private fun mapWeatherCodeToEmoji(iconCode: String): String {
        val code = iconCode.toIntOrNull() ?: return "☁️"
        return when (code) {
            0 -> "☀️"
            1 -> "🌤️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55, 56, 57 -> "🌦️"
            61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "🌨️"
            95, 96, 99 -> "⛈️"
            else -> "☁️"
        }
    }

    private fun refreshWeatherForCurrentSelection() {
        val selectedCoordinates = selectedCoordinatesOverride
        if (selectedCoordinates != null) {
            viewModel.refreshWeatherByCoordinates(selectedCoordinates.first, selectedCoordinates.second)
        } else {
            val selectedCity = selectedCityOverride
            viewModel.refreshWeather(selectedCity ?: FALLBACK_CITY)
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