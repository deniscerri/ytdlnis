package com.deniscerri.ytdl.ui.more.settings.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.TemporaryFilesUtil
import com.deniscerri.ytdl.util.UiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.HashSet

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
            "save_subdirectory" -> {
                pref.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        val list = newValue as HashSet<String>

                        if (list.isEmpty()) {
                            preferences.edit(commit = true) {
                                putBoolean("trim_filenames", false)
                            }
                        }
                        host.findPref("trim_filenames")?.apply {
                            isEnabled = list.isEmpty()
                            if (list.isNotEmpty()){
                                (this as SwitchPreferenceCompat).isChecked = false
                            }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "trim_filenames" -> {
                pref.apply {
                    isEnabled = preferences.getStringSet("save_subdirectory", setOf())!!.isEmpty()
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
                        (host.findPref("keep_cache") as? SwitchPreferenceCompat)?.apply {
                            if (newValue) {
                                isEnabled = false
                                isChecked = false
                            } else {
                                isEnabled = true
                            }
                        }
                        host.refreshUI()
                        true
                    }
                }
            }
            "keep_cache" -> {
                (pref as SwitchPreferenceCompat).apply {
                    val noFragments = preferences.getBoolean("no_part", false)
                    if (noFragments) {
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
                            summary = it
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
                            summary = it
                            host.refreshUI()
                        }
                        false
                    }
                }
            }
            "temporary_files" -> {
                pref.apply {
                    host.hostLifecycleOwner.lifecycleScope.launch {
                        val total = withContext(Dispatchers.IO) {
                            TemporaryFilesUtil.Category.values().sumOf { TemporaryFilesUtil.getSize(context, it) }
                        }
                        summary = TemporaryFilesUtil.formatSize(total)
                        host.refreshUI()
                    }
                    setOnPreferenceClickListener {
                        host.requestNavigate(R.id.temporaryFilesFragment)
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