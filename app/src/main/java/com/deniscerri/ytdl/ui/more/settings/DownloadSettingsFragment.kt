package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.DragInteraction
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.CleanUpLeftoverDownloads
import com.deniscerri.ytdl.work.DownloadWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit


class DownloadSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.downloads

    private lateinit var archivePath: Preference

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

        val preventDuplicateDownloads = findPreference<ListPreference>("prevent_duplicate_downloads")
        preventDuplicateDownloads?.setOnPreferenceChangeListener { _, newValue ->
            archivePath.isVisible = newValue == "download_archive"
            true
        }

        archivePath = findPreference("download_archive_path")!!
        archivePath.summary = FileUtil.getDownloadArchivePath(requireContext())
        archivePath.isVisible = preferences.getString("prevent_duplicate_downloads", "") == "download_archive"
        archivePath.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                archivePathResultLauncher.launch(intent)
                true
            }

        val workManager = WorkManager.getInstance(requireContext())
        val cleanupLeftoverDownloads = findPreference<Preference>("cleanup_leftover_downloads")
        cleanupLeftoverDownloads?.setOnPreferenceChangeListener { preference, newValue ->
            var nextTime : Calendar? = Calendar.getInstance()
            when(newValue) {
                "daily" ->  nextTime?.add(Calendar.DAY_OF_WEEK, 1)
                "weekly" -> nextTime?.add(Calendar.DAY_OF_WEEK, 7)
                "monthly" -> nextTime?.add(Calendar.MONTH, 1)
                else -> nextTime = null
            }

            if (nextTime == null) workManager.cancelAllWorkByTag("cleanup_leftover_downloads")
            else {
                val workConstraints = Constraints.Builder()
                val allowMeteredNetworks = preferences.getBoolean("metered_networks", true)
                if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

                val delay = nextTime.timeInMillis.minus(System.currentTimeMillis())

                val workRequest = OneTimeWorkRequestBuilder<CleanUpLeftoverDownloads>()
                    .addTag("cleanup_leftover_downloads")
                    .setConstraints(workConstraints.build())
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)

                workManager.enqueueUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.REPLACE,
                    workRequest.build()
                )
            }

            true
        }


        val scheduler = AlarmScheduler(requireContext())

        val useAlarmManagerInsteadOfWorkManager = findPreference<SwitchPreferenceCompat>("use_alarm_for_scheduling")
        useAlarmManagerInsteadOfWorkManager?.setOnPreferenceChangeListener { preference, newValue ->
            var allowChange = true
            if (newValue as Boolean){
                if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                    Intent().also { intent ->
                        intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        requireContext().startActivity(intent)
                    }
                    allowChange = false
                }
            }

            allowChange
        }

        val useScheduler = findPreference<SwitchPreferenceCompat>("use_scheduler")
        val scheduleStart = findPreference<Preference>("schedule_start")
        scheduleStart?.summary = preferences.getString("schedule_start", "00:00")
        val scheduleEnd = findPreference<Preference>("schedule_end")
        scheduleEnd?.summary = preferences.getString("schedule_end", "05:00")

        useScheduler?.setOnPreferenceChangeListener { preference, newValue ->
            var allowChange = true
            if (newValue as Boolean){
                if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                    Intent().also { intent ->
                        intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        requireContext().startActivity(intent)
                    }
                    allowChange = false
                }else{
                    scheduler.schedule()
                }
            }else{
                scheduler.cancel()
                //start worker if there are leftover downloads waiting for scheduler
                val workConstraints = Constraints.Builder()
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .addTag("download")
                    .setConstraints(workConstraints.build())
                    .setInitialDelay(1000L, TimeUnit.MILLISECONDS)

                WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.REPLACE,
                    workRequest.build()
                )
            }
            allowChange
        }

        scheduleStart?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager, preferences){
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
            UiUtil.showTimePicker(parentFragmentManager, preferences){
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

    private var archivePathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val path = result.data!!.data.toString()
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val editor = preferences.edit()
            editor.putString("download_archive_path", path)
            editor.apply()
            archivePath.summary = FileUtil.getDownloadArchivePath(requireContext())
        }
    }

}