package com.deniscerri.ytdl.ui.more.settings.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.collections.first

object FolderSettingsModule: SettingModule {

    const val MUSIC_PATH_CODE = 33333
    const val VIDEO_PATH_CODE = 55555
    const val COMMAND_PATH_CODE = 77777
    const val CACHE_PATH_CODE = 99999

    override fun bindLogic(
        pref: Preference,
        host: SettingHost
    ) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var activeDownloadCount = 0
        val downloadViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[DownloadViewModel::class.java]

        when(pref.key) {
            "music_path" -> {
                if (preferences.getString(pref.key, "")!!.isEmpty()) {
                    preferences.edit(commit = true) {
                        putString(pref.key, FileUtil.getDefaultAudioPath())
                    }
                }
                pref.apply {
                    summary = FileUtil.formatPath(preferences.getString(pref.key, "")!!)
                    onPreferenceClickListener =
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
                                changePath(host,pref, result.data, MUSIC_PATH_CODE)
                            }
                            true
                        }
                }
            }
            "video_path" -> {
                if (preferences.getString(pref.key, "")!!.isEmpty()) {
                    preferences.edit(commit = true) {
                        putString(pref.key, FileUtil.getDefaultVideoPath())
                    }
                }
                pref.apply {
                    summary = FileUtil.formatPath(preferences.getString(pref.key, "")!!)
                    onPreferenceClickListener =
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
                                changePath(host, pref, result.data, VIDEO_PATH_CODE)
                            }
                            true
                        }
                }
            }
            "command_path" -> {
                if (preferences.getString(pref.key, "")!!.isEmpty()) {
                    preferences.edit(commit = true) {
                        putString(pref.key, FileUtil.getDefaultCommandPath())
                    }
                }
                pref.apply {
                    summary = FileUtil.formatPath(preferences.getString("command_path", "")!!)
                    onPreferenceClickListener =
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
                                changePath(host,pref, result.data, COMMAND_PATH_CODE)
                            }
                            true
                        }
                }
            }
            "cache_path" -> {
                if (preferences.getString(pref.key, "")!!.isEmpty()) {
                    preferences.edit(commit = true) {
                        putString(pref.key, FileUtil.getCachePath(context))
                    }
                }
                pref.apply {
                    summary = FileUtil.formatPath(preferences.getString(pref.key, FileUtil.getCachePath(context))!!)
                    isEnabled = (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
                            Build.VERSION.SDK_INT < 30
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            UiUtil.showGenericConfirmDialog(context, context.getString(R.string.cache_directory), context.getString(
                                R.string.cache_directory_warning)) {
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
                                    changePath(host, pref, result.data, CACHE_PATH_CODE)
                                }
                            }
                            true
                        }
                }
            }
            "access_all_files" -> {
                pref.apply {
                    if ((Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
                        Build.VERSION.SDK_INT < 30) {
                        isVisible = false
                    }

                    if (Build.VERSION.SDK_INT >= 30) {
                        onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                val uri = Uri.parse("package:" + context.packageName)
                                intent.data = uri
                                host.getHostContext().startActivity(intent)
                                host.refreshUI()
                                true
                            }
                    }
                }
            }
            "no_part" -> {
                (pref as SwitchPreferenceCompat).apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        if(newValue as Boolean){
                            preferences.edit(commit = true) {
                                putBoolean("keep_cache", false)
                            }
                        }
                        true
                    }
                }
            }
            "keep_cache" -> {
                (pref as SwitchPreferenceCompat).apply {
                    val noFragments = host.findPref("no_part") as SwitchPreferenceCompat
                    if (noFragments.isChecked) {
                        isEnabled = false
                        isChecked = false
                    } else {
                        isEnabled = true
                    }
                }
            }
            "cache_downloads" -> {
                (pref as SwitchPreferenceCompat).apply {
                    if (FileUtil.hasAllFilesAccess()) {
                        isEnabled = true
                    } else {
                        isEnabled = false
                        preferences.edit(commit = true) {
                            putBoolean(pref.key, true)
                        }
                    }
                }
            }
            "file_name_template" -> {
                pref.apply {
                    title = "${context.getString(R.string.file_name_template)} [${context.getString(R.string.video)}]"
                    summary = preferences.getString(pref.key, "%(uploader).30B - %(title).170B")

                    setOnPreferenceClickListener {
                        UiUtil.showFilenameTemplateDialog(host.getHostContext(),pref.summary.toString(), "${context.getString(
                            R.string.file_name_template)} [${context.getString(R.string.video)}]") {
                            preferences.edit(commit = true) {
                                putString(pref.key, it)
                            }
                            host.refreshUI()
                        }
                        false
                    }
                }
            }
            "file_name_template_audio" -> {
                pref.apply {
                    title = "${context.getString(R.string.file_name_template)} [${context.getString(R.string.audio)}]"
                    summary = preferences.getString(pref.key, "%(uploader).30B - %(title).170B")

                    setOnPreferenceClickListener {
                        UiUtil.showFilenameTemplateDialog(host.getHostContext(), pref.summary.toString(), "${context.getString(
                            R.string.file_name_template)} [${context.getString(R.string.audio)}]") {
                            preferences.edit(commit = true) {
                                putString(pref.key, it)
                            }
                            host.refreshUI()
                        }
                        false
                    }
                }
            }
            "clear_cache" -> {
                pref.apply {
                    val cacheSize = File(FileUtil.getCachePath(context)).walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                    val filesize  = if (cacheSize < 10000) {
                        "0B"
                    }else {
                        FileUtil.convertFileSize(cacheSize)
                    }

                    summary = "${context.resources.getString(R.string.clear_temporary_files_summary)} (${filesize}) "
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            host.hostLifecycleOwner.lifecycleScope.launch {
                                activeDownloadCount = withContext(Dispatchers.IO) {
                                    downloadViewModel.getActiveDownloadsCount()
                                }
                                if (activeDownloadCount == 0){
                                    fun clearCacheFolder(folder: File) {
                                        if (folder.exists() && folder.isDirectory) {
                                            folder.listFiles()?.forEach { file ->
                                                if (file.isDirectory) {
                                                    clearCacheFolder(file)
                                                    file.delete()
                                                } else {
                                                    file.delete()
                                                }
                                            }
                                        }
                                    }
                                    clearCacheFolder(File(FileUtil.getCachePath(context)))

                                    Snackbar.make(host.hostView!!, context.getString(R.string.cache_cleared), Snackbar.LENGTH_SHORT).show()
                                }else{
                                    Snackbar.make(host.hostView!!, context.getString(R.string.downloads_running_try_later), Snackbar.LENGTH_SHORT).show()
                                }
                                host.refreshUI()
                            }
                            true
                        }
                }
            }
            "move_cache" -> {
                pref.apply {
                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            val workRequest = OneTimeWorkRequestBuilder<MoveCacheFilesWorker>()
                                .addTag("cacheFiles")
                                .build()

                            WorkManager.Companion.getInstance(context).beginUniqueWork(
                                System.currentTimeMillis().toString(),
                                ExistingWorkPolicy.KEEP,
                                workRequest
                            ).enqueue()

                            WorkManager.Companion.getInstance(context)
                                .getWorkInfosByTagLiveData("cacheFiles")
                                .observe(host.hostLifecycleOwner){ list ->
                                    if (list == null) return@observe
                                    if (list.first() == null) return@observe

                                    if (list.first().state == WorkInfo.State.SUCCEEDED){
                                        host.refreshUI()
                                    }
                                }

                            true
                        }
                }
            }

        }
    }

    private fun changePath(host: SettingHost, p: Preference?, data: Intent?, requestCode: Int) {
        val path = data!!.data.toString()
        p!!.summary = FileUtil.formatPath(data.data.toString())
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(host.getHostContext())
        val editor = sharedPreferences.edit()
        when (requestCode) {
            MUSIC_PATH_CODE -> editor.putString("music_path", path)
            VIDEO_PATH_CODE -> editor.putString("video_path", path)
            COMMAND_PATH_CODE -> editor.putString("command_path", path)
            CACHE_PATH_CODE -> editor.putString("cache_path", path)
        }
        editor.apply()
        host.refreshUI()
    }
}