package com.example.ev

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ev.databinding.ScenarioAddBinding
import java.util.Locale

class AddScenarioDialog : DialogFragment() {

    private var _binding: ScenarioAddBinding? = null
    private val binding get() = _binding!!

    private lateinit var roomOptions: List<Pair<String, String>>
    private val selectedRoomKeys = mutableListOf<String>()
    private lateinit var roomAdapter: RoomAdapter
    private var currentTemperature = 22
    private var scheduleEnabled = false
    private var selectedHour = 9
    private var selectedMinute = 0

    interface OnScenarioAddedListener {
        fun onScenarioAdded(scenario: Scenario)
    }

    private var listener: OnScenarioAddedListener? = null

    fun setOnScenarioAddedListener(listener: OnScenarioAddedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ScenarioAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        roomOptions = RoomMapper.getAvailableRooms(requireContext())

        setupRoomSpinner()
        setupSelectedRoomsRecyclerView()
        setupTemperatureControls()
        setupScheduleControls()
        setupButtons()
        setupKeyboardHiding()
        updateEmptyState()
        updateLocalizedTexts()
    }

    private fun updateLocalizedTexts() {
        binding.titleTextView.text = getString(R.string.add_scenario)
        binding.nameLabel.text = getString(R.string.scenario_name)
        binding.scenarioNameInput.hint = getString(R.string.scenario_name)
        binding.roomsLabel.text = getString(R.string.select_rooms)
        binding.selectedRoomsTitle.text = getString(R.string.selected_rooms)
        binding.temperatureLabel.text = getString(R.string.temperature)
        binding.scheduleSwitch.text = getString(R.string.enable_schedule)
        binding.scheduleTimeLabel.text = getString(R.string.schedule_time)
        binding.chooseTimeButton.text = getString(R.string.choose_time)
        binding.addRoomButton.text = getString(R.string.add)
        binding.addScenarioButton.text = getString(R.string.add_scenario)
    }

    private fun setupRoomSpinner() {
        val displayNames = roomOptions.map { it.second }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.roomSpinner.adapter = adapter
    }

    private fun setupSelectedRoomsRecyclerView() {
        roomAdapter = RoomAdapter(
            items = selectedRoomKeys,
            getDisplayName = { key -> RoomMapper.keyToDisplayName(requireContext(), key) },
            onRemoveClick = { keyToRemove ->
                val position = selectedRoomKeys.indexOf(keyToRemove)
                if (position != -1) {
                    selectedRoomKeys.removeAt(position)
                    roomAdapter.notifyItemRemoved(position)
                    updateEmptyState()
                }
            }
        )

        binding.selectedRoomsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.selectedRoomsRecyclerView.adapter = roomAdapter
    }

    private fun setupTemperatureControls() {
        updateTemperatureDisplay()

        binding.decreaseTempButton.setOnClickListener {
            if (currentTemperature > 16) {
                currentTemperature--
                updateTemperatureDisplay()
            }
        }

        binding.increaseTempButton.setOnClickListener {
            if (currentTemperature < 30) {
                currentTemperature++
                updateTemperatureDisplay()
            }
        }
    }

    private fun updateTemperatureDisplay() {
        binding.temperatureValue.text = "$currentTemperature°C"
    }

    private fun setupScheduleControls() {
        updateScheduleTimeDisplay()
        updateScheduleViewsState()

        binding.scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            scheduleEnabled = isChecked
            updateScheduleViewsState()
        }

        binding.chooseTimeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    updateScheduleTimeDisplay()
                },
                selectedHour,
                selectedMinute,
                true
            ).show()
        }
    }

    private fun updateScheduleViewsState() {
        binding.scheduleTimeLabel.isEnabled = scheduleEnabled
        binding.scheduleTimeValue.isEnabled = scheduleEnabled
        binding.chooseTimeButton.isEnabled = scheduleEnabled
    }

    private fun updateScheduleTimeDisplay() {
        binding.scheduleTimeValue.text = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            selectedHour,
            selectedMinute
        )
    }

    private fun setupButtons() {
        binding.addRoomButton.setOnClickListener {
            hideKeyboard()

            val selectedPosition = binding.roomSpinner.selectedItemPosition
            if (selectedPosition < 0 || selectedPosition >= roomOptions.size) {
                Toast.makeText(requireContext(), getString(R.string.select_room_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedKey = roomOptions[selectedPosition].first

            if (!selectedRoomKeys.contains(selectedKey)) {
                selectedRoomKeys.add(selectedKey)
                roomAdapter.notifyItemInserted(selectedRoomKeys.size - 1)
                updateEmptyState()
                binding.roomSpinner.setSelection(0)
            } else {
                Toast.makeText(requireContext(), getString(R.string.room_already_selected), Toast.LENGTH_SHORT).show()
            }
        }

        binding.addScenarioButton.setOnClickListener {
            hideKeyboard()

            val scenarioName = binding.scenarioNameInput.text.toString().trim()

            when {
                scenarioName.isEmpty() -> {
                    Toast.makeText(requireContext(), getString(R.string.enter_scenario_name), Toast.LENGTH_SHORT).show()
                }
                selectedRoomKeys.isEmpty() -> {
                    Toast.makeText(requireContext(), getString(R.string.select_at_least_one_room), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val scenario = Scenario(
                        name = scenarioName,
                        rooms = selectedRoomKeys.toList(), // Save keys, not display names
                        temperature = currentTemperature,
                        scheduleEnabled = scheduleEnabled,
                        startHour = selectedHour,
                        startMinute = selectedMinute
                    )

                    listener?.onScenarioAdded(scenario)
                    dismiss()
                }
            }
        }
    }

    private fun setupKeyboardHiding() {
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        binding.selectedRoomsRecyclerView.setOnTouchListener { _, _ ->
            hideKeyboard()
            false
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity?.currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        binding.scenarioNameInput.clearFocus()
    }

    private fun updateEmptyState() {
        if (selectedRoomKeys.isEmpty()) {
            binding.selectedRoomsRecyclerView.visibility = View.GONE
        } else {
            binding.selectedRoomsRecyclerView.visibility = View.VISIBLE
            val itemHeightDp = 48
            val maxHeightDp = 140
            val targetDp = (selectedRoomKeys.size * itemHeightDp).coerceAtMost(maxHeightDp)
            binding.selectedRoomsRecyclerView.layoutParams.height = dpToPx(targetDp)
            binding.selectedRoomsRecyclerView.requestLayout()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}