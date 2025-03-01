package com.deniscerri.ytdl.ui.more.settings

import android.app.Activity
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class FolderSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.directories

    private var musicPath: Preference? = null
    private var videoPath: Preference? = null
    private var commandPath: Preference? = null
    private var cachePath: Preference? = null
    private var accessAllFiles : Preference? = null
    private var noFragments: SwitchPreferenceCompat? = null
    private var keepFragments: SwitchPreferenceCompat? = null
    private var cacheDownloads : Preference? = null
    private var audioFilenameTemplate : Preference? = null
    private var videoFilenameTemplate : Preference? = null
    private var clearCache: Preference? = null
    private var moveCache: Preference? = null
    private lateinit var preferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var downloadViewModel: DownloadViewModel
    private var activeDownloadCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.folders_preference, rootKey)

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]

        musicPath = findPreference("music_path")
        videoPath = findPreference("video_path")
        commandPath = findPreference("command_path")
        cachePath = findPreference("cache_path")
        accessAllFiles = findPreference("access_all_files")
        noFragments = findPreference("no_part")
        keepFragments = findPreference("keep_cache")
        cacheDownloads = findPreference("cache_downloads")
        videoFilenameTemplate = findPreference("file_name_template")
        audioFilenameTemplate = findPreference("file_name_template_audio")
        clearCache = findPreference("clear_cache")
        moveCache = findPreference("move_cache")

        if (preferences.getString("music_path", "")!!.isEmpty()) {
            editor.putString("music_path", FileUtil.getDefaultAudioPath()).apply()
        }
        if (preferences.getString("video_path", "")!!.isEmpty()) {
            editor.putString("video_path", FileUtil.getDefaultVideoPath()).apply()
        }
        if (preferences.getString("command_path", "")!!.isEmpty()) {
            editor.putString("command_path", FileUtil.getDefaultCommandPath()).apply()
        }
        if (preferences.getString("cache_path", "")!!.isEmpty()) {
            editor.putString("cache_path", FileUtil.getCachePath(requireContext())).apply()
        }

        if (FileUtil.hasAllFilesAccess()) {
            accessAllFiles!!.isVisible = false
            cacheDownloads!!.isEnabled = true
        }else{
            editor.putBoolean("cache_downloads", true).apply()
            cacheDownloads!!.isEnabled = false
        }

        musicPath!!.summary = FileUtil.formatPath(preferences.getString("music_path", "")!!)
        musicPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                musicPathResultLauncher.launch(intent)
                true
            }
        videoPath!!.summary = FileUtil.formatPath(preferences.getString("video_path", "")!!)
        videoPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                videoPathResultLauncher.launch(intent)
                true
            }
        commandPath!!.summary = FileUtil.formatPath(preferences.getString("command_path", "")!!)
        commandPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                commandPathResultLauncher.launch(intent)
                true
            }

        cachePath!!.summary = FileUtil.formatPath(preferences.getString("cache_path", FileUtil.getCachePath(requireContext()))!!)
        cachePath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.cache_directory), getString(R.string.cache_directory_warning)) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    cachePathResultLauncher.launch(intent)
                }
                true
            }

        if(VERSION.SDK_INT >= 30){
            accessAllFiles!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.parse("package:" + requireContext().packageName)
                    intent.data = uri
                    startActivity(intent)
                    true
                }
        }

        if (noFragments!!.isChecked) {
            editor.putBoolean("keep_cache", false).apply()
            keepFragments!!.isChecked = false
            keepFragments!!.isEnabled = false
        }
        noFragments!!.setOnPreferenceChangeListener { _, newValue ->
            if(newValue as Boolean){
                editor.putBoolean("keep_cache", false).apply()
                keepFragments!!.isChecked = false
                keepFragments!!.isEnabled = false
            }else{
                keepFragments!!.isEnabled = true
            }
            true
        }

        videoFilenameTemplate?.title = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"
        videoFilenameTemplate?.summary = preferences.getString("file_name_template", "%(uploader).30B - %(title).170B")
        audioFilenameTemplate?.title = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
        audioFilenameTemplate?.summary = preferences.getString("file_name_template_audio", "%(uploader).30B - %(title).170B")

        videoFilenameTemplate?.setOnPreferenceClickListener {
            UiUtil.showFilenameTemplateDialog(requireActivity(), videoFilenameTemplate?.summary.toString() ?: "", "${getString(R.string.file_name_template)} [${getString(R.string.video)}]") {
                editor.putString("file_name_template", it).apply()
                videoFilenameTemplate?.summary = it
            }
            false
        }

        audioFilenameTemplate?.setOnPreferenceClickListener {
            UiUtil.showFilenameTemplateDialog(requireActivity(), audioFilenameTemplate?.summary.toString() ?: "", "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]") {
                editor.putString("file_name_template_audio", it).apply()
                audioFilenameTemplate?.summary = it
            }
            false
        }

        var cacheSize = File(FileUtil.getCachePath(requireContext())).walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
        clearCache!!.summary = "(${FileUtil.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
        clearCache!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    activeDownloadCount = withContext(Dispatchers.IO){
                        downloadViewModel.getActiveDownloadsCount()
                    }
                    if (activeDownloadCount == 0){
                        File(FileUtil.getCachePath(requireContext())).deleteRecursively()
                        Snackbar.make(requireView(), getString(R.string.cache_cleared), Snackbar.LENGTH_SHORT).show()
                        cacheSize = File(FileUtil.getCachePath(requireContext())).walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                        clearCache!!.summary = "(${FileUtil.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
                    }else{
                        Snackbar.make(requireView(), getString(R.string.downloads_running_try_later), Snackbar.LENGTH_SHORT).show()
                    }
                }
                true
            }

        moveCache!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val workRequest = OneTimeWorkRequestBuilder<MoveCacheFilesWorker>()
                    .addTag("cacheFiles")
                    .build()

                WorkManager.getInstance(requireContext()).beginUniqueWork(
                    System.currentTimeMillis().toString(),
                    ExistingWorkPolicy.KEEP,
                    workRequest
                ).enqueue()

                WorkManager.getInstance(requireContext())
                    .getWorkInfosByTagLiveData("cacheFiles")
                    .observe(viewLifecycleOwner){ list ->
                        if (list == null) return@observe
                        if (list.first() == null) return@observe

                        if (list.first().state == WorkInfo.State.SUCCEEDED){
                            cacheSize = File(FileUtil.getCachePath(requireContext())).walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                            clearCache!!.summary = "(${FileUtil.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
                        }
                    }

                true
            }


        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, R.xml.folders_preference)
                requireActivity().recreate()
            }
            true
        }

    }

    override fun onResume() {
        if((VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
            VERSION.SDK_INT < 30) {
            accessAllFiles!!.isVisible = false
            cacheDownloads!!.isEnabled = true
        }else{
            editor.putBoolean("cache_downloads", true).apply()
            cacheDownloads!!.isEnabled = false
        }
        super.onResume()
    }

    private var musicPathResultLauncher = registerForActivityResult(
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
            changePath(musicPath, result.data, MUSIC_PATH_CODE)
        }
    }
    private var videoPathResultLauncher = registerForActivityResult(
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
            changePath(videoPath, result.data, VIDEO_PATH_CODE)
        }
    }
    private var commandPathResultLauncher = registerForActivityResult(
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
            changePath(commandPath, result.data, COMMAND_PATH_CODE)
        }
    }
    private var cachePathResultLauncher = registerForActivityResult(
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
            changePath(cachePath, result.data, CACHE_PATH_CODE)
        }
    }

    private fun changePath(p: Preference?, data: Intent?, requestCode: Int) {
        val path = data!!.data.toString()
        p!!.summary = FileUtil.formatPath(data.data.toString())
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = sharedPreferences.edit()
        when (requestCode) {
            MUSIC_PATH_CODE -> editor.putString("music_path", path)
            VIDEO_PATH_CODE -> editor.putString("video_path", path)
            COMMAND_PATH_CODE -> editor.putString("command_path", path)
            CACHE_PATH_CODE -> editor.putString("cache_path", path)
        }
        editor.apply()
    }

    companion object {
        const val MUSIC_PATH_CODE = 33333
        const val VIDEO_PATH_CODE = 55555
        const val COMMAND_PATH_CODE = 77777
        const val CACHE_PATH_CODE = 99999
    }
}