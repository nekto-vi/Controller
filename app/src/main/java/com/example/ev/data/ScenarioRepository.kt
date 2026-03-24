package com.example.ev.data

import android.content.Context
import android.content.SharedPreferences
import com.example.ev.Scenario

class ScenarioRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("scenarios", Context.MODE_PRIVATE)

    fun getAllScenarios(): List<Scenario> {
        val savedScenarios = prefs.getStringSet("scenario_ids", setOf()) ?: setOf()
        val scenarios = mutableListOf<Scenario>()

        for (id in savedScenarios) {
            val name = prefs.getString("scenario_${id}_name", "") ?: ""
            val roomKeys = prefs.getStringSet("scenario_${id}_rooms", setOf())?.toList() ?: listOf()
            val temp = prefs.getInt("scenario_${id}_temp", 22)
            if (name.isNotEmpty()) {
                scenarios.add(Scenario(id = id.toLong(), name = name, rooms = roomKeys, temperature = temp))
            }
        }
        return scenarios
    }

    fun saveScenario(scenario: Scenario) {
        val ids = prefs.getStringSet("scenario_ids", setOf())?.toMutableSet() ?: mutableSetOf()
        ids.add(scenario.id.toString())
        prefs.edit().putStringSet("scenario_ids", ids).apply()

        prefs.edit()
            .putString("scenario_${scenario.id}_name", scenario.name)
            .putStringSet("scenario_${scenario.id}_rooms", scenario.rooms.toSet())
            .putInt("scenario_${scenario.id}_temp", scenario.temperature)
            .apply()
    }

    fun updateScenario(scenario: Scenario) {
        saveScenario(scenario)
    }

    fun deleteScenario(scenarioId: Long) {
        val ids = prefs.getStringSet("scenario_ids", setOf())?.toMutableSet() ?: mutableSetOf()
        ids.remove(scenarioId.toString())
        prefs.edit().putStringSet("scenario_ids", ids).apply()

        prefs.edit()
            .remove("scenario_${scenarioId}_name")
            .remove("scenario_${scenarioId}_rooms")
            .remove("scenario_${scenarioId}_temp")
            .apply()
    }
}