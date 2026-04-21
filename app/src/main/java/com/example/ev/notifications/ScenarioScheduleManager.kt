package com.example.ev.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.ev.Scenario
import java.util.Calendar

object ScenarioScheduleManager {
    const val CHANNEL_ID = "scenario_activation_channel_v2"
    const val EXTRA_SCENARIO_ID = "extra_scenario_id"
    const val EXTRA_SCENARIO_NAME = "extra_scenario_name"
    const val EXTRA_SCENARIO_HOUR = "extra_scenario_hour"
    const val EXTRA_SCENARIO_MINUTE = "extra_scenario_minute"

    fun scheduleScenario(context: Context, scenario: Scenario) {
        if (!scenario.scheduleEnabled) {
            cancelScenario(context, scenario.id)
            return
        }

        val triggerAtMillis = calculateNextTriggerTime(scenario.startHour, scenario.startMinute)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(
            context = context,
            scenarioId = scenario.id,
            scenarioName = scenario.name,
            hour = scenario.startHour,
            minute = scenario.startMinute
        )

        try {
            if (canScheduleExactAlarms(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelScenario(context: Context, scenarioId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScenarioActivationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeForScenario(scenarioId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(com.example.ev.R.string.scenario_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.example.ev.R.string.scenario_notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
            val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createPendingIntent(
        context: Context,
        scenarioId: Long,
        scenarioName: String,
        hour: Int,
        minute: Int
    ): PendingIntent {
        val intent = Intent(context, ScenarioActivationReceiver::class.java).apply {
            putExtra(EXTRA_SCENARIO_ID, scenarioId)
            putExtra(EXTRA_SCENARIO_NAME, scenarioName)
            putExtra(EXTRA_SCENARIO_HOUR, hour)
            putExtra(EXTRA_SCENARIO_MINUTE, minute)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeForScenario(scenarioId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return canScheduleExactAlarms(alarmManager)
    }

    fun createExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            alarmManager.canScheduleExactAlarms()
        }
    }

    private fun calculateNextTriggerTime(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (trigger.before(now) || trigger.timeInMillis == now.timeInMillis) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }
        return trigger.timeInMillis
    }

    fun requestCodeForScenario(scenarioId: Long): Int {
        return (scenarioId xor (scenarioId ushr 32)).toInt()
    }
}
