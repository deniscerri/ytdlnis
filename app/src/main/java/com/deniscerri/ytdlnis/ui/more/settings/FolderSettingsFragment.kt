package com.deniscerri.ytdlnis.ui.more.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import java.io.File


class FolderSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.directories

    private var musicPath: Preference? = null
    private var videoPath: Preference? = null
    private var commandPath: Preference? = null
    private var accessAllFiles : Preference? = null
    private var clearCache: Preference? = null

    private var fileUtil: FileUtil? = null
    private var activeDownloadCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.folders_preference, rootKey)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = preferences.edit()
        fileUtil = FileUtil()

        musicPath = findPreference("music_path")
        videoPath = findPreference("video_path")
        commandPath = findPreference("command_path")
        accessAllFiles = findPreference("access_all_files")
        clearCache = findPreference("clear_cache")

        if (preferences.getString("music_path", "")!!.isEmpty()) {
            editor.putString("music_path", getString(R.string.music_path))
        }
        if (preferences.getString("video_path", "")!!.isEmpty()) {
            editor.putString("video_path", getString(R.string.video_path))
        }
        if (preferences.getString("command_path", "")!!.isEmpty()) {
            editor.putString("command_path", getString(R.string.command_path))
        }

        if((VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
            VERSION.SDK_INT < 30) {
            accessAllFiles!!.isVisible = false
        }

        editor.apply()

        musicPath!!.summary = fileUtil?.formatPath(preferences.getString("music_path", "")!!)
        musicPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                musicPathResultLauncher.launch(intent)
                true
            }
        videoPath!!.summary = fileUtil?.formatPath(preferences.getString("video_path", "")!!)
        videoPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                videoPathResultLauncher.launch(intent)
                true
            }
        commandPath!!.summary = fileUtil?.formatPath(preferences.getString("command_path", "")!!)
        commandPath!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                commandPathResultLauncher.launch(intent)
                true
            }
        accessAllFiles!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.parse("package:" + requireContext().packageName)
                intent.data = uri
                startActivity(intent)
                true
            }

        var cacheSize = File(requireContext().cacheDir.absolutePath + "/downloads").walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
        clearCache!!.summary = "(${fileUtil!!.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
        clearCache!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                if (activeDownloadCount == 0){
                    File(requireContext().cacheDir.absolutePath + "/downloads").deleteRecursively()
                    Snackbar.make(requireView(), getString(R.string.cache_cleared), Snackbar.LENGTH_SHORT).show()
                    cacheSize = File(requireContext().cacheDir.absolutePath + "/downloads").walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                    clearCache!!.summary = "(${fileUtil!!.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
                }else{
                    Snackbar.make(requireView(), getString(R.string.downloads_running_try_later), Snackbar.LENGTH_SHORT).show()
                }
                true
            }

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

    private fun changePath(p: Preference?, data: Intent?, requestCode: Int) {
        val path = data!!.data.toString()
        p!!.summary = fileUtil?.formatPath(data.data.toString())
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = sharedPreferences.edit()
        when (requestCode) {
            MUSIC_PATH_CODE -> editor.putString("music_path", path)
            VIDEO_PATH_CODE -> editor.putString("video_path", path)
            COMMAND_PATH_CODE -> editor.putString("command_path", path)
        }
        editor.apply()
    }

    companion object {
        const val MUSIC_PATH_CODE = 33333
        const val VIDEO_PATH_CODE = 55555
        const val COMMAND_PATH_CODE = 77777
    }
}