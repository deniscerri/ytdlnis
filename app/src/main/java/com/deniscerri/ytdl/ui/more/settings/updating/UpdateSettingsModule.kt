package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.PackageManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.ApkInstallUtil.REQUEST_CODE_SHIZUKU
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File


object UpdateSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference,host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val updateUtil = UpdateUtil(context)
        val ytdlpViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[YTDLPViewModel::class.java]
        val settingsViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[SettingsViewModel::class.java]

        val canUpdateApp = BuildConfig.FLAVOR == "github"

        when(pref.key) {
            "ytdlp_source_label" -> {
                pref.apply {
                    summary = preferences.getString("ytdlp_source_label", "")!!.ifEmpty { context.getString(R.string.update_ytdl_stable) }
                    setOnPreferenceClickListener {
                        UiUtil.showYTDLSourceBottomSheet(host.getHostContext(), preferences) { t, r ->
                            summary = t
                            preferences.edit().putString("ytdlp_source", r).apply()
                            preferences.edit().putString("ytdlp_source_label", t).apply()
                            val ytdlVersionPreference = host.findPref("ytdl-version")!!
                            initYTDLUpdate(context, host, updateUtil, ytdlpViewModel, preferences, ytdlVersionPreference)
                        }
                        true
                    }
                }
            }
            "ytdl-version" -> {
                pref.apply {
                    host.hostLifecycleOwner.lifecycleScope.launch {
                        summary = context.getString(R.string.loading)
                        summary = withContext(Dispatchers.IO){
                            ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
                        }
                        if (summary?.isBlank() == true) {
                            setYTDLPVersion(context, host, ytdlpViewModel, preferences, pref)
                        }
                        setOnPreferenceClickListener {
                            initYTDLUpdate(context, host, updateUtil, ytdlpViewModel, preferences, pref)
                            true
                        }
                    }
                }
            }
            "update_ytdl" -> {
                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val ytdlVersionPreference = host.findPref("ytdl-version")!!
                        initYTDLUpdate(context, host, updateUtil, ytdlpViewModel, preferences, ytdlVersionPreference)
                        true
                    }
            }
            "changelog" -> {
                pref.setOnPreferenceClickListener {
                    host.requestNavigate(R.id.changeLogFragment)
                    false
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
            "version" -> {
                pref.apply {
                    val nativeLibraryDir = context.applicationInfo?.nativeLibraryDir
                    summary = "${BuildConfig.VERSION_NAME} (${nativeLibraryDir?.split("/lib/")?.get(1)}) (${BuildConfig.FLAVOR})"

                    if (canUpdateApp) {
                        onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                host.hostLifecycleOwner.lifecycleScope.launch{
                                    val updateUtil = UpdateUtil(context)
                                    val res = withContext(Dispatchers.IO){
                                        updateUtil.tryGetNewVersion()
                                    }
                                    if (res.isFailure) {
                                        if (host.hostView != null && host.hostView!!.isAttachedToWindow) {
                                            Snackbar.make(host.hostView!!, res.exceptionOrNull()?.message ?: context.getString(R.string.network_error), Snackbar.LENGTH_LONG).show()
                                        }
                                    }else{
                                        if (preferences.getBoolean("automatic_backup", false)) {
                                            withContext(Dispatchers.IO){
                                                settingsViewModel.backup()
                                            }
                                        }
                                        UiUtil.showNewAppUpdateDialog(res.getOrNull()!!, host.getHostContext(), updateUtil, host.hostLifecycleOwner, preferences, host.getAppInstallLauncher())
                                    }
                                }
                                true
                            }
                    }
                }
            }
            "update_app" -> {
                pref.apply {
                    isVisible = canUpdateApp
                }
            }
            "update_beta" -> {
                pref.apply {
                    isVisible = canUpdateApp
                }
            }
        }
    }

    private fun setYTDLPVersion(
        context: Context,
        host: SettingHost,
        ytdlpViewModel: YTDLPViewModel,
        preferences: SharedPreferences,
        pref: Preference
    ) {
       host.hostLifecycleOwner.lifecycleScope.launch {
            pref.summary = context.getString(R.string.loading)
            val version = withContext(Dispatchers.IO){
                ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
            }
            preferences.edit(commit = true) {
                putString("ytdl-version", version)
            }
            pref.summary = version
            host.refreshUI()
        }
    }

    private fun initYTDLUpdate(
        context: Context,
        host: SettingHost,
        updateUtil: UpdateUtil,
        ytdlpViewModel: YTDLPViewModel,
        preferences: SharedPreferences,
        ytdlVersionPreference: Preference,
        channel: String? = null
    ) = host.hostLifecycleOwner.lifecycleScope.launch {
        val view = host.hostView

        if (view != null && view.isAttachedToWindow) {
            Snackbar.make(view, context.getString(R.string.ytdl_updating_started), Snackbar.LENGTH_LONG).show()
        }

        runCatching {
            val res = updateUtil.updateYTDL(channel)
            when (res.status) {
                UpdateUtil.YTDLPUpdateStatus.DONE -> {
                    if (view != null && view.isAttachedToWindow) {
                        Snackbar.make(view, res.message, Snackbar.LENGTH_LONG).show()
                    }

                    setYTDLPVersion(context, host, ytdlpViewModel, preferences, ytdlVersionPreference)
                    val infoJsonPath = FileUtil.getInfoJsonPath(context)
                    File(infoJsonPath).deleteRecursively()
                }
                UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> {
                    if (view != null && view.isAttachedToWindow) {
                        Snackbar.make(view,
                            context.getString(R.string.you_are_in_latest_version),
                            Snackbar.LENGTH_LONG).show()
                    }
                }
                UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                    val msg = res.message
                    if (view != null && view.isAttachedToWindow) {
                        val snackBar = Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                        snackBar.setAction(R.string.copy_log){
                            UiUtil.copyToClipboard(msg, host.getHostContext())
                        }
                        val snackbarView: View = snackBar.view
                        val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                        snackTextView.maxLines = 9999999
                        snackBar.show()
                    }
                }
                else -> {

                }
            }
        }.onFailure {
            val msg = it.message ?: context.getString(R.string.errored)
            if (view != null && view.isAttachedToWindow) {
                val snackBar = Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                snackBar.setAction(R.string.copy_log){
                    UiUtil.copyToClipboard(msg, host.getHostContext())
                }
                val snackbarView: View = snackBar.view
                val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                snackTextView.maxLines = 9999999
                snackBar.show()
            }
        }
    }
}