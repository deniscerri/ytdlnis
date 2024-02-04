package com.deniscerri.ytdlnis.ui.more.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.receiver.downloadAlarmReceivers.AlarmStartReceiver
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.work.AlarmScheduler
import java.util.Calendar


class DownloadSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.downloads

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.downloading_preferences, rootKey)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val rememberDownloadType = findPreference<SwitchPreferenceCompat>("remember_download_type")
        val downloadType = findPreference<ListPreference>("preferred_download_type")
        downloadType?.isEnabled = rememberDownloadType?.isChecked == false
        rememberDownloadType?.setOnPreferenceClickListener {
            downloadType?.isEnabled = !rememberDownloadType.isChecked
            true
        }

        val useScheduler = findPreference<SwitchPreferenceCompat>("use_scheduler")
        val scheduleStart = findPreference<Preference>("schedule_start")
        scheduleStart?.summary = preferences.getString("schedule_start", "00:00")
        val scheduleEnd = findPreference<Preference>("schedule_end")
        scheduleEnd?.summary = preferences.getString("schedule_end", "05:00")
        val scheduler = AlarmScheduler(requireContext())

        useScheduler?.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean){
                scheduler.schedule()
            }else{
                scheduler.cancel()
                //start worker if there are leftover downloads waiting for scheduler
                val intent = Intent(context, AlarmStartReceiver::class.java)
                requireActivity().sendBroadcast(intent)
            }
            true
        }

        scheduleStart?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager){
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_start",formattedTime).apply()
                scheduleStart.summary = formattedTime

                scheduler.schedule()
            }
            true
        }

        scheduleEnd?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager){
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_end",formattedTime).apply()
                scheduleEnd.summary = formattedTime

                scheduler.schedule()
            }
            true
        }
    }

}