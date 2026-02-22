package com.example.ev

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Scenario(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val rooms: List<String>,
    val temperature: Int
) : Parcelable