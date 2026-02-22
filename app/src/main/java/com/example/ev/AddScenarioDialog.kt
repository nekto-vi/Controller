package com.example.ev

import android.app.Dialog
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

class AddScenarioDialog : DialogFragment() {

    private var _binding: ScenarioAddBinding? = null
    private val binding get() = _binding!!

    private val availableRooms = listOf("Living Room", "Bedroom", "Kitchen", "Bathroom", "Hall")
    private val selectedRooms = mutableListOf<String>()
    private lateinit var roomAdapter: RoomAdapter
    private var currentTemperature = 22

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

        setupRoomSpinner()
        setupSelectedRoomsRecyclerView()
        setupTemperatureControls()
        setupButtons()
        setupKeyboardHiding()
        updateEmptyState()
    }

    private fun setupRoomSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableRooms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.roomSpinner.adapter = adapter
    }

    private fun setupSelectedRoomsRecyclerView() {
        roomAdapter = RoomAdapter(selectedRooms) { roomToRemove ->
            val position = selectedRooms.indexOf(roomToRemove)
            if (position != -1) {
                selectedRooms.removeAt(position)
                roomAdapter.notifyItemRemoved(position)
                updateEmptyState()
            }
        }

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

    private fun setupButtons() {
        binding.addRoomButton.setOnClickListener {
            hideKeyboard()

            val selectedItem = binding.roomSpinner.selectedItem
            if (selectedItem == null) {
                Toast.makeText(requireContext(), "Please select a room first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRoom = selectedItem.toString()

            if (selectedRoom.isBlank()) {
                Toast.makeText(requireContext(), "Invalid room selection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!selectedRooms.contains(selectedRoom)) {
                selectedRooms.add(selectedRoom)
                roomAdapter.notifyItemInserted(selectedRooms.size - 1)
                updateEmptyState()
                binding.roomSpinner.setSelection(0)
            } else {
                Toast.makeText(requireContext(), "Room already selected", Toast.LENGTH_SHORT).show()
            }
        }

        binding.addScenarioButton.setOnClickListener {
            hideKeyboard()

            val scenarioName = binding.scenarioNameInput.text.toString().trim()

            when {
                scenarioName.isEmpty() -> {
                    Toast.makeText(requireContext(), "Please enter scenario name", Toast.LENGTH_SHORT).show()
                }
                selectedRooms.isEmpty() -> {
                    Toast.makeText(requireContext(), "Please select at least one room", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val scenario = Scenario(
                        name = scenarioName,
                        rooms = selectedRooms.toList(),
                        temperature = currentTemperature
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
        if (selectedRooms.isEmpty()) {
            binding.selectedRoomsRecyclerView.visibility = View.GONE
        } else {
            binding.selectedRoomsRecyclerView.visibility = View.VISIBLE
        }
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