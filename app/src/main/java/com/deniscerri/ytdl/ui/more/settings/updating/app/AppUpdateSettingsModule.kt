package com.deniscerri.ytdl.ui.more.settings.updating.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateChannelPreference
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateStatusCardPreference
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object AppUpdateSettingsModule : SettingModule {
    private val canUpdateApp = BuildConfig.FLAVOR == "github"

    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        when(pref.key) {
            "app_status_card" -> {
                (pref as UpdateStatusCardPreference).apply {
                    version = BuildConfig.VERSION_NAME
                    showButton = canUpdateApp
                    buttonText = null
                    status = restingStatus(preferences.getBoolean("update_app", true))
                    onButtonClick = { checkForUpdates(context, host, preferences) }
                }
            }
            "app_channel" -> {
                host.findPref("app_update_channel_category")?.isVisible = canUpdateApp
                (pref as UpdateChannelPreference).apply {
                    isVisible = canUpdateApp
                    setChannels(listOf(
                        "stable" to context.getString(R.string.channel_stable),
                        "beta" to context.getString(R.string.channel_beta)
                    ))
                    selectedChannel = if (preferences.getBoolean("update_beta", false)) "beta" else "stable"
                    onChannelSelected = { value ->
                        preferences.edit { putBoolean("update_beta", value == "beta") }
                        checkForUpdates(context, host, preferences)
                    }
                }
            }
            "update_app" -> {
                pref.isVisible = canUpdateApp
                pref.setOnPreferenceChangeListener { _, newValue ->
                    (host.findPref("app_status_card") as? UpdateStatusCardPreference)?.apply {
                        if (status == UpdateStatusCardPreference.Status.UP_TO_DATE || status == UpdateStatusCardPreference.Status.UNKNOWN) {
                            status = restingStatus(newValue as Boolean)
                        }
                    }
                    true
                }
            }
            "changelog" -> {
                pref.setOnPreferenceClickListener {
                    host.requestNavigate(R.id.changeLogFragment)
                    false
                }
            }
        }
    }

    private fun restingStatus(autoCheck: Boolean) =
        if (canUpdateApp && autoCheck) UpdateStatusCardPreference.Status.UP_TO_DATE else UpdateStatusCardPreference.Status.UNKNOWN

    private fun checkForUpdates(context: Context, host: SettingHost, preferences: SharedPreferences) {
        val card = host.findPref("app_status_card") as? UpdateStatusCardPreference
        val updateUtil = UpdateUtil(context)

        host.hostLifecycleOwner.lifecycleScope.launch {
            card?.buttonText = null
            card?.status = UpdateStatusCardPreference.Status.CHECKING

            val res = withContext(Dispatchers.IO){
                updateUtil.tryGetNewVersion()
            }

            if (res.isSuccess) {
                val release = res.getOrNull()!!
                card?.apply {
                    status = UpdateStatusCardPreference.Status.UPDATE_AVAILABLE
                    buttonText = "${context.getString(R.string.update)} (${release.tag_name})"
                    onButtonClick = { showUpdateDialog(release, updateUtil, host, preferences) }
                }
                showUpdateDialog(release, updateUtil, host, preferences)
            } else if (res.exceptionOrNull()?.message == context.getString(R.string.you_are_in_latest_version)) {
                card?.status = UpdateStatusCardPreference.Status.UP_TO_DATE
            } else {
                card?.status = UpdateStatusCardPreference.Status.ERROR
                val view = host.hostView
                if (view != null && view.isAttachedToWindow) {
                    Snackbar.make(view, res.exceptionOrNull()?.message ?: context.getString(R.string.network_error), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUpdateDialog(release: GithubRelease, updateUtil: UpdateUtil, host: SettingHost, preferences: SharedPreferences) {
        val settingsViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[SettingsViewModel::class.java]
        host.hostLifecycleOwner.lifecycleScope.launch {
            if (preferences.getBoolean("automatic_backup", false)) {
                withContext(Dispatchers.IO){
                    settingsViewModel.backup()
                }
            }
            UiUtil.showNewAppUpdateDialog(release, host.getHostContext(), updateUtil, host.hostLifecycleOwner, preferences, host.getAppInstallLauncher())
        }
    }
}
