package com.example.ev

import android.content.Context
import android.content.Intent

object ScenarioShare {

    fun share(context: Context, scenario: Scenario) {
        val roomNames = scenario.rooms.joinToString { roomKey ->
            RoomMapper.keyToDisplayName(context, roomKey)
        }
        val body = buildString {
            appendLine(scenario.name)
            appendLine(context.getString(R.string.share_text_rooms_line, roomNames))
            appendLine(context.getString(R.string.share_text_temperature_line, scenario.temperature))
            if (scenario.scheduleEnabled) {
                appendLine(
                    context.getString(
                        R.string.share_text_schedule_line,
                        scenario.startHour,
                        scenario.startMinute
                    )
                )
            }
        }.trimEnd()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, scenario.name)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(
            intent,
            context.getString(R.string.share_scenario_chooser_title)
        )
        context.startActivity(chooser)
    }
}
