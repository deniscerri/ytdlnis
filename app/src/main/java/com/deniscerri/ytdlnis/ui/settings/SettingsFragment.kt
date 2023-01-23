package com.deniscerri.ytdlnis.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.*
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UpdateUtil

class SettingsFragment : PreferenceFragmentCompat() {
    private var musicPath: Preference? = null
    private var videoPath: Preference? = null
    private var commandPath: Preference? = null
    private var incognito: SwitchPreferenceCompat? = null
    private var downloadCard: SwitchPreferenceCompat? = null
    private var apiKey: EditTextPreference? = null
    private var concurrentFragments: SeekBarPreference? = null
    private var concurrentDownloads: SeekBarPreference? = null
    private var limitRate: EditTextPreference? = null
    private var aria2: SwitchPreferenceCompat? = null
    private var sponsorblockFilters: MultiSelectListPreference? = null
    private var embedSubtitles: SwitchPreferenceCompat? = null
    private var embedThumbnail: SwitchPreferenceCompat? = null
    private var addChapters: SwitchPreferenceCompat? = null
    private var writeThumbnail: SwitchPreferenceCompat? = null
    private var audioFormat: ListPreference? = null
    private var videoFormat: ListPreference? = null
    private var audioQuality: SeekBarPreference? = null
    private var videoQuality: ListPreference? = null
    private var updateYTDL: Preference? = null
    private var updateApp: SwitchPreferenceCompat? = null
    private var version: Preference? = null
    private var updateUtil: UpdateUtil? = null
    private var fileUtil: FileUtil? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())
        fileUtil = FileUtil()
        initPreferences()
        initListeners()
    }

    private fun initPreferences() {
        val preferences =
            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        val editor = preferences.edit()
        musicPath = findPreference("music_path")
        videoPath = findPreference("video_path")
        commandPath = findPreference("command_path")
        incognito = findPreference("incognito")
        downloadCard = findPreference("download_card")
        apiKey = findPreference("api_key")
        concurrentFragments = findPreference("concurrent_fragments")
        concurrentDownloads = findPreference("concurrent_downloads")
        limitRate = findPreference("limit_rate")
        aria2 = findPreference("aria2")
        sponsorblockFilters = findPreference("sponsorblock_filter")
        embedSubtitles = findPreference("embed_subtitles")
        embedThumbnail = findPreference("embed_thumbnail")
        addChapters = findPreference("add_chapters")
        writeThumbnail = findPreference("write_thumbnail")
        audioFormat = findPreference("audio_format")
        videoFormat = findPreference("video_format")
        audioQuality = findPreference("audio_quality")
        videoQuality = findPreference("video_quality")
        updateYTDL = findPreference("update_ytdl")
        updateApp = findPreference("update_app")
        version = findPreference("version")
        version!!.summary = BuildConfig.VERSION_NAME
        if (preferences.getString("music_path", "")!!.isEmpty()) {
            editor.putString("music_path", getString(R.string.music_path))
        }
        if (preferences.getString("video_path", "")!!.isEmpty()) {
            editor.putString("video_path", getString(R.string.video_path))
        }
        if (preferences.getString("command_path", "")!!.isEmpty()) {
            editor.putString("command_path", getString(R.string.command_path))
        }
        editor.putBoolean("incognito", incognito!!.isChecked)
        editor.putBoolean("download_card", downloadCard!!.isChecked)
        editor.putString("api_key", apiKey!!.text)
        editor.putInt("concurrent_fragments", concurrentFragments!!.value)
        editor.putInt("concurrent_downloads", concurrentDownloads!!.value)
        editor.putString("limit_rate", limitRate!!.text)
        editor.putBoolean("aria2", aria2!!.isChecked)
        editor.putStringSet("sponsorblock_filters", sponsorblockFilters!!.values)
        editor.putBoolean("embed_subtitles", embedSubtitles!!.isChecked)
        editor.putBoolean("embed_thumbnail", embedThumbnail!!.isChecked)
        editor.putBoolean("add_chapters", addChapters!!.isChecked)
        editor.putBoolean("write_thumbnail", writeThumbnail!!.isChecked)
        editor.putString("audio_format", audioFormat!!.value)
        editor.putString("video_format", videoFormat!!.value)
        editor.putInt("audio_quality", audioQuality!!.value)
        editor.putString("video_quality", videoQuality!!.value)
        editor.putBoolean("update_app", updateApp!!.isChecked)
        editor.apply()
    }

    private fun initListeners() {
        val preferences =
            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        val editor = preferences.edit()
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
        incognito!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("incognito", enable)
                editor.apply()
                true
            }
        downloadCard!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("download_card", enable)
                editor.apply()
                true
            }
        apiKey!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("api_key", newValue.toString())
                editor.apply()
                true
            }
        concurrentFragments!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val value = newValue.toString().toInt()
                editor.putInt("concurrent_fragments", value)
                editor.apply()
                true
            }
        concurrentDownloads!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val value = newValue.toString().toInt()
                editor.putInt("concurrent_downloads", value)
                editor.apply()
                true
            }
        limitRate!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("limit_rate", newValue.toString())
                editor.apply()
                true
            }
        aria2!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("aria2", enable)
                editor.apply()
                true
            }
        sponsorblockFilters!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                sponsorblockFilters!!.values = newValue as Set<String?>?
                editor.putStringSet("sponsorblock_filters", newValue)
                editor.apply()
                true
            }
        embedSubtitles!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val embed = newValue as Boolean
                editor.putBoolean("embed_subtitles", embed)
                editor.apply()
                true
            }
        embedThumbnail!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val embed = newValue as Boolean
                editor.putBoolean("embed_thumbnail", embed)
                editor.apply()
                true
            }
        addChapters!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val add = newValue as Boolean
                editor.putBoolean("add_chapters", add)
                editor.apply()
                true
            }
        writeThumbnail!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val write = newValue as Boolean
                editor.putBoolean("write_thumbnail", write)
                editor.apply()
                true
            }
        audioFormat!!.summary = preferences.getString("audio_format", "")
        audioFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("audio_format", newValue.toString())
                audioFormat!!.summary = newValue.toString()
                editor.apply()
                true
            }
        videoFormat!!.summary = preferences.getString("video_format", "")
        videoFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("video_format", newValue.toString())
                videoFormat!!.summary = newValue.toString()
                editor.apply()
                true
            }
        audioQuality!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putInt("audio_format", newValue.toString().toInt())
                editor.apply()
                true
            }
        videoQuality!!.summary = preferences.getString("video_quality", "")
        videoQuality!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("video_quality", newValue.toString())
                videoQuality!!.summary = newValue.toString()
                editor.apply()
                true
            }
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                updateUtil!!.updateYoutubeDL()
                true
            }
        updateApp!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("update_app", enable)
                editor.apply()
                true
            }
        version!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                if (!updateUtil!!.updateApp()) {
                    Toast.makeText(context, R.string.you_are_in_latest_version, Toast.LENGTH_SHORT)
                        .show()
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
        val sharedPreferences =
            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
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
        private const val TAG = "SettingsFragment"
    }
}