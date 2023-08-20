package com.deniscerri.ytdlnis.ui.more.settings

import android.app.Activity
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class DownloadSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.downloads

    private var concurrentDownloads: SeekBarPreference? = null
    private var ignoreBatteryOptimization: Preference? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.downloading_preferences, rootKey)

        ignoreBatteryOptimization = findPreference("ignore_battery")
        val packageName: String = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ignoreBatteryOptimization!!.isVisible = false
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

}