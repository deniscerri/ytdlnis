package com.deniscerri.ytdlnis.ui.more.settings

import android.app.Activity
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*


class SettingsFragment : PreferenceFragmentCompat() {
    private var language: ListPreference? = null
    private var theme: ListPreference? = null
    private var accent: ListPreference? = null
    private var highContrast: SwitchPreferenceCompat? = null
    private var ignoreBatteryOptimization: Preference? = null
    private var musicPath: Preference? = null
    private var videoPath: Preference? = null
    private var commandPath: Preference? = null
    private var accessAllFiles : Preference? = null
    private var clearCache: Preference? = null
    private var incognito: SwitchPreferenceCompat? = null
    private var preferredDownloadType : ListPreference? = null
    private var searchEngine : ListPreference? = null
    private var startDestination : ListPreference? = null
    private var downloadCard: SwitchPreferenceCompat? = null
    private var quickDownload: SwitchPreferenceCompat? = null
    private var meteredNetwork: SwitchPreferenceCompat? = null
    private var apiKey: EditTextPreference? = null
    private var homeRecommendations: SwitchPreferenceCompat? = null
    private var concurrentFragments: SeekBarPreference? = null
    private var concurrentDownloads: SeekBarPreference? = null
    private var limitRate: EditTextPreference? = null
    private var aria2: SwitchPreferenceCompat? = null
    private var logDownloads: SwitchPreferenceCompat? = null
    private var sponsorblockFilters: MultiSelectListPreference? = null
    private var filenameTemplate: EditTextPreference? = null
    private var restrictFilenames: SwitchPreferenceCompat? = null
    private var mtime: SwitchPreferenceCompat? = null
    private var embedSubtitles: SwitchPreferenceCompat? = null
    private var writeSubtitles: SwitchPreferenceCompat? = null
    private var embedThumbnail: SwitchPreferenceCompat? = null
    private var addChapters: SwitchPreferenceCompat? = null
    private var writeThumbnail: SwitchPreferenceCompat? = null
    private var audioFormat: ListPreference? = null
    private var videoFormat: ListPreference? = null
    private var audioQuality: SeekBarPreference? = null
    private var videoQuality: ListPreference? = null
    private var formatID: EditTextPreference? = null
    private var updateYTDL: Preference? = null
    private var updateNightlyYTDL: SwitchPreferenceCompat? = null
    private var updateFormats: SwitchPreferenceCompat? = null
    private var formatSource: ListPreference? = null
    private var updateApp: SwitchPreferenceCompat? = null
    private var exportPreferences : Preference? = null
    private var importPreferences : Preference? = null
    private var ytdlVersion: Preference? = null
    private var version: Preference? = null


    private var updateUtil: UpdateUtil? = null
    private var fileUtil: FileUtil? = null
    private var activeDownloadCount = 0

