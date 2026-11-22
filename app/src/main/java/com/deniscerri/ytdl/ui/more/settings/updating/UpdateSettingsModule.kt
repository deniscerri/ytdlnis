package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.pm.PackageManager
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.background.UpdateCheckWorker
import com.google.android.material.snackbar.Snackbar
import java.io.File


object UpdateSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference,host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        when(pref.key) {
            "ytdlp_update_screen" -> {
                pref.apply {
                    val version = preferences.getString("ytdl-version", "")!!
                    val source = preferences.getString("ytdlp_source", "nightly")!!

                    val channel = when(source) {
                        "stable" -> context.getString(R.string.channel_stable)
                        "nightly" -> context.getString(R.string.channel_nightly)
                        "master" -> context.getString(R.string.channel_master)
                        else -> preferences.getString("ytdlp_source_label", "")!!.ifBlank { source }
                    }
                    summary = "$version (${channel})"
                    setOnPreferenceClickListener {
                        host.requestNavigate(R.id.ytdlpUpdateSettingsFragment)
                        true
                    }
                }
            }
            "app_update_screen" -> {
                pref.apply {
                    summary = "${BuildConfig.VERSION_NAME} (${File(context.applicationInfo.nativeLibraryDir).name}) (${BuildConfig.FLAVOR})"
                    setOnPreferenceClickListener {
                        host.requestNavigate(R.id.appUpdateSettingsFragment)
                        true
                    }
                }
            }
            "packages" -> {
                pref.apply {
                    summary = "Python, FFmpeg, Aria2c, NodeJS, Deno"
                    setOnPreferenceClickListener {
                        host.requestNavigate(R.id.packagesFragment)
                        false
                    }
                }
            }
            UpdateCheckWorker.PREF_ENABLED -> {
                pref.setOnPreferenceChangeListener { _, newValue ->
                    preferences.edit().putBoolean(UpdateCheckWorker.PREF_ENABLED, newValue as Boolean).commit()
                    UpdateCheckWorker.sync(context)
                    true
                }
            }
            UpdateCheckWorker.PREF_TYPES -> {
                (pref as MultiSelectListPreference).apply {
                    // app updates are only offered by the GitHub build
                    if (BuildConfig.FLAVOR != "github") {
                        entries = entries.drop(1).toTypedArray()
                        entryValues = entryValues.drop(1).toTypedArray()
                    }
                    fun setSummary(selected: Set<String>) {
                        summary = entryValues.indices.filter { selected.contains(entryValues[it]) }
                            .joinToString(", ") { entries[it] }
                            .ifEmpty { context.getString(R.string.notset) }
                    }
                    setSummary(values)
                    setOnPreferenceChangeListener { _, newValue ->
                        @Suppress("UNCHECKED_CAST")
                        setSummary(newValue as Set<String>)
                        true
                    }
                }
            }
            "apk_install_method" -> {
                pref.apply {
                    summary = ApkInstallUtil.getSelectedInstallerLabel(context)
                    setOnPreferenceClickListener {
                        ApkInstallUtil.showInstallerPickerDialog(host.getHostContext()) { picked ->
                            if (picked) summary = ApkInstallUtil.getSelectedInstallerLabel(context)
                        }
                        true
                    }
                }
            }
        }
    }
}
