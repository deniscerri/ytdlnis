package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.google.android.material.color.DynamicColors


object ThemeUtil {

    private val activities = mutableListOf<Activity>()

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                activities.add(p0)
            }

            override fun onActivityStarted(p0: Activity) {

            }

            override fun onActivityResumed(p0: Activity) {

            }

            override fun onActivityPaused(p0: Activity) {

            }

            override fun onActivityStopped(p0: Activity) {

            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

            }

            override fun onActivityDestroyed(p0: Activity) {
                activities.remove(p0)
            }
        })

    }

    sealed class AppIcon(
        @StringRes val nameResource: Int,
        @DrawableRes val iconResource: Int,
        val activityAlias: String
    ) {
        object Default : AppIcon(R.string.auto, R.mipmap.ic_launcher, "Default")
        object Light : AppIcon(R.string.light, R.mipmap.ic_launcher_light, "LightIcon")
        object Dark : AppIcon(R.string.dark, R.mipmap.ic_launcher_dark, "DarkIcon")
        object Blue : AppIcon(R.string.blue, R.mipmap.ic_launcher_blue, "BlueIcon")
        object Green : AppIcon(R.string.green, R.mipmap.ic_launcher_green, "GreenIcon")
    }

    val availableIcons = listOf(
        AppIcon.Default,
        AppIcon.Light,
        AppIcon.Dark,
        AppIcon.Blue,
        AppIcon.Green,
    )

    fun recreateMain() {
        activities.firstOrNull { it.javaClass == MainActivity::class.java }?.recreate()
    }

    fun recreateAllActivities() {
        activities.forEach {
            it.recreate()
        }
    }

    fun updateThemes() {
        activities.forEach {
            updateTheme(it)
            it.recreate()
        }
    }

    fun updateTheme(activity: Activity) {
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

        val theme = sharedPreferences.getString("ytdlnis_theme", "System")!!
        when (theme) {
            "Light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "Dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            // or "System"
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }


        val iconMode = sharedPreferences.getString("ytdlnis_icon", "Default")!!
        updateAppIcon(activity,theme, iconMode)
    }

    fun getThemeColor(context: Context, colorCode: Int): Int {
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


    fun getLiquidGlassMode(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString("liquid_glass_mode", "full") ?: "full"
    }

    fun isLiquidGlassEnabled(context: Context): Boolean {
        return getLiquidGlassMode(context) != "off"
    }

    fun isLiquidGlassFull(context: Context): Boolean {
        return getLiquidGlassMode(context) == "full"
    }

    fun applyLiquidGlassToCard(view: View) {
        val mode = getLiquidGlassMode(view.context)
        when (mode) {
            "full" -> view.setBackgroundResource(R.drawable.bg_glass_card_full)
            "lite" -> view.setBackgroundResource(R.drawable.bg_glass_card_lite)
            else -> {}
        }
    }

    fun styleCard(cardView: com.google.android.material.card.MaterialCardView, isChecked: Boolean = false) {
        val context = cardView.context
        val mode = getLiquidGlassMode(context)

        if (mode != "off") {
            val checkedStrokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, context.resources.displayMetrics).toInt()

            cardView.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (mode == "full") 20f else 18f, context.resources.displayMetrics)

            if (isChecked) {
                cardView.strokeWidth = checkedStrokePx
                cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)))
            } else {
                cardView.strokeWidth = 0
            }
            applyLiquidGlassToCard(cardView)
        } else {
            if (isChecked) {
                val checkedStrokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, context.resources.displayMetrics).toInt()
                cardView.strokeWidth = checkedStrokePx
                cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)))
            } else {
                cardView.strokeWidth = 0
            }
        }
    }

    fun applyLiquidGlassToNavBar(view: View) {
        val mode = getLiquidGlassMode(view.context)
        when (mode) {
            "full" -> view.setBackgroundResource(R.drawable.bg_glass_navbar_full)
            "lite" -> view.setBackgroundResource(R.drawable.bg_glass_navbar_lite)
            else -> {}
        }
    }

    fun applyLiquidGlassToSearchBar(view: View) {
        val mode = getLiquidGlassMode(view.context)
        when (mode) {
            "full" -> view.setBackgroundResource(R.drawable.bg_glass_searchbar_full)
            "lite" -> view.setBackgroundResource(R.drawable.bg_glass_searchbar_lite)
            else -> {}
        }
    }

    fun updateAppIcon(activity: Activity, theme: String, appIconMode: String) {
        //disable old icons
        for (appIcon in availableIcons) {
            val activityClass = "com.deniscerri.ytdl." + appIcon.activityAlias

            // remove old icons
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        var iconMode = appIconMode
        if (appIconMode == "Default") {
            iconMode = theme
        }

        when (iconMode) {
            "LightIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.LightIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "DarkIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.DarkIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "BlueIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.BlueIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            "GreenIcon" -> {
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.GreenIcon"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // or "System"
            else -> {
                //set dynamic icon
                activity.packageManager.setComponentEnabledSetting(
                    ComponentName(activity.packageName, "com.deniscerri.ytdl.Default"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}