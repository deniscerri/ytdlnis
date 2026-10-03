package com.deniscerri.ytdl.ui.more.settings.updating.ytdlp

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateChannelPreference
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateStatusCardPreference
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


object YtdlpUpdateSettingsModule : SettingModule {

    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val defaultChannelsTitles = context.getStringArray(R.array.ytdlp_source)
        val defaultChannelsValues = context.getStringArray(R.array.ytdlp_source_values)

        when(pref.key) {
            "ytdlp_status_card" -> {
                (pref as UpdateStatusCardPreference).apply {
                    status = restingStatus(preferences.getBoolean("auto_update_ytdlp", true))
                    onButtonClick = { checkForUpdates(context, host, preferences) }
                }
                loadVersion(context, host, preferences)
            }
            "ytdlp_channel" -> {
                (pref as UpdateChannelPreference).apply {
                    setChannels(defaultChannelsValues.zip(defaultChannelsTitles))
                    // a custom source leaves the group without a selection
                    selectedChannel = currentSource(preferences)
                    onChannelSelected = { value ->
                        selectSource(context, host, preferences, defaultChannelsTitles[defaultChannelsValues.indexOf(value)], value)
                    }
                }
            }
            "auto_update_ytdlp" -> {
                pref.setOnPreferenceChangeListener { _, newValue ->
                    (host.findPref("ytdlp_status_card") as? UpdateStatusCardPreference)?.apply {
                        if (status == UpdateStatusCardPreference.Status.UP_TO_DATE || status == UpdateStatusCardPreference.Status.UNKNOWN) {
                            status = restingStatus(newValue as Boolean)
                        }
                    }
                    true
                }
            }
            "ytdlp_source_label" -> {
                pref.apply {
                    summary = sourceSummary(context, preferences)
                    setOnPreferenceClickListener {
                        UiUtil.showYTDLCustomSourceBottomSheet(host.getHostContext(), preferences) { title, source ->
                            selectSource(context, host, preferences, title, source)
                        }
                        true
                    }
                }
            }
        }
    }

    private fun restingStatus(autoUpdate: Boolean) =
        if (autoUpdate) UpdateStatusCardPreference.Status.UP_TO_DATE else UpdateStatusCardPreference.Status.UNKNOWN

    private fun currentSource(preferences: SharedPreferences) = preferences.getString("ytdlp_source", "stable")!!

    private fun sourceSummary(context: Context, preferences: SharedPreferences) : String {
        val source = currentSource(preferences)
        val defaultChannelsValues = context.getStringArray(R.array.ytdlp_source_values)
        if (defaultChannelsValues.contains(source)) return context.getString(R.string.custom_channels_summary)
        val label = preferences.getString("ytdlp_source_label", "")!!
        return if (label.isBlank()) source else "$label\n$source"
    }

    private fun selectSource(context: Context, host: SettingHost, preferences: SharedPreferences, title: String, source: String) {
        preferences.edit {
            putString("ytdlp_source", source)
            putString("ytdlp_source_label", title)
        }

        (host.findPref("ytdlp_channel") as? UpdateChannelPreference)?.selectedChannel = source
        host.findPref("ytdlp_source_label")?.summary = sourceSummary(context, preferences)

        checkForUpdates(context, host, preferences)
    }

    private fun loadVersion(context: Context, host: SettingHost, preferences: SharedPreferences) {
        val card = host.findPref("ytdlp_status_card") as? UpdateStatusCardPreference ?: return
        val ytdlpViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[YTDLPViewModel::class.java]
        host.hostLifecycleOwner.lifecycleScope.launch {
            val version = withContext(Dispatchers.IO) {
                runCatching { ytdlpViewModel.getVersion(currentSource(preferences)) }.getOrDefault("")
            }
            preferences.edit(commit = true) {
                putString("ytdl-version", version)
            }
            card.version = version
        }
    }

    private fun checkForUpdates(context: Context, host: SettingHost, preferences: SharedPreferences) {
        val card = host.findPref("ytdlp_status_card") as? UpdateStatusCardPreference
        val updateUtil = UpdateUtil(context)

        host.hostLifecycleOwner.lifecycleScope.launch {
            val view = host.hostView
            card?.status = UpdateStatusCardPreference.Status.CHECKING

            runCatching {
                val res = updateUtil.updateYTDL()
                when (res.status) {
                    UpdateUtil.YTDLPUpdateStatus.DONE -> {
                        if (view != null && view.isAttachedToWindow) {
                            Snackbar.make(view, res.message, Snackbar.LENGTH_LONG).show()
                        }
                        card?.status = UpdateStatusCardPreference.Status.UP_TO_DATE
                        withContext(Dispatchers.IO) {
                            File(FileUtil.getInfoJsonPath(context)).deleteRecursively()
                        }
                    }
                    UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> {
                        card?.status = UpdateStatusCardPreference.Status.UP_TO_DATE
                    }
                    UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                        card?.status = UpdateStatusCardPreference.Status.ERROR
                        showErrorSnackbar(view, host, res.message)
                    }
                    UpdateUtil.YTDLPUpdateStatus.PROCESSING -> {
                        card?.status = restingStatus(preferences.getBoolean("auto_update_ytdlp", true))
                    }
                }
            }.onFailure {
                card?.status = UpdateStatusCardPreference.Status.ERROR
                showErrorSnackbar(view, host, it.message ?: context.getString(R.string.errored))
            }

            loadVersion(context, host, preferences)
        }
    }

    private fun showErrorSnackbar(view: View?, host: SettingHost, msg: String) {
        if (view == null || !view.isAttachedToWindow) return
        val snackBar = Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
        snackBar.setAction(R.string.copy_log){
            UiUtil.copyToClipboard(msg, host.getHostContext())
        }
        val snackTextView = snackBar.view.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
        snackTextView.maxLines = 9999999
        snackBar.show()
    }
}
