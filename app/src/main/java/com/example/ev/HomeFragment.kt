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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ev.data.ScenarioRepository
import com.example.ev.viewmodel.HomeViewModel
import com.example.ev.viewmodel.HomeViewModelFactory

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addScenarioButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var scenarioAdapter: ScenarioAdapter
    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.scenariosRecyclerView)
        addScenarioButton = view.findViewById(R.id.addScenarioButton)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        setupViewModel()
        setupRecyclerView()
        setupObservers()
        setupAddButton()

        return view
    }

    private fun setupViewModel() {
        val repository = ScenarioRepository(requireContext())
        val factory = HomeViewModelFactory(repository)
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

            override fun onScenarioDeleted(scenario: Scenario) {
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