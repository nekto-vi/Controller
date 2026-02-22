package com.example.ev

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addScenarioButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var scenarioAdapter: ScenarioAdapter

    private val scenarios = mutableListOf<Scenario>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.scenariosRecyclerView)
        addScenarioButton = view.findViewById(R.id.addScenarioButton)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        setupRecyclerView()
        setupAddButton()
        loadScenarios()

        return view
    }

    private fun setupRecyclerView() {
        scenarioAdapter = ScenarioAdapter(scenarios) { scenario ->
            showEditScenarioDialog(scenario)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = scenarioAdapter
    }

    private fun setupAddButton() {
        addScenarioButton.setOnClickListener {
            val dialog = AddScenarioDialog()
            dialog.setOnScenarioAddedListener(object : AddScenarioDialog.OnScenarioAddedListener {
                override fun onScenarioAdded(scenario: Scenario) {
                    scenarios.add(scenario)
                    scenarioAdapter.updateScenarios(scenarios.toList())
                    saveScenarios()
                    updateEmptyState()
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
                val position = scenarios.indexOfFirst { it.id == updatedScenario.id }
                if (position != -1) {
                    scenarios[position] = updatedScenario
                    scenarioAdapter.updateScenarios(scenarios.toList())
                    saveScenarios()
                    Toast.makeText(requireContext(), getString(R.string.scenario_updated), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onScenarioDeleted(scenario: Scenario) {
            }
        })
        dialog.show(parentFragmentManager, "EditScenarioDialog")
    }

    private fun deleteScenario(scenario: Scenario) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_scenario_title))
            .setMessage(getString(R.string.delete_scenario_message, scenario.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                scenarios.removeAll { it.id == scenario.id }
                scenarioAdapter.updateScenarios(scenarios.toList())
                saveScenarios()
                updateEmptyState()
                Toast.makeText(requireContext(), getString(R.string.scenario_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateEmptyState() {
        if (scenarios.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = getString(R.string.no_scenarios_yet)
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
    }

    private fun loadScenarios() {
        val prefs = requireContext().getSharedPreferences("scenarios", android.content.Context.MODE_PRIVATE)
        val savedScenarios = prefs.getStringSet("scenario_ids", setOf()) ?: setOf()

        scenarios.clear()
        for (id in savedScenarios) {
            val name = prefs.getString("scenario_${id}_name", "") ?: ""
            val roomKeys = prefs.getStringSet("scenario_${id}_rooms", setOf())?.toList() ?: listOf()
            val temp = prefs.getInt("scenario_${id}_temp", 22)
            if (name.isNotEmpty()) {
                scenarios.add(Scenario(id = id.toLong(), name = name, rooms = roomKeys, temperature = temp))
            }
        }

        scenarioAdapter.updateScenarios(scenarios.toList())
        updateEmptyState()
    }

    private fun saveScenarios() {
        val prefs = requireContext().getSharedPreferences("scenarios", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.clear()

        val ids = scenarios.map { it.id.toString() }.toSet()
        editor.putStringSet("scenario_ids", ids)

        for (scenario in scenarios) {
            editor.putString("scenario_${scenario.id}_name", scenario.name)
            editor.putStringSet("scenario_${scenario.id}_rooms", scenario.rooms.toSet())
            editor.putInt("scenario_${scenario.id}_temp", scenario.temperature)
        }

        editor.apply()
    }
}