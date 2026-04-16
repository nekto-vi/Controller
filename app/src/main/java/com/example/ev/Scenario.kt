package com.example.ev

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Scenario(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val rooms: List<String>,
    val temperature: Int,
    val scheduleEnabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0
) : Parcelable