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
            val imageUri = prefs.getString("scenario_${id}_image_uri", null)
            val scheduleEnabled = prefs.getBoolean("scenario_${id}_schedule_enabled", false)
            val startHour = prefs.getInt("scenario_${id}_start_hour", 9)
            val startMinute = prefs.getInt("scenario_${id}_start_minute", 0)
            if (name.isNotEmpty()) {
                scenarios.add(
                    Scenario(
                        id = id.toLong(),
                        name = name,
                        rooms = roomKeys,
                        temperature = temp,
                        imageUri = imageUri,
                        scheduleEnabled = scheduleEnabled,
                        startHour = startHour,
                        startMinute = startMinute
                    )
                )
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
            .putString("scenario_${scenario.id}_image_uri", scenario.imageUri)
            .putBoolean("scenario_${scenario.id}_schedule_enabled", scenario.scheduleEnabled)
            .putInt("scenario_${scenario.id}_start_hour", scenario.startHour)
            .putInt("scenario_${scenario.id}_start_minute", scenario.startMinute)
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
            .remove("scenario_${scenarioId}_image_uri")
            .remove("scenario_${scenarioId}_schedule_enabled")
            .remove("scenario_${scenarioId}_start_hour")
            .remove("scenario_${scenarioId}_start_minute")
            .apply()
    }
}