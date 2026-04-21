package com.example.ev.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ev.data.ScenarioRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (FirebaseAuth.getInstance().currentUser == null) return@launch
                val repository = ScenarioRepository(appContext)
                repository.getAllScenarios()
                    .filter { it.scheduleEnabled }
                    .forEach { scenario ->
                        ScenarioScheduleManager.scheduleScenario(appContext, scenario)
                    }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