    private val jsonFormat = Json { prettyPrint = true }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())
        fileUtil = FileUtil()

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        initPreferences()
        initListeners()
    }

    private fun initPreferences() {
        val preferences =
            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        val editor = preferences.edit()
        language = findPreference("app_language")
        theme = findPreference("ytdlnis_theme")
        accent = findPreference("theme_accent")
        highContrast = findPreference("high_contrast")
        ignoreBatteryOptimization = findPreference("ignore_battery")
        musicPath = findPreference("music_path")
        videoPath = findPreference("video_path")
        commandPath = findPreference("command_path")
        accessAllFiles = findPreference("access_all_files")
        clearCache = findPreference("clear_cache")
        incognito = findPreference("incognito")
        preferredDownloadType = findPreference("preferred_download_type")
        searchEngine = findPreference("search_engine")
        startDestination = findPreference("start_destination")
        downloadCard = findPreference("download_card")
        quickDownload = findPreference("quick_download")
        meteredNetwork = findPreference("metered_networks")
        apiKey = findPreference("api_key")
        homeRecommendations = findPreference("home_recommendations")
        concurrentFragments = findPreference("concurrent_fragments")
        concurrentDownloads = findPreference("concurrent_downloads")
        limitRate = findPreference("limit_rate")
        aria2 = findPreference("aria2")
        logDownloads = findPreference("log_downloads")
        sponsorblockFilters = findPreference("sponsorblock_filters")
        mtime = findPreference("mtime")
        embedSubtitles = findPreference("embed_subtitles")
        writeSubtitles = findPreference("write_subtitles")
        filenameTemplate = findPreference("file_name_template")
        restrictFilenames = findPreference("restrict_filenames")
        embedThumbnail = findPreference("embed_thumbnail")
        addChapters = findPreference("add_chapters")
        writeThumbnail = findPreference("write_thumbnail")
        audioFormat = findPreference("audio_format")
        videoFormat = findPreference("video_format")
        audioQuality = findPreference("audio_quality")
        videoQuality = findPreference("video_quality")
        formatID = findPreference("format_id")
        updateYTDL = findPreference("update_ytdl")
        updateNightlyYTDL = findPreference("nightly_ytdl")
        updateFormats = findPreference("update_formats")
        formatSource = findPreference("formats_source")
        updateApp = findPreference("update_app")
        exportPreferences = findPreference("export_preferences")
        importPreferences = findPreference("import_preferences")
        ytdlVersion = findPreference("ytdl-version")
        ytdlVersion!!.summary = preferences.getString("ytdl-version", "NULL")
        version = findPreference("version")
        version!!.summary = BuildConfig.VERSION_NAME

        val values = resources.getStringArray(R.array.language_values)
        val entries = mutableListOf<String>()
        values.forEach {
            entries.add(Locale(it).getDisplayName(Locale(it)))
        }
        language!!.entries = entries.toTypedArray()
        val lang = if (values.contains(Locale.getDefault().language)) Locale.getDefault().language else "en"
        editor.putString("app_language", lang)

        val packageName: String = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ignoreBatteryOptimization!!.isVisible = false
        }

        editor.putString("ytdlnis_theme", theme!!.value)
        editor.putString("theme_accent", accent!!.value)
        editor.putBoolean("high_contrast", highContrast!!.isChecked)

        if (preferences.getString("music_path", "")!!.isEmpty()) {
            editor.putString("music_path", getString(R.string.music_path))
        }
        if (preferences.getString("video_path", "")!!.isEmpty()) {
            editor.putString("video_path", getString(R.string.video_path))
        }
        if (preferences.getString("command_path", "")!!.isEmpty()) {
            editor.putString("command_path", getString(R.string.command_path))
        }

        if((Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
                Build.VERSION.SDK_INT < 30) {
            accessAllFiles!!.isVisible = false
        }

        editor.putBoolean("incognito", incognito!!.isChecked)
        editor.putString("preferred_download_type", preferredDownloadType!!.value)
        editor.putString("search_engine", searchEngine!!.value)
        editor.putString("start_destination", startDestination!!.value)
        editor.putBoolean("download_card", downloadCard!!.isChecked)
        editor.putBoolean("quick_download", quickDownload!!.isChecked)
        editor.putBoolean("metered_networks", meteredNetwork!!.isChecked)
        editor.putString("api_key", apiKey!!.text)
        editor.putBoolean("home_recommendations", homeRecommendations!!.isChecked)
        editor.putInt("concurrent_fragments", concurrentFragments!!.value)
        editor.putInt("concurrent_downloads", concurrentDownloads!!.value)
        editor.putString("limit_rate", limitRate!!.text)
        editor.putBoolean("aria2", aria2!!.isChecked)
        editor.putBoolean("log_downloads", logDownloads!!.isChecked)
        editor.putStringSet("sponsorblock_filters", sponsorblockFilters!!.values)
        editor.putString("file_name_template", filenameTemplate!!.text)
        editor.putBoolean("restrict_filenames", restrictFilenames!!.isChecked)
        editor.putBoolean("mtime", mtime!!.isChecked)
        editor.putBoolean("embed_subtitles", embedSubtitles!!.isChecked)
        editor.putBoolean("write_subtitles", writeSubtitles!!.isChecked)
        editor.putBoolean("embed_thumbnail", embedThumbnail!!.isChecked)
        editor.putBoolean("add_chapters", addChapters!!.isChecked)
        editor.putBoolean("write_thumbnail", writeThumbnail!!.isChecked)
        editor.putString("audio_format", audioFormat!!.value)
        editor.putString("video_format", videoFormat!!.value)
        editor.putInt("audio_quality", audioQuality!!.value)
        editor.putString("video_quality", videoQuality!!.value)
        editor.putString("format_id", formatID!!.text)
        editor.putBoolean("nightly_ytdl", updateNightlyYTDL!!.isChecked)
        editor.putBoolean("update_formats", updateFormats!!.isChecked)
        editor.putString("formats_source", formatSource!!.value)
        editor.putBoolean("update_app", updateApp!!.isChecked)
        editor.apply()
    }

    private fun initListeners() {
        val preferences =
            requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        val editor = preferences.edit()
        if(language!!.value == null) language!!.value = Locale.getDefault().language
        language!!.summary = Locale(language!!.value).getDisplayLanguage(Locale(language!!.value))
        language!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("app_language", newValue.toString())
                language!!.summary = Locale(newValue.toString()).getDisplayLanguage(Locale(newValue.toString()))
                editor.apply()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
                if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) requireActivity().recreate()
                true
            }

        theme!!.summary = theme!!.entry
        theme!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                when(newValue){
                    "System" -> {
                        theme!!.summary = getString(R.string.system)
                    }
                    "Dark" -> {
                        theme!!.summary = getString(R.string.dark)
                    }
                    else -> {
                        theme!!.summary = getString(R.string.light)
                    }
                }
                editor.putString("ytdlnis_theme", newValue.toString())
                editor.commit()
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)

                val intent = Intent(requireContext(), MainActivity::class.java)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        accent!!.summary = accent!!.entry
        accent!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("theme_accent", newValue.toString())
                editor.apply().apply {
                    accent!!.summary = accent!!.entry
                }
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)

                val intent = Intent(requireContext(), MainActivity::class.java)
                this.startActivity(intent)
                requireActivity().finishAffinity()

                true
            }
        highContrast!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("high_contrast", enable)
                editor.apply()

                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                this.startActivity(intent)
                requireActivity().finishAffinity()

                true
            }
        ignoreBatteryOptimization!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
                true
            }

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
                    Toast.makeText(requireContext(), getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                    cacheSize = File(requireContext().cacheDir.absolutePath + "/downloads").walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                    clearCache!!.summary = "(${fileUtil!!.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
                }else{
                    Toast.makeText(requireContext(), getString(R.string.downloads_running_try_later), Toast.LENGTH_SHORT).show()
                }
                true
            }
        incognito!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("incognito", enable)
                editor.apply()
                true
            }
        preferredDownloadType!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("preferred_download_type", newValue.toString())
                editor.apply()
                true
            }
        searchEngine!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("search_engine", newValue.toString())
                editor.apply()
                true
            }
        startDestination!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("start_destination", newValue.toString())
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
        quickDownload!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("quick_download", enable)
                editor.apply()
                true
            }
        meteredNetwork!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("metered_networks", enable)
                editor.apply()
                true
            }
        apiKey!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("api_key", newValue.toString())
                editor.apply()
                true
            }
        homeRecommendations!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("home_recommendations", enable)
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

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(resources.getString(R.string.warning))
                    .setMessage(resources.getString(R.string.workmanager_updated))
                    .setNegativeButton(resources.getString(R.string.cancel)) { dialog, which ->
                        dialog.cancel()
                    }
                    .setPositiveButton(resources.getString(R.string.restart)) { dialog, which ->
                        val intent = Intent(context, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        requireContext().startActivity(intent)
                        if (context is Activity) {
                            (context as Activity).finish()
                        }
                        Runtime.getRuntime().exit(0)
                    }
                    .show()

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
        logDownloads!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("log_downloads", enable)
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
        filenameTemplate!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("file_name_template", newValue.toString())
                editor.apply()
                true
            }
        restrictFilenames!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val embed = newValue as Boolean
                editor.putBoolean("restrict_filenames", embed)
                editor.apply()
                true
            }
        mtime!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val embed = newValue as Boolean
                editor.putBoolean("mtime", embed)
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
        writeSubtitles!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val embed = newValue as Boolean
                editor.putBoolean("write_subtitles", embed)
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
        audioFormat!!.summary = preferences.getString("audio_format", "")!!.replace("Default", getString(R.string.defaultValue))
        audioFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("audio_format", newValue.toString())
                audioFormat!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                editor.apply()
                true
            }
        videoFormat!!.summary = preferences.getString("video_format", "")!!.replace("Default", getString(R.string.defaultValue))
        videoFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("video_format", newValue.toString())
                videoFormat!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                editor.apply()
                true
            }
        audioQuality!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putInt("audio_quality", newValue.toString().toInt())
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
        formatID!!.summary = formatID!!.text
        formatID!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                formatID!!.summary = newValue.toString()
                editor.putString("format_id", newValue.toString())
                editor.apply()
                true
            }
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    when (updateUtil!!.updateYoutubeDL()) {
                        YoutubeDL.UpdateStatus.DONE -> {
                            Toast.makeText(
                                context,
                                requireContext().getString(R.string.ytld_update_success),
                                Toast.LENGTH_LONG
                            ).show()
                            YoutubeDL.getInstance().version(context)?.let {
                                editor.putString("ytdl-version", it)
                                editor.apply()
                                ytdlVersion!!.summary = it
                            }
                        }
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> Toast.makeText(
                            context,
                            requireContext().getString(R.string.you_are_in_latest_version),
                            Toast.LENGTH_LONG
                        ).show()
                        else -> Toast.makeText(context, getString(R.string.errored), Toast.LENGTH_LONG).show()
                    }
                }
                true
            }
        updateFormats!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener {  _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("update_formats", enable)
                editor.apply()
                true
            }
        formatSource!!.summary = preferences.getString("formats_source", "")!!.replace("Default", getString(R.string.defaultValue))
        formatSource!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                editor.putString("formats_source", newValue.toString())
                formatSource!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                editor.apply()
                true
            }
        updateNightlyYTDL!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener {  _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("nightly_ytdl", enable)
                editor.apply()
                true
            }
        updateApp!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val enable = newValue as Boolean
                editor.putBoolean("update_app", enable)
                editor.apply()
                true
            }
//        exportPreferences!!.onPreferenceClickListener =
//            Preference.OnPreferenceClickListener {
//                val prefs = preferences.all
//                val json = buildJsonObject {
//                    putJsonArray("YTDLnis_Preferences") {
//                        prefs.forEach {
//                            add(buildJsonObject {
//                                put("key", it.key)
//                                put("value", it.value.toString())
//                                put("type", it.value!!::class.simpleName)
//                            })
//                        }
//                    }
//                }
//                val string = jsonFormat.encodeToString(json)
//                val clipboard: ClipboardManager =
//                    requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
//                clipboard.setText(string)
//                true
//            }
//        importPreferences!!.onPreferenceClickListener =
//            Preference.OnPreferenceClickListener {
//                val clipboard = requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
//                val clip = clipboard.primaryClip!!.getItemAt(0).text.toString()
//                try{
//                    val preferencesKeys = preferences.all.map { it.key }
//                    var count = 0
//                    jsonFormat.decodeFromString<JsonObject>(clip).run {
//                        val prefs = this.jsonObject["YTDLnis_Preferences"]!!.jsonArray
//                        prefs.forEach {
//                            val key : String = it.jsonObject["key"].toString().replace("\"", "")
//                            if (preferencesKeys.contains(key)){
//                                when(it.jsonObject["type"].toString().replace("\"", "")){
//                                    "String" -> {
//                                        val value = it.jsonObject["value"].toString().replace("\"", "")
//                                        Log.e("aa", value.toString())
//                                        editor.putString(key, value)
//                                        editor.apply()
//                                    }
//                                    "Boolean" -> {
//                                        val value = it.jsonObject["value"].toString().replace("\"", "").toBoolean()
//                                        Log.e("aa", value.toString())
//                                        editor.putBoolean(key, value)
//                                        editor.apply()
//                                    }
//                                    "Int" -> {
//                                        val value = it.jsonObject["value"].toString().replace("\"", "").toInt()
//                                        Log.e("aa", value.toString())
//                                        editor.putInt(key, value)
//                                        editor.apply()
//                                    }
//                                    "HashSet" -> {
//                                        val value = hashSetOf(it.jsonObject["value"].toString().replace("(\")|(\\[)|(])".toRegex(), ""))
//                                        Log.e("aa", value.toString())
//                                        editor.putStringSet(key, value)
//                                        editor.apply()
//                                    }
//                                }
//                                count++
//                            }
//                        }
//                    }
//                    Toast.makeText(requireContext(), "${getString(R.string.items_imported)} (${count})", Toast.LENGTH_LONG).show()
//                }catch (e: Exception){
//                    e.printStackTrace()
//                }
//
//                true
//            }
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