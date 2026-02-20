package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
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

// Fragment for download‑related settings (concurrent downloads, rate limit, scheduler, etc.)
class DownloadSettingsFragment : SearchableSettingsFragment() {
    override val title: Int = R.string.downloads

    private lateinit var archivePath: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.downloading_preferences, rootKey)
        buildPreferenceList(preferenceScreen)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Remember download type: when enabled, the preferred download type list is disabled.
        val rememberDownloadType = findPreference<SwitchPreferenceCompat>("remember_download_type")
        val downloadType = findPreference<ListPreference>("preferred_download_type")
        downloadType?.isEnabled = rememberDownloadType?.isChecked == false
        rememberDownloadType?.setOnPreferenceClickListener {
            downloadType?.isEnabled = !rememberDownloadType.isChecked
            true
        }

        // Prevent duplicate downloads: if set to "download_archive", show the archive path preference.
        val preventDuplicateDownloads = findPreference<ListPreference>("prevent_duplicate_downloads")
        preventDuplicateDownloads?.setOnPreferenceChangeListener { _, newValue ->
            archivePath.isVisible = newValue == "download_archive"
            true
        }

        // Archive path – opens a folder picker and updates the summary.
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

        // Cleanup leftover downloads – schedule periodic cleanup based on user choice.
        val cleanupLeftoverDownloads = findPreference<Preference>("cleanup_leftover_downloads")
        cleanupLeftoverDownloads?.setOnPreferenceChangeListener { preference, newValue ->
            var nextTime: Calendar? = Calendar.getInstance()
            when (newValue) {
                "daily" -> nextTime?.add(Calendar.DAY_OF_WEEK, 1)
                "weekly" -> nextTime?.add(Calendar.DAY_OF_WEEK, 7)
                "monthly" -> nextTime?.add(Calendar.MONTH, 1)
                else -> nextTime = null
            }

            if (nextTime == null) {
                workManager.cancelAllWorkByTag("cleanup_leftover_downloads")
            } else {
                val workConstraints = Constraints.Builder()
                val allowMeteredNetworks = preferences.getBoolean("metered_networks", true)
                if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

                val delay = nextTime.timeInMillis - System.currentTimeMillis()

                val workRequest = OneTimeWorkRequestBuilder<CleanUpLeftoverDownloads>()
                    .addTag("cleanup_leftover_downloads")
                    .setConstraints(workConstraints.build())
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build()

                workManager.enqueueUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            true
        }

        val scheduler = AlarmScheduler(requireContext())

        // Use AlarmManager instead of WorkManager for scheduling – checks exact alarm permission.
        val useAlarmManagerInsteadOfWorkManager = findPreference<SwitchPreferenceCompat>("use_alarm_for_scheduling")
        useAlarmManagerInsteadOfWorkManager?.setOnPreferenceChangeListener { preference, newValue ->
            var allowChange = true
            if (newValue as Boolean) {
                if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31) {
                    Intent().also { intent ->
                        intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        requireContext().startActivity(intent)
                    }
                    allowChange = false
                }
            }
            allowChange
        }

        // Scheduler enable/disable.
        val useScheduler = findPreference<SwitchPreferenceCompat>("use_scheduler")
        val scheduleStart = findPreference<Preference>("schedule_start")
        scheduleStart?.summary = preferences.getString("schedule_start", "00:00")
        val scheduleEnd = findPreference<Preference>("schedule_end")
        scheduleEnd?.summary = preferences.getString("schedule_end", "05:00")

        useScheduler?.setOnPreferenceChangeListener { preference, newValue ->
            var allowChange = true
            if (newValue as Boolean) {
                if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31) {
                    Intent().also { intent ->
                        intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        requireContext().startActivity(intent)
                    }
                    allowChange = false
                } else {
                    scheduler.schedule()
                }
            } else {
                scheduler.cancel()
                // If there are downloads waiting for the scheduler, start them immediately.
                val workConstraints = Constraints.Builder()
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .addTag("download")
                    .setConstraints(workConstraints.build())
                    .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                    .build()

                WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            allowChange
        }

        // Time picker for schedule start.
        scheduleStart?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager, preferences) {
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_start", formattedTime).apply()
                scheduleStart.summary = formattedTime
                scheduler.schedule()
            }
            true
        }

        // Time picker for schedule end.
        scheduleEnd?.setOnPreferenceClickListener {
            UiUtil.showTimePicker(parentFragmentManager, preferences) {
                val hr = it.get(Calendar.HOUR_OF_DAY)
                val mn = it.get(Calendar.MINUTE)
                val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                preferences.edit().putString("schedule_end", formattedTime).apply()
                scheduleEnd.summary = formattedTime
                scheduler.schedule()
            }
            true
        }

        // Proxy preference – show current value in summary.
        findPreference<EditTextPreference>("proxy")?.apply {
            val s = getString(R.string.socks5_proxy_summary)
            summary = if (text.isNullOrBlank()) s else "${s}\n[${text}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${newValue}]"
                true
            }
        }

        // Preferred download type – show selected entry in summary.
        findPreference<ListPreference>("preferred_download_type")?.apply {
            val s = getString(R.string.preferred_download_type_summary)
            summary = if (value.isNullOrBlank()) s else "${s}\n[${entries[entryValues.indexOf(value)]}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${entries[entryValues.indexOf(newValue)]}]"
                true
            }
        }

        // Limit rate – show current value in summary.
        findPreference<EditTextPreference>("limit_rate")?.apply {
            val s = getString(R.string.limit_rate_summary)
            summary = if (text.isNullOrBlank()) s else "${s}\n[${text}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${newValue}]"
                true
            }
        }

        // Buffer size – show current value in summary.
        findPreference<EditTextPreference>("buffer_size")?.apply {
            val s = getString(R.string.buffer_size_summary)
            summary = if (text.isNullOrBlank()) s else "${s}\n[${text}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${newValue}]"
                true
            }
        }

        // Socket timeout – show current value in summary.
        findPreference<EditTextPreference>("socket_timeout")?.apply {
            val s = getString(R.string.socket_timeout_description)
            summary = if (text.isNullOrBlank()) s else "${s}\n[${text}]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "${s}\n[${newValue}]"
                true
            }
        }

        // Reset all preferences in this screen.
        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(preferences.edit(), R.xml.downloading_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!, true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }

    // Launcher for archive path folder picker – takes persistable URI permission and updates preference.
    private var archivePathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val path = result.data!!.data.toString()
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            preferences.edit().putString("download_archive_path", path).apply()
            archivePath.summary = FileUtil.getDownloadArchivePath(requireContext())
        }
    }
}