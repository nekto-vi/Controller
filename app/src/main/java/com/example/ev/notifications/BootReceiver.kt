package com.example.ev.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ev.data.ScenarioRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val repository = ScenarioRepository(context)
        repository.getAllScenarios()
            .filter { it.scheduleEnabled }
            .forEach { scenario ->
                ScenarioScheduleManager.scheduleScenario(context, scenario)
            }
    }
}
