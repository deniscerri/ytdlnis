package com.deniscerri.ytdl.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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

    /**
     * One entry per "theme_accent" option. [value] is the exact string stored in
     * SharedPreferences (must match res/values/arrays.xml's accents_values and the
     * `when` branches in [updateTheme]). [styleResource] is only meaningful for the
     * non-Default entries — Default is previewed/applied via DynamicColors instead,
     * since it has no fixed style of its own.
     */
    sealed class AccentOption(
        @StringRes val nameResource: Int,
        val value: String,
        @StyleRes val styleResource: Int
    ) {
        object Default : AccentOption(R.string.material_you, "Default", R.style.BaseTheme)
        object Blue : AccentOption(R.string.blue, "blue", R.style.Theme_Blue)
        object Red : AccentOption(R.string.red, "red", R.style.Theme_Red)
        object Green : AccentOption(R.string.green, "green", R.style.Theme_Green)
        object Purple : AccentOption(R.string.purple, "purple", R.style.Theme_Purple)
        object Yellow : AccentOption(R.string.yellow, "yellow", R.style.Theme_Yellow)
        object Orange : AccentOption(R.string.orange, "orange", R.style.Theme_Orange)
        object Monochrome : AccentOption(R.string.monochrome, "monochrome", R.style.Theme_Monochrome)
    }

    val availableAccents = listOf(
        AccentOption.Default,
        AccentOption.Blue,
        AccentOption.Red,
        AccentOption.Green,
        AccentOption.Purple,
        AccentOption.Yellow,
        AccentOption.Orange,
        AccentOption.Monochrome,
    )

    /**
     * Ported from BAI's Theme.Mode. CONCRETE = always use one fixed preset.
     * AUTO_LIGHT_DARK = separately pick a light-mode preset and a dark-mode
     * preset, and switch between them based on the system's current light/dark
     * state — checked directly via Configuration rather than porting BAI's own
     * pre-Android-10 detection/warning path, since ytdlnis's AppCompatDelegate
     * usage elsewhere already handles that compatibility range.
     */
    enum class ThemePresetMode { CONCRETE, AUTO_LIGHT_DARK }

    /**
     * Ported from BAI's Theme.java ThemeDescriptor list. [isDark] reflects each
     * preset's actual rendered luminance, not always BAI's own stored flag —
     * see theme_presets.xml's header comment for the one entry ("Ukrrooter")
     * where the two disagree.
     */
    sealed class ThemePreset(
        @StringRes val nameResource: Int,
        val value: String,
        @StyleRes val styleResource: Int,
        val isDark: Boolean
    ) {
        object Classic : ThemePreset(R.string.theme_preset_classic, "classic", R.style.ThemePreset_Classic, false)
        object Ruby : ThemePreset(R.string.theme_preset_ruby, "ruby", R.style.ThemePreset_Ruby, true)
        object Rena : ThemePreset(R.string.theme_preset_rena, "rena", R.style.ThemePreset_Rena, true)
        object Ukrrooter : ThemePreset(R.string.theme_preset_ukrrooter, "ukrrooter", R.style.ThemePreset_Ukrrooter, true)
        object Amoled : ThemePreset(R.string.theme_preset_amoled, "amoled", R.style.ThemePreset_Amoled, true)
        object Pixel : ThemePreset(R.string.theme_preset_pixel, "pixel", R.style.ThemePreset_Pixel, false)
        object FDroidLight : ThemePreset(R.string.theme_preset_fdroid_light, "fdroid_light", R.style.ThemePreset_FDroidLight, false)
        object Dark : ThemePreset(R.string.theme_preset_dark, "dark", R.style.ThemePreset_Dark, true)
        object Gold : ThemePreset(R.string.theme_preset_gold, "gold", R.style.ThemePreset_Gold, true)
        object RenaLight : ThemePreset(R.string.theme_preset_rena_light, "rena_light", R.style.ThemePreset_RenaLight, false)
        object Mint : ThemePreset(R.string.theme_preset_mint, "mint", R.style.ThemePreset_Mint, true)
        object FDroidDark : ThemePreset(R.string.theme_preset_fdroid_dark, "fdroid_dark", R.style.ThemePreset_FDroidDark, true)
    }

    val availableThemePresets = listOf(
        ThemePreset.Classic,
        ThemePreset.Ruby,
        ThemePreset.Rena,
        ThemePreset.Ukrrooter,
        ThemePreset.Amoled,
        ThemePreset.Pixel,
        ThemePreset.FDroidLight,
        ThemePreset.Dark,
        ThemePreset.Gold,
        ThemePreset.RenaLight,
        ThemePreset.Mint,
        ThemePreset.FDroidDark,
    )

    fun isThemePresetsEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("use_theme_presets", false)

    fun getThemePresetMode(context: Context): ThemePresetMode {
        val auto = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("theme_preset_auto_mode", false)
        return if (auto) ThemePresetMode.AUTO_LIGHT_DARK else ThemePresetMode.CONCRETE
    }

    fun setThemePresetAutoMode(context: Context, auto: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean("theme_preset_auto_mode", auto) }
    }

    private fun findPreset(value: String?, fallback: ThemePreset): ThemePreset =
        availableThemePresets.firstOrNull { it.value == value } ?: fallback

    fun getConcreteThemePreset(context: Context): ThemePreset = findPreset(
        PreferenceManager.getDefaultSharedPreferences(context).getString("theme_preset_concrete_id", null),
        ThemePreset.Classic
    )

    fun setConcreteThemePreset(context: Context, preset: ThemePreset) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { putString("theme_preset_concrete_id", preset.value) }
    }

    fun getLightThemePreset(context: Context): ThemePreset = findPreset(
        PreferenceManager.getDefaultSharedPreferences(context).getString("theme_preset_light_id", null),
        ThemePreset.Classic
    )

    fun getDarkThemePreset(context: Context): ThemePreset = findPreset(
        PreferenceManager.getDefaultSharedPreferences(context).getString("theme_preset_dark_id", null),
        ThemePreset.Dark
    )

    /**
     * Writes both slots in a single Editor transaction so a process death
     * mid-save can't persist one side of the pair without the other.
     */
    fun setLightAndDarkThemePresets(context: Context, light: ThemePreset, dark: ThemePreset) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString("theme_preset_light_id", light.value)
            putString("theme_preset_dark_id", dark.value)
        }
    }

    private fun applyThemePreset(activity: Activity) {
        val preset = when (getThemePresetMode(activity)) {
            ThemePresetMode.CONCRETE -> getConcreteThemePreset(activity)
            ThemePresetMode.AUTO_LIGHT_DARK -> {
                val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) getDarkThemePreset(activity) else getLightThemePreset(activity)
            }
        }
        activity.setTheme(preset.styleResource)
    }

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

        if (isThemePresetsEnabled(activity)) {
            val nightMode = when (getThemePresetMode(activity)) {
                ThemePresetMode.AUTO_LIGHT_DARK -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemePresetMode.CONCRETE -> if (getConcreteThemePreset(activity).isDark) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
            applyThemePreset(activity)
        } else {
            //update accent
            when (sharedPreferences.getString("theme_accent","blue")) {
                "Default" -> {
                    activity.setTheme(R.style.BaseTheme)
                    DynamicColors.applyToActivityIfAvailable(activity)
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

            when (sharedPreferences.getString("ytdlnis_theme", "System")) {
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
        }

        val theme = sharedPreferences.getString("ytdlnis_theme", "System")!!
        val iconMode = sharedPreferences.getString("ytdlnis_icon", "Default")!!
        updateAppIcon(activity,theme, iconMode)
    }

    fun getThemeColor(context: Context, colorCode: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val accent = sharedPreferences.getString("theme_accent", "blue")
        return if (accent == "blue" && !isThemePresetsEnabled(context)){
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
