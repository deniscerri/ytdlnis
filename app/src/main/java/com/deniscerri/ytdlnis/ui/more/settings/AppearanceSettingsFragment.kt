package com.deniscerri.ytdlnis.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context.POWER_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
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
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.SearchHistoryItem
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.CookieViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.ThemeUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.Locale


class AppearanceSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.appearance

    private var language: ListPreference? = null
    private var theme: ListPreference? = null
    private var accent: ListPreference? = null
    private var highContrast: SwitchPreferenceCompat? = null
    private var locale: ListPreference? = null

    private var updateUtil: UpdateUtil? = null
    private var fileUtil: FileUtil? = null
    private var activeDownloadCount = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appearance_preferences, rootKey)
        updateUtil = UpdateUtil(requireContext())
        fileUtil = FileUtil()

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        language = findPreference("app_language")
        theme = findPreference("ytdlnis_theme")
        accent = findPreference("theme_accent")
        highContrast = findPreference("high_contrast")
        locale = findPreference("locale")

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

    }
}