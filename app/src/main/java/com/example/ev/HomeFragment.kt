package com.example.ev

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
                    scenarioAdapter.updateScenarios(scenarios)
                    saveScenarios()
                    updateEmptyState()
                }
            })
            dialog.show(parentFragmentManager, "AddScenarioDialog")
        }
    }

    private fun updateEmptyState() {
        if (scenarios.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
    }

    private fun loadScenarios() {
        updateEmptyState()
    }

    private fun saveScenarios() {

    }
}