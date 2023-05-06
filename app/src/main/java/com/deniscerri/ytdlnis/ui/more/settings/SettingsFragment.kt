package com.deniscerri.ytdlnis.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.Locale


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
    private var locale: ListPreference? = null
    private var concurrentFragments: SeekBarPreference? = null
    private var concurrentDownloads: SeekBarPreference? = null
    private var audioFormat: ListPreference? = null
    private var videoFormat: ListPreference? = null
    private var videoQuality: ListPreference? = null
    private var formatID: EditTextPreference? = null
    private var updateYTDL: Preference? = null
    private var formatSource: ListPreference? = null
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
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
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

        locale = findPreference("locale")
        concurrentFragments = findPreference("concurrent_fragments")
        concurrentDownloads = findPreference("concurrent_downloads")

        audioFormat = findPreference("audio_format")
        videoFormat = findPreference("video_format")
        videoQuality = findPreference("video_quality")
        formatID = findPreference("format_id")
        updateYTDL = findPreference("update_ytdl")
        formatSource = findPreference("formats_source")
        exportPreferences = findPreference("export_preferences")
        importPreferences = findPreference("import_preferences")
        ytdlVersion = findPreference("ytdl-version")
        ytdlVersion!!.summary = preferences.getString("ytdl-version", "NULL")
        version = findPreference("version")
        version!!.summary = BuildConfig.VERSION_NAME

        if(VERSION.SDK_INT < VERSION_CODES.TIRAMISU){
            val values = resources.getStringArray(R.array.language_values)
            val entries = mutableListOf<String>()
            values.forEach {
                entries.add(Locale(it).getDisplayName(Locale(it)))
            }
            language!!.entries = entries.toTypedArray()
        }else{
            language!!.isVisible = false
        }

        val packageName: String = requireContext().packageName
        val pm = requireContext().applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            ignoreBatteryOptimization!!.isVisible = false
        }


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
    }

    @SuppressLint("BatteryLife", "InlinedApi")
    private fun initListeners() {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = preferences.edit()

        if(language!!.value == null) language!!.value = Locale.getDefault().language
        language!!.summary = Locale(language!!.value).getDisplayLanguage(Locale(language!!.value))
        language!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                language!!.summary = Locale(newValue.toString()).getDisplayLanguage(Locale(newValue.toString()))
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newValue.toString()))
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
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        accent!!.summary = accent!!.entry
        accent!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                ThemeUtil.updateTheme(requireActivity() as AppCompatActivity)
                val intent = Intent(requireContext(), MainActivity::class.java)
                this.startActivity(intent)
                requireActivity().finishAffinity()
                true
            }
        highContrast!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
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
                    Snackbar.make(requireView(), getString(R.string.cache_cleared), Snackbar.LENGTH_SHORT).show()
                    cacheSize = File(requireContext().cacheDir.absolutePath + "/downloads").walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
                    clearCache!!.summary = "(${fileUtil!!.convertFileSize(cacheSize)}) ${resources.getString(R.string.clear_temporary_files_summary)}"
                }else{
                    Snackbar.make(requireView(), getString(R.string.downloads_running_try_later), Snackbar.LENGTH_SHORT).show()
                }
                true
            }

        locale!!.summary = locale!!.value
        concurrentDownloads!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(resources.getString(R.string.warning))
                    .setMessage(resources.getString(R.string.workmanager_updated))
                    .setNegativeButton(resources.getString(R.string.cancel)) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setPositiveButton(resources.getString(R.string.restart)) { _, _ ->
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

        audioFormat!!.summary = preferences.getString("audio_format", "")!!.replace("Default", getString(R.string.defaultValue))
        audioFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                audioFormat!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                true
            }
        videoFormat!!.summary = preferences.getString("video_format", "")!!.replace("Default", getString(R.string.defaultValue))
        videoFormat!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                videoFormat!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                true
            }

        videoQuality!!.summary = preferences.getString("video_quality", "")
        videoQuality!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                videoQuality!!.summary = newValue.toString()
                true
            }
        formatID!!.summary = formatID!!.text
        formatID!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                formatID!!.summary = newValue.toString()
                true
            }
        YoutubeDL.getInstance().version(context)?.let {
            editor.putString("ytdl-version", it)
            editor.apply()
            ytdlVersion!!.summary = it
        }
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    Snackbar.make(requireView(),
                        requireContext().getString(R.string.ytdl_updating_started),
                        Snackbar.LENGTH_LONG).show()
                    when (updateUtil!!.updateYoutubeDL()) {
                        YoutubeDL.UpdateStatus.DONE -> {
                            Snackbar.make(requireView(),
                                requireContext().getString(R.string.ytld_update_success),
                                Snackbar.LENGTH_LONG).show()

                            YoutubeDL.getInstance().version(context)?.let {
                                editor.putString("ytdl-version", it)
                                editor.apply()
                                ytdlVersion!!.summary = it
                            }
                        }
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> Snackbar.make(requireView(),
                            requireContext().getString(R.string.you_are_in_latest_version),
                            Snackbar.LENGTH_LONG).show()
                        else -> {
                            Snackbar.make(requireView(),
                                requireContext().getString(R.string.errored),
                                Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                true
            }
        formatSource!!.summary = preferences.getString("formats_source", "")!!.replace("Default", getString(R.string.defaultValue))
        formatSource!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                formatSource!!.summary = newValue.toString().replace("Default", getString(R.string.defaultValue))
                true
            }
        exportPreferences!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val prefs = preferences.all
                val json = buildJsonObject {
                    putJsonArray("YTDLnis_Preferences") {
                        prefs.forEach {
                            add(buildJsonObject {
                                put("key", it.key)
                                put("value", it.value.toString())
                                put("type", it.value!!::class.simpleName)
                            })
                        }
                    }
                }
                val string = jsonFormat.encodeToString(json)
                val clipboard: ClipboardManager =
                    requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setText(string)
                Snackbar.make(requireView(), getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                true
            }
        importPreferences!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val clipboard = requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip!!.getItemAt(0).text.toString()
                try{
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit(commit = true) {
                        clear()

                        val preferencesKeys = preferences.all.map { it.key }
                        jsonFormat.decodeFromString<JsonObject>(clip).run {
                            val prefs = this.jsonObject["YTDLnis_Preferences"]!!.jsonArray
                            prefs.forEach {
                                val key : String = it.jsonObject["key"].toString().replace("\"", "")
                                if (preferencesKeys.contains(key)){
                                    when(it.jsonObject["type"].toString().replace("\"", "")){
                                        "String" -> {
                                            val value = it.jsonObject["value"].toString().replace("\"", "")
                                            putString(key, value)
                                        }
                                        "Boolean" -> {
                                            val value = it.jsonObject["value"].toString().replace("\"", "").toBoolean()
                                            putBoolean(key, value)
                                        }
                                        "Int" -> {
                                            val value = it.jsonObject["value"].toString().replace("\"", "").toInt()
                                            putInt(key, value)
                                        }
                                        "HashSet" -> {
                                            val value = hashSetOf(it.jsonObject["value"].toString().replace("(\")|(\\[)|(])".toRegex(), ""))
                                            putStringSet(key, value)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    requireContext().startActivity(intent)
                    if (context is Activity) {
                        (context as Activity).finish()
                    }
                    Runtime.getRuntime().exit(0)
                }catch (e: Exception){
                    e.printStackTrace()
                }

                true
            }
        version!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        updateUtil!!.updateApp{ msg ->
                            lifecycleScope.launch(Dispatchers.Main){
                                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }

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