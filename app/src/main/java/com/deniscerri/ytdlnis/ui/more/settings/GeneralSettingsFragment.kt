package com.deniscerri.ytdlnis.ui.more.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import java.util.Locale


class GeneralSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.general

    private var language: ListPreference? = null
    private var theme: ListPreference? = null
    private var accent: ListPreference? = null
    private var highContrast: SwitchPreferenceCompat? = null
    private var locale: ListPreference? = null
    private var showTerminalShareIcon: SwitchPreferenceCompat? = null
    private var ignoreBatteryOptimization: Preference? = null

    private var updateUtil: UpdateUtil? = null
    private var activeDownloadCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        language = findPreference("app_language")
        theme = findPreference("ytdlnis_theme")
        accent = findPreference("theme_accent")
        highContrast = findPreference("high_contrast")
        locale = findPreference("locale")
        showTerminalShareIcon = findPreference("show_terminal")

        if(language!!.value == null) language!!.value = Locale.getDefault().language

        language!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
                true
            }


        theme!!.summary = theme!!.entry
        theme!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                when(newValue){
                    "System" -> {
                        theme!!.summary = getString(R.string.system)
                    }
                    "Dark" -> {
                        theme!!.summary = getString(R.string.dark)
                    }
                    else -> {
                        theme!!.summary = getString(R.string.light)
                    }
                }
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        accent!!.summary = accent!!.entry
        accent!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        highContrast!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
                requireActivity().finishAffinity()

                true
            }

        showTerminalShareIcon!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref: Preference?, _: Any ->
                val packageManager = requireContext().packageManager
                val aliasComponentName = ComponentName(requireContext(), "com.deniscerri.ytdlnis.terminalShareAlias")
                if ((pref as SwitchPreferenceCompat).isChecked){
                    packageManager.setComponentEnabledSetting(aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                }else{
                    packageManager.setComponentEnabledSetting(aliasComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP)
                }
                true
            }

        ignoreBatteryOptimization = findPreference("ignore_battery")
        ignoreBatteryOptimization!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
                true
            }
    }

    override fun onResume() {
        val packageName: String = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ignoreBatteryOptimization!!.isVisible = false
        }
        super.onResume()
    }

}