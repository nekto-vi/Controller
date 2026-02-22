package com.example.ev

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    private const val PREF_THEME = "pref_theme"
    private const val THEME_LIGHT = "light"
    private const val THEME_DARK = "dark"
    private const val THEME_SYSTEM = "system"

    fun setTheme(context: Context, theme: String) {
        persistTheme(context, theme)
        applyTheme(theme)
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return try {
            prefs.getString(PREF_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        } catch (e: ClassCastException) {
            // Если раньше было сохранено число, очищаем и возвращаем default
            prefs.edit().remove(PREF_THEME).apply()
            THEME_SYSTEM
        }
    }

    private fun persistTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_THEME, theme).apply()
    }

    fun applyTheme(theme: String) {
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun updateTheme(context: Context) {
        val theme = getTheme(context)
        applyTheme(theme)
    }
}