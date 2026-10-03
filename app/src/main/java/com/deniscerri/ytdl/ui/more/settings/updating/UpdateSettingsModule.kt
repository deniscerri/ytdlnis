package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.pm.PackageManager
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.snackbar.Snackbar


object UpdateSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference,host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        when(pref.key) {
            "ytdlp_update_screen" -> {
                pref.apply {
                    val version = preferences.getString("ytdl-version", "")!!
                    val source = preferences.getString("ytdlp_source", "stable")!!

                    val channel = when(source) {
                        "stable" -> context.getString(R.string.channel_stable)
                        "nightly" -> context.getString(R.string.channel_nightly)
                        "master" -> context.getString(R.string.channel_master)
                        else -> preferences.getString("ytdlp_source_label", "")!!.ifBlank { source }
                    }
                    summary = listOf(version, channel).filter { it.isNotBlank() }.joinToString(" • ")
                    setOnPreferenceClickListener {
                        host.requestNavigate(R.id.ytdlpUpdateSettingsFragment)
                        true
                    }
                }
            }
            "app_update_screen" -> {
                pref.apply {
                    summary = listOf(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR).filter { it.isNotBlank() }.joinToString(" • ")
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
            "apk_install_method" -> {
                pref.apply {
                    setOnPreferenceChangeListener { _, newValue ->
                        var resp = true
                        if ((newValue as String) == "shizuku") {
                            ApkInstallUtil.requestShizukuPermission { granted, error ->
                                if (!granted) {
                                    Snackbar.make(host.hostView!!, error ?: "Shizuku permission not granted", Snackbar.LENGTH_LONG).show()
                                    resp = false
                                }
                            }
                        }

                        if (resp) {
                            host.findPref("apk_install_external_apk_id")?.isVisible = (newValue as String) == "external"
                            host.refreshUI()
                        }

                        resp
                    }
                }
            }
            "apk_install_external_apk_id" -> {
                pref.apply {
                    isVisible = preferences.getString("apk_install_method", "system")!! == "external"
                    val packageName = preferences.getString("apk_install_external_apk_id", "")!!

                    fun setDetails(packageName: String) {
                        if (packageName == "") {
                            summary = context.getString(R.string.notset)
                        } else {
                            try {
                                val appIcon = host.getHostContext().packageManager.getApplicationIcon(packageName)
                                val appInfo = host.getHostContext().packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                                icon = appIcon
                                summary = host.getHostContext().packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                summary = context.getString(R.string.notset)
                            }
                        }
                    }
                    setDetails(packageName)

                    onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            UiUtil.showChooseInstallerAppDialog(host.getHostContext()) {
                                preferences.edit().putString("apk_install_external_apk_id", it).apply()
                                setDetails(it)
                                host.refreshUI()
                            }
                            true
                        }
                }
            }
        }
    }
}
