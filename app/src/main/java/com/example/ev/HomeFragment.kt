package com.example.ev

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ev.data.ScenarioRepository
import com.example.ev.data.WeatherRepository
import com.example.ev.network.NetworkConnectivity
import com.example.ev.notifications.ScenarioScheduleManager
import com.example.ev.viewmodel.HomeViewModel
import com.example.ev.viewmodel.HomeViewModelFactory
import com.example.ev.viewmodel.HomeViewModel.SortMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class HomeFragment : Fragment() {
    companion object {
        private const val FALLBACK_CITY = "Minsk"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var addScenarioButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var searchInput: EditText
    private lateinit var filterButton: ImageButton
    private lateinit var scenarioAdapter: ScenarioAdapter
    private lateinit var viewModel: HomeViewModel
    private lateinit var sortModes: List<SortMode>
    private lateinit var roomFilterKeys: List<String?>
    private lateinit var tempRanges: List<IntRange?>
    private lateinit var sortLabels: List<String>
    private lateinit var roomLabels: List<String>
    private lateinit var tempLabels: List<String>

    private lateinit var weatherContainer: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherLocationText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var conditionText: TextView
    private lateinit var weatherDetailsText: TextView
    private lateinit var weatherLoadingText: TextView
    private lateinit var weatherErrorText: TextView
    private lateinit var refreshWeatherButton: Button
    private var useDeviceLocation: Boolean = true
    private var selectedCityOverride: String? = null
    private var selectedCoordinatesOverride: Pair<Double, Double>? = null

    private val requestLocationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (fine || coarse) {
                fetchLocationAndLoadWeather()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.location_permission_required),
                    Toast.LENGTH_LONG
                ).show()
                switchToFallbackCityWeather()
            }
        }

    private var pendingLocationCallback: LocationCallback? = null

    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkAvailable: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.scenariosRecyclerView)
        addScenarioButton = view.findViewById(R.id.addScenarioButton)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        searchInput = view.findViewById(R.id.searchInput)
        filterButton = view.findViewById(R.id.filterButton)

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
        setupDataToolsControls()
        setupObservers()
        setupAddButton()
        setupLocationPicker()
        setupRefreshButton()

        return view
    }

    override fun onDestroyView() {
        cancelPendingLocationUpdates()
        super.onDestroyView()
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

    private fun setupDataToolsControls() {
        setupSearchInput()
        initFilterOptions()
        setupFilterButton()
    }

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
        })
    }

    private fun initFilterOptions() {
        sortModes = listOf(
            SortMode.DATE_DESC,
            SortMode.DATE_ASC,
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.TEMPERATURE_ASC,
            SortMode.TEMPERATURE_DESC
        )

        sortLabels = listOf(
            getString(R.string.sort_date_newest),
            getString(R.string.sort_date_oldest),
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_temp_asc),
            getString(R.string.sort_temp_desc)
        )

        val roomOptions = RoomMapper.getAvailableRooms(requireContext())
        roomFilterKeys = listOf(null) + roomOptions.map { it.first }
        roomLabels = listOf(getString(R.string.filter_all_rooms)) + roomOptions.map { it.second }

        tempRanges = listOf(
            null,
            16..20,
            21..24,
            25..30
        )
        tempLabels = listOf(
            getString(R.string.filter_all_temperatures),
            getString(R.string.temp_range_cool),
            getString(R.string.temp_range_comfort),
            getString(R.string.temp_range_warm)
        )
    }

    private fun setupFilterButton() {
        filterButton.setOnClickListener {
            showFiltersDialog()
        }
    }

    private fun showFiltersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scenario_filters, null)
        val dialogSortSpinner = dialogView.findViewById<Spinner>(R.id.dialogSortSpinner)
        val dialogRoomSpinner = dialogView.findViewById<Spinner>(R.id.dialogRoomSpinner)
        val dialogTempSpinner = dialogView.findViewById<Spinner>(R.id.dialogTempSpinner)
        val applyButton = dialogView.findViewById<Button>(R.id.applyFiltersButton)
        val resetButton = dialogView.findViewById<Button>(R.id.resetFiltersButton)

        dialogSortSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        dialogRoomSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roomLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        dialogTempSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tempLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        dialogSortSpinner.setSelection(sortModes.indexOf(viewModel.getSortMode()).coerceAtLeast(0))
        dialogRoomSpinner.setSelection(roomFilterKeys.indexOf(viewModel.getRoomFilter()).coerceAtLeast(0))
        dialogTempSpinner.setSelection(tempRanges.indexOf(viewModel.getTemperatureFilter()).coerceAtLeast(0))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.filters_dialog_title))
            .setView(dialogView)
            .create()

        applyButton.setOnClickListener {
            viewModel.setSortMode(sortModes[dialogSortSpinner.selectedItemPosition])
            viewModel.setRoomFilter(roomFilterKeys[dialogRoomSpinner.selectedItemPosition])
            viewModel.setTemperatureFilter(tempRanges[dialogTempSpinner.selectedItemPosition])
            dialog.dismiss()
        }

        resetButton.setOnClickListener {
            dialogSortSpinner.setSelection(0)
            dialogRoomSpinner.setSelection(0)
            dialogTempSpinner.setSelection(0)
            viewModel.setSortMode(SortMode.DATE_DESC)
            viewModel.setRoomFilter(null)
            viewModel.setTemperatureFilter(null)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupObservers() {
        viewModel.scenarios.observe(viewLifecycleOwner) { scenarios ->
            scenarioAdapter.updateScenarios(scenarios)
            updateEmptyState(scenarios.isEmpty(), viewModel.hasActiveFiltersOrSearch())
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

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

    private fun resolvedWeatherPlaceLabel(storedCityName: String): String {
        val t = storedCityName.trim()
        if (t.isEmpty() || WeatherRepository.isGenericDeviceLocationStoredLabel(t)) {
            return getString(R.string.weather_city_current_location)
        }
        return t
    }

    private fun updateWeatherUI(weather: com.example.ev.data.weather.WeatherData) {
        weatherContainer.isVisible = true
        weatherLoadingText.isVisible = false

        weatherLocationText.text = when {
            useDeviceLocation -> resolvedWeatherPlaceLabel(weather.cityName)
            selectedCoordinatesOverride != null && !selectedCityOverride.isNullOrBlank() ->
                selectedCityOverride!!
            else -> resolvedWeatherPlaceLabel(weather.cityName)
        }
        temperatureText.text = getString(R.string.temperature_format, weather.temperature.toInt())

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
            if (useDeviceLocation) {
                requestLocationAndRefresh()
            } else {
                val selectedCoordinates = selectedCoordinatesOverride
                if (selectedCoordinates != null) {
                    viewModel.refreshWeatherByCoordinates(selectedCoordinates.first, selectedCoordinates.second)
                } else {
                    val selectedCity = selectedCityOverride
                    viewModel.refreshWeather(selectedCity ?: FALLBACK_CITY)
                }
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
                53.9045 to 27.5615,
                52.0976 to 23.7341,
                53.6694 to 23.8131,
                55.1904 to 30.2049,
                53.9006 to 30.3317
            )
            val options = arrayOf(
                getString(R.string.weather_city_current_location),
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
                        0 -> {
                            useDeviceLocation = true
                            selectedCityOverride = null
                            requestLocationAndRefresh()
                        }
                        in 1..5 -> {
                            useDeviceLocation = false
                            val idx = which - 1
                            selectedCoordinatesOverride = cityCoordinates[idx]
                            selectedCityOverride = getString(
                                R.string.weather_city_with_country_format,
                                cityLabels[idx]
                            )
                            viewModel.refreshWeatherByCoordinates(
                                cityCoordinates[idx].first,
                                cityCoordinates[idx].second
                            )
                        }
                        6 -> showCustomCityDialog()
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
                    useDeviceLocation = false
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
            weatherLocationText.text = if (useDeviceLocation) {
                getString(R.string.weather_city_current_location)
            } else {
                getString(R.string.weather_city_minsk)
            }
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
        if (useDeviceLocation) {
            requestLocationAndRefresh()
        } else {
            val selectedCoordinates = selectedCoordinatesOverride
            if (selectedCoordinates != null) {
                viewModel.refreshWeatherByCoordinates(selectedCoordinates.first, selectedCoordinates.second)
            } else {
                val selectedCity = selectedCityOverride
                viewModel.refreshWeather(selectedCity ?: FALLBACK_CITY)
            }
        }
    }

    private fun requestLocationAndRefresh() {
        if (!useDeviceLocation) {
            refreshWeatherForManualSelection()
            return
        }
        val ctx = requireContext()
        when {
            hasLocationPermission(ctx) -> fetchLocationAndLoadWeather()
            else -> requestLocationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isDeviceLocationEnabled(): Boolean {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun cancelPendingLocationUpdates() {
        val cb = pendingLocationCallback ?: return
        pendingLocationCallback = null
        runCatching {
            LocationServices.getFusedLocationProviderClient(requireContext().applicationContext)
                .removeLocationUpdates(cb)
        }
    }

    private fun refreshWeatherForManualSelection() {
        val selectedCoordinates = selectedCoordinatesOverride
        if (selectedCoordinates != null) {
            viewModel.refreshWeatherByCoordinates(selectedCoordinates.first, selectedCoordinates.second)
        } else {
            viewModel.refreshWeather(selectedCityOverride ?: FALLBACK_CITY)
        }
    }

    private fun fetchLocationAndLoadWeather() {
        if (!isAdded) return
        val ctx = requireContext()
        cancelPendingLocationUpdates()

        if (!isDeviceLocationEnabled()) {
            Toast.makeText(ctx, getString(R.string.location_services_disabled), Toast.LENGTH_LONG).show()
            switchToFallbackCityWeather()
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(ctx)

        fun onLocationFailed() {
            if (!isAdded) return
            Toast.makeText(ctx, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            switchToFallbackCityWeather()
        }

        fun applyFromLocation(loc: Location) {
            if (!isAdded) return
            cancelPendingLocationUpdates()
            applyDeviceLocationWeather(loc.latitude, loc.longitude)
        }

        client.lastLocation
            .addOnSuccessListener { loc ->
                if (!isAdded) return@addOnSuccessListener
                if (loc != null) {
                    applyFromLocation(loc)
                } else {
                    requestFreshCurrentLocation(client, { applyFromLocation(it) }) {
                        requestSingleLocationUpdate(client, { applyFromLocation(it) }, ::onLocationFailed)
                    }
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                requestFreshCurrentLocation(client, { applyFromLocation(it) }) {
                    requestSingleLocationUpdate(client, { applyFromLocation(it) }, ::onLocationFailed)
                }
            }
    }

    private fun requestFreshCurrentLocation(
        client: FusedLocationProviderClient,
        onSuccess: (Location) -> Unit,
        onStillNullOrError: () -> Unit
    ) {
        if (!isAdded) return
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { current ->
                if (!isAdded) return@addOnSuccessListener
                if (current != null) onSuccess(current)
                else onStillNullOrError()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                onStillNullOrError()
            }
    }

    private fun requestSingleLocationUpdate(
        client: FusedLocationProviderClient,
        onSuccess: (Location) -> Unit,
        onFailed: () -> Unit
    ) {
        if (!isAdded) return
        cancelPendingLocationUpdates()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()

        var completed = false
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (completed || !isAdded) return
                val loc = result.lastLocation
                if (loc != null) {
                    completed = true
                    cancelPendingLocationUpdates()
                    onSuccess(loc)
                }
            }
        }
        pendingLocationCallback = callback
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            pendingLocationCallback = null
            if (isAdded) onFailed()
            return
        }

        view?.postDelayed({
            if (!completed && isAdded) {
                completed = true
                cancelPendingLocationUpdates()
                onFailed()
            }
        }, 25_000L)
    }

    private fun switchToFallbackCityWeather() {
        useDeviceLocation = false
        selectedCoordinatesOverride = null
        selectedCityOverride = FALLBACK_CITY
        viewModel.refreshWeather(FALLBACK_CITY)
    }

    private fun applyDeviceLocationWeather(latitude: Double, longitude: Double) {
        useDeviceLocation = true
        selectedCoordinatesOverride = null
        selectedCityOverride = null
        viewModel.refreshWeatherByCoordinates(latitude, longitude)
    }

    private fun setupAddButton() {
        addScenarioButton.setOnClickListener {
            val dialog = AddScenarioDialog()
            dialog.setOnScenarioAddedListener(object : AddScenarioDialog.OnScenarioAddedListener {
                override fun onScenarioAdded(scenario: Scenario) {
                    viewModel.addScenario(scenario)
                    ScenarioScheduleManager.scheduleScenario(requireContext(), scenario)
                }
            })
            dialog.show(parentFragmentManager, "AddScenarioDialog")
        }
    }

    private fun showEditScenarioDialog(scenario: Scenario) {
        val options = arrayOf(
            getString(R.string.edit),
            getString(R.string.share),
            getString(R.string.delete),
            getString(R.string.cancel)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(scenario.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editScenario(scenario)
                    1 -> ScenarioShare.share(requireContext(), scenario)
                    2 -> deleteScenario(scenario)
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
                ScenarioScheduleManager.scheduleScenario(requireContext(), updatedScenario)
                Toast.makeText(requireContext(), getString(R.string.scenario_updated), Toast.LENGTH_SHORT).show()
            }

            override fun onScenarioDeleted(deletedScenario: Scenario) {}
        })
        dialog.show(parentFragmentManager, "EditScenarioDialog")
    }

    private fun deleteScenario(scenario: Scenario) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_scenario_title))
            .setMessage(getString(R.string.delete_scenario_message, scenario.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                ScenarioScheduleManager.cancelScenario(requireContext(), scenario.id)
                viewModel.deleteScenario(scenario.id)
                Toast.makeText(requireContext(), getString(R.string.scenario_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateEmptyState(isEmpty: Boolean, hasActiveFilters: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = if (hasActiveFilters) {
                getString(R.string.no_scenarios_found)
            } else {
                getString(R.string.no_scenarios_yet)
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
    }
}