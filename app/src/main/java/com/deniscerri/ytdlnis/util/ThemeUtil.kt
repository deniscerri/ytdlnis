package com.deniscerri.ytdlnis.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdlnis.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    sealed class AppIcon(
        @DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.mipmap.ic_launcher_dark, "DarkIcon")
    }

    private val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark
    )

    fun updateTheme(activity: AppCompatActivity) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

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
            "orange" -> activity.setTheme(R.style.Theme_Orange)
            "monochrome" -> activity.setTheme(R.style.Theme_Monochrome)
        }

        //high contrast theme
        if (sharedPreferences.getBoolean("high_contrast",false)) {
            activity.theme.applyStyle(R.style.Pure, true)
        }

        //disable old icons
        for (appIcon in availableIcons) {
            val activityClass = "com.deniscerri.ytdlnis." + appIcon.activityAlias

            // remove old icons
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        when (sharedPreferences.getString("ytdlnis_theme", "System")!!) {
            "System" -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdlnis.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            "Light" -> {
                //set light icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdlnis.LightIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "Dark" -> {
                //set dark icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdlnis.DarkIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdlnis.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

    }

    private fun getThemeColor(context: Context, colorCode: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val accent = sharedPreferences.getString("theme_accent", "blue")
        return if (accent == "blue"){
            "d43c3b".toInt(16)
        }else{
            val value = TypedValue()
            context.theme.resolveAttribute(colorCode, value, true)
            value.data
        }

    }

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "<span  style='color:$hexColor';>YTDL</span>nis"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
}