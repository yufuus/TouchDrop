package com.touchdrop.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS_NAME = "touchdrop_theme_prefs"
    private const val KEY_THEME = "selected_theme"

    const val THEME_LIGHT = "light"       // Modern iOS Grimsi Aydınlık / Cool Slate Gray
    const val THEME_OBSIDIAN = "obsidian" // Koyu / Obsidian
    const val THEME_SYSTEM = "system"     // Sistem varsayılanı

    fun applyTheme(context: Context) {
        val theme = getSavedTheme(context)
        val mode = when (theme) {
            THEME_LIGHT, "cream" -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_OBSIDIAN -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
        val mode = when (theme) {
            THEME_LIGHT, "cream" -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_OBSIDIAN -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getSavedTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, THEME_OBSIDIAN) ?: THEME_OBSIDIAN
        return if (saved == "cream") THEME_LIGHT else saved
    }
}
