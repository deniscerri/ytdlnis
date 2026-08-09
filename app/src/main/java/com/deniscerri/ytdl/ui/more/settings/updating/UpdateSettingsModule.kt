package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.update.UpdateChecker
import com.deniscerri.ytdl.update.UpdatePrefs
import com.deniscerri.ytdl.update.UpdateRegistry
import com.deniscerri.ytdl.update.UpdateStore
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object UpdateSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference,host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val updateUtil = UpdateUtil(context)
        val ytdlpViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[YTDLPViewModel::class.java]

        val canUpdateApp = BuildConfig.FLAVOR == "github";

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
            "version" -> {
                pref.apply {
                    val nativeLibraryDir = context.applicationInfo?.nativeLibraryDir
                    summary = "${BuildConfig.VERSION_NAME} (${nativeLibraryDir?.split("/lib/")?.get(1)}) (${BuildConfig.FLAVOR})"
                }
            }
            "update_app" -> {
                pref.apply {
                    isVisible = canUpdateApp
                    // The switch is the only writer of UpdatePrefs, so the flow the dialog reads
                    // and the value stored here can never drift apart.
                    setOnPreferenceChangeListener { _, value ->
                        val auto = value as Boolean
                        UpdatePrefs.setAutoPrompt(context, auto)
                        host.findPref(KEY_UPDATE_CHECK)?.isEnabled = !auto
                        true
                    }
                }
            }
            KEY_UPDATE_CHECK -> {
                pref.apply {
                    isVisible = canUpdateApp
                    // With the alerts on, the app already checks on every launch — leaving nothing
                    // for this row to do that has not been done.
                    isEnabled = !UpdatePrefs.autoPrompt.value

                    // Scoped to this binding rather than the module, which outlives the screen.
                    var checking = false
                    setOnPreferenceClickListener {
                        // An update already in hand needs no lookup — including the APK downloaded
                        // last session and still waiting to install. Reopen the prompt instead.
                        if (UpdateRegistry.available.value != null) {
                            UpdateRegistry.requestPrompt()
                            return@setOnPreferenceClickListener true
                        }
                        if (checking) return@setOnPreferenceClickListener true

                        checking = true
                        isEnabled = false
                        summary = context.getString(R.string.update_checking)
                        host.hostLifecycleOwner.lifecycleScope.launch {
                            val found = UpdateChecker.check()
                            found?.let {
                                UpdateStore.save(context, it)
                                UpdateRegistry.setAvailable(it)
                                UpdateRegistry.requestPrompt()
                            }
                            checking = false
                            isEnabled = true
                            // A found update speaks for itself through the dialog; only its
                            // absence needs saying.
                            summary = if (found == null) context.getString(R.string.update_up_to_date) else null
                        }
                        true
                    }
                }
            }
        }
    }

    private const val KEY_UPDATE_CHECK = "update_check"

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
