package com.example.ev

import android.content.Context

object RoomMapper {

    private val roomKeys = listOf(
        R.string.room_living_room,
        R.string.room_bedroom,
        R.string.room_kitchen,
        R.string.room_bathroom,
        R.string.room_hall
    )

    private val roomDisplayNames = listOf(
        R.string.living_room,
        R.string.bedroom,
        R.string.kitchen,
        R.string.bathroom,
        R.string.hall
    )

    fun getAvailableRooms(context: Context): List<Pair<String, String>> {
        return roomKeys.mapIndexed { index, keyRes ->
            val key = context.getString(keyRes)
            val displayName = context.getString(roomDisplayNames[index])
            key to displayName
        }
    }

    fun keyToDisplayName(context: Context, key: String): String {
        val index = roomKeys.indexOfFirst { context.getString(it) == key }
        return if (index != -1) context.getString(roomDisplayNames[index]) else key
    }

    fun displayNameToKey(context: Context, displayName: String): String {
        val index = roomDisplayNames.indexOfFirst { context.getString(it) == displayName }
        return if (index != -1) context.getString(roomKeys[index]) else displayName
    }

    fun getDisplayNames(context: Context): List<String> {
        return roomDisplayNames.map { context.getString(it) }
    }

    fun getKeys(context: Context): List<String> {
        return roomKeys.map { context.getString(it) }
    }
}