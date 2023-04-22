package com.deniscerri.ytdlnis.util

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.deniscerri.ytdlnis.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    fun updateTheme(activity: AppCompatActivity) {
        val sharedPreferences = activity.getSharedPreferences("root_preferences", Application.MODE_PRIVATE)
        val themeMode = sharedPreferences.getString("ytdlnis_theme", "Default")!!

        //update accent
        val themeAccent = sharedPreferences.getString("theme_accent","blue")
        when (themeAccent) {
            "Default" -> activity.setTheme(R.style.BaseTheme)
            "blue" -> activity.setTheme(R.style.Theme_Blue)
            "red" -> activity.setTheme(R.style.Theme_Red)
            "green" -> activity.setTheme(R.style.Theme_Green)
            "purple" -> activity.setTheme(R.style.Theme_Purple)
            "yellow" -> activity.setTheme(R.style.Theme_Yellow)
            "monochrome" -> activity.setTheme(R.style.Theme_Monochrome)
        }


        //dynamic colors
        if (themeAccent == "Default") DynamicColors.applyToActivityIfAvailable(activity)

        //high contrast theme
        val highContrast = sharedPreferences.getBoolean(
            "high_contrast",
            false
        )
        if (highContrast) activity.theme.applyStyle(R.style.Pure, true)

        when (themeMode) {
            "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}