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

        //update accent
        when (sharedPreferences.getString("theme_accent","blue")) {
            "Default" -> {
                DynamicColors.applyToActivityIfAvailable(activity)
                activity.setTheme(R.style.BaseTheme)
            }
            "blue" -> activity.setTheme(R.style.Theme_Blue)
            "red" -> activity.setTheme(R.style.Theme_Red)
            "green" -> activity.setTheme(R.style.Theme_Green)
            "purple" -> activity.setTheme(R.style.Theme_Purple)
            "yellow" -> activity.setTheme(R.style.Theme_Yellow)
            "monochrome" -> activity.setTheme(R.style.Theme_Monochrome)
        }

        //high contrast theme
        if (sharedPreferences.getBoolean("high_contrast",false)) {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        when (sharedPreferences.getString("ytdlnis_theme", "System")!!) {
            "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

    }
}