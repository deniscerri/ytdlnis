package com.deniscerri.ytdl.util

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeUtilInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun updateTheme_automaticPresetResetsLegacyDarkModeAndUsesLightPreset() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("use_theme_presets", true)
            .putBoolean("theme_preset_auto_mode", true)
            .putString("theme_preset_light_id", ThemeUtil.ThemePreset.Classic.value)
            .putString("theme_preset_dark_id", ThemeUtil.ThemePreset.Dark.value)
            .putString("ytdlnis_theme", "Dark")
            .commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        ActivityScenario.launch(ThemeUtilTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ThemeUtil.updateTheme(activity)

                assertThat(AppCompatDelegate.getDefaultNightMode())
                    .isEqualTo(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                assertThat(activity.themeColor(MaterialR.attr.colorPrimary))
                    .isEqualTo(Color.parseColor("#639eff"))
            }
        }
    }

    @Test
    fun updateTheme_automaticPresetUsesDarkPresetInNightMode() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("use_theme_presets", true)
            .putBoolean("theme_preset_auto_mode", true)
            .putString("theme_preset_light_id", ThemeUtil.ThemePreset.Classic.value)
            .putString("theme_preset_dark_id", ThemeUtil.ThemePreset.Dark.value)
            .commit()

        ActivityScenario.launch(ThemeUtilDarkTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ThemeUtil.updateTheme(activity)

                assertThat(AppCompatDelegate.getDefaultNightMode())
                    .isEqualTo(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                assertThat(activity.themeColor(MaterialR.attr.colorSurface))
                    .isEqualTo(Color.parseColor("#131313"))
            }
        }
    }

    @Test
    fun updateTheme_darkConcretePresetEnablesNightModeAndAppliesPreset() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("use_theme_presets", true)
            .putBoolean("theme_preset_auto_mode", false)
            .putString("theme_preset_concrete_id", ThemeUtil.ThemePreset.Dark.value)
            .commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        ActivityScenario.launch(ThemeUtilTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ThemeUtil.updateTheme(activity)

                assertThat(AppCompatDelegate.getDefaultNightMode())
                    .isEqualTo(AppCompatDelegate.MODE_NIGHT_YES)
                assertThat(activity.themeColor(MaterialR.attr.colorSurface))
                    .isEqualTo(Color.parseColor("#131313"))
            }
        }
    }
}

private fun Activity.themeColor(attribute: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attribute, value, true)
    return value.data
}
