package com.example.ev.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ev.MainActivity
import com.example.ev.R
import com.example.ev.Scenario

class ScenarioActivationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val scenarioId = intent.getLongExtra(ScenarioScheduleManager.EXTRA_SCENARIO_ID, -1L)
        if (scenarioId == -1L) return

        val scenarioName = intent.getStringExtra(ScenarioScheduleManager.EXTRA_SCENARIO_NAME)
            ?: context.getString(R.string.scenario_default_name)
        val hour = intent.getIntExtra(ScenarioScheduleManager.EXTRA_SCENARIO_HOUR, 9)
        val minute = intent.getIntExtra(ScenarioScheduleManager.EXTRA_SCENARIO_MINUTE, 0)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", R.id.home)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            ScenarioScheduleManager.requestCodeForScenario(scenarioId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ScenarioScheduleManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(context.getString(R.string.scenario_activated_title))
            .setContentText(context.getString(R.string.scenario_activated_message, scenarioName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context)
                .notify(ScenarioScheduleManager.requestCodeForScenario(scenarioId), notification)
        }

        // Re-schedule for the next day.
        ScenarioScheduleManager.scheduleScenario(
            context,
            Scenario(
                id = scenarioId,
                name = scenarioName,
                rooms = emptyList(),
                temperature = 22,
                scheduleEnabled = true,
                startHour = hour,
                startMinute = minute
            )
        )
    }
}
