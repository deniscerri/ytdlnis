package com.deniscerri.ytdl.ui.more.settings.downloading

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.CleanUpLeftoverDownloads
import com.deniscerri.ytdl.work.DownloadWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost

object DownloadSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        when(pref.key) {
            "remember_download_type" -> {
                val rememberDownloadType = pref as SwitchPreferenceCompat
                val downloadType = host.findPref("preferred_download_type")
                downloadType?.isEnabled = !rememberDownloadType.isChecked

                rememberDownloadType.setOnPreferenceChangeListener { _, newValue ->
                    downloadType?.isEnabled = !(newValue as Boolean)
                    host.refreshUI()
                    true
                }
            }
            "prevent_duplicate_downloads" -> {
                val archivePath = host.findPref("download_archive_path")
                pref.setOnPreferenceChangeListener { _, newValue ->
                    archivePath?.isVisible = newValue == "download_archive"
                    host.refreshUI()
                    true
                }
            }
            "download_archive_path" -> {
                pref.summary = FileUtil.getDownloadArchivePath(context)
                pref.isVisible = preferences.getString("prevent_duplicate_downloads", "") == "download_archive"
                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        host.activityResultDelegate.launch(intent) { result ->
                            result.data?.data?.let {
                                host.getHostContext().contentResolver?.takePersistableUriPermission(
                                    it,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            }

                            val path = result.data!!.data.toString()
                            preferences.edit(commit = true) {
                                putString("download_archive_path", path)
                            }
                            host.refreshUI()
                        }
                        true
                    }
            }
            "cleanup_leftover_downloads" -> {
                val workManager = WorkManager.getInstance(context)
                pref.setOnPreferenceChangeListener { preference, newValue ->
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
                    host.refreshUI()
                    true
                }
            }
            "use_alarm_for_scheduling" -> {
                val scheduler = AlarmScheduler(context)
                pref.setOnPreferenceChangeListener { preference, newValue ->
                    var allowChange = true
                    if (newValue as Boolean){
                        if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                            Intent().also { intent ->
                                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                context.startActivity(intent)
                            }
                            allowChange = false
                        }
                    }
                    host.refreshUI()
                    allowChange
                }
            }
            "use_scheduler" -> {
                val scheduler = AlarmScheduler(context)

                val useScheduler = pref as SwitchPreferenceCompat
                useScheduler.setOnPreferenceChangeListener { preference, newValue ->
                    var allowChange = true
                    if (newValue as Boolean){
                        if (!scheduler.canSchedule() && Build.VERSION.SDK_INT >= 31){
                            Intent().also { intent ->
                                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                context.startActivity(intent)
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

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            System.currentTimeMillis().toString(),
                            ExistingWorkPolicy.REPLACE,
                            workRequest.build()
                        )
                    }
                    host.refreshUI()
                    allowChange
                }
            }
            "schedule_start" -> {
                val scheduler = AlarmScheduler(context)

                pref.summary = preferences.getString("schedule_start", "00:00")
                pref.setOnPreferenceClickListener {
                    UiUtil.showTimePicker(host.requestGetParentFragmentManager(), preferences){
                        val hr = it.get(Calendar.HOUR_OF_DAY)
                        val mn = it.get(Calendar.MINUTE)
                        val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                        preferences.edit(commit = true) {
                            putString("schedule_start", formattedTime)
                        }
                        pref.summary = formattedTime
                        scheduler.schedule()
                    }
                    host.refreshUI()
                    true
                }
            }
            "schedule_end" -> {
                val scheduler = AlarmScheduler(context)

                pref.summary = preferences.getString("schedule_end", "05:00")
                pref.setOnPreferenceClickListener {
                    UiUtil.showTimePicker(host.requestGetParentFragmentManager(), preferences){
                        val hr = it.get(Calendar.HOUR_OF_DAY)
                        val mn = it.get(Calendar.MINUTE)
                        val formattedTime = String.format("%02d", hr) + ":" + String.format("%02d", mn)
                        preferences.edit(commit = true) {
                            putString("schedule_end",formattedTime)
                        }
                        pref.summary = formattedTime
                        scheduler.schedule()
                    }
                    host.refreshUI()
                    true
                }
            }
            "proxy" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.socks5_proxy_summary)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "preferred_download_type" -> {
                (pref as ListPreference).apply {
                    val s = context.getString(R.string.preferred_download_type_summary)
                    summary = if (value.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${entries[entryValues.indexOf(value)]}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${entries[entryValues.indexOf(newValue)]}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "buffer_size" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.buffer_size_summary)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "socket_timeout" -> {
                (pref as EditTextPreference).apply {
                    val s = context.getString(R.string.socket_timeout_description)
                    summary = if (text.isNullOrBlank()) {
                        s
                    }else {
                        "${s}\n[${text}]"
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        summary = if ((newValue as String?).isNullOrBlank()) {
                            s
                        }else {
                            "${s}\n[${newValue}]"
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
        }

    }
}