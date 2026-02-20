package com.deniscerri.ytdl.ui.more.settings.updating

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.more.settings.SearchableSettingsFragment
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Fragment for update‑related settings (yt‑dlp version, update channel, app updates)
class UpdateSettingsFragment : SearchableSettingsFragment() {
    override val title: Int = R.string.updating

    private var updateYTDL: Preference? = null
    private var ytdlVersion: Preference? = null
    private var ytdlSource: Preference? = null
    private var updateUtil: UpdateUtil? = null
    private var version: Preference? = null
    private lateinit var preferences: SharedPreferences

    private lateinit var ytdlpViewModel: YTDLPViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.updating_preferences, rootKey)
        buildPreferenceList(preferenceScreen)

        updateUtil = UpdateUtil(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        updateYTDL = findPreference("update_ytdl")
        ytdlVersion = findPreference("ytdl-version")
        ytdlSource = findPreference("ytdlp_source_label")

        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        // yt‑dlp source selection – opens a bottom sheet to choose stable/nightly/master.
        ytdlSource?.apply {
            summary = preferences.getString("ytdlp_source_label", "")!!
                .ifEmpty { getString(R.string.update_ytdl_stable) }
            setOnPreferenceClickListener {
                UiUtil.showYTDLSourceBottomSheet(requireActivity(), preferences) { label, source ->
                    summary = label
                    preferences.edit()
                        .putString("ytdlp_source", source)
                        .putString("ytdlp_source_label", label)
                        .apply()
                    // After changing source, immediately update to that version.
                    initYTDLUpdate(source)
                }
                true
            }
        }

        // yt‑dlp version display – fetch on creation and allow manual update on click.
        ytdlVersion?.apply {
            lifecycleScope.launch {
                summary = getString(R.string.loading)
                val version = withContext(Dispatchers.IO) {
                    ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
                }
                summary = version
                if (summary?.isBlank() == true) {
                    setYTDLPVersion()
                }
                setOnPreferenceClickListener {
                    initYTDLUpdate()
                    true
                }
            }
        }

        // Manual update button – triggers yt‑dlp update using current source.
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                initYTDLUpdate()
                true
            }

        // Changelog – opens a separate fragment.
        findPreference<Preference>("changelog")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.changeLogFragment)
            false
        }

        // App version – shows current version and ABI; checks for updates on click.
        version = findPreference("version")
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir
        version!!.summary = "${BuildConfig.VERSION_NAME} (${nativeLibraryDir?.split("/lib/")?.get(1)})"
        version!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        updateUtil!!.tryGetNewVersion()
                    }
                    if (result.isFailure) {
                        Snackbar.make(
                            requireView(),
                            result.exceptionOrNull()?.message ?: getString(R.string.network_error),
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        // If automatic backup is enabled, do it before updating.
                        if (preferences.getBoolean("automatic_backup", false)) {
                            withContext(Dispatchers.IO) {
                                settingsViewModel.backup()
                            }
                        }
                        UiUtil.showNewAppUpdateDialog(
                            result.getOrNull()!!,
                            requireActivity(),
                            preferences
                        )
                    }
                }
                true
            }

        // Reset all preferences in this screen.
        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(
                requireContext(),
                getString(R.string.reset),
                getString(R.string.reset_preferences_in_screen)
            ) {
                resetPreferences(preferences.edit(), R.xml.updating_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!, true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }

    // Helper to refresh the displayed yt‑dlp version.
    private fun setYTDLPVersion() {
        lifecycleScope.launch {
            ytdlVersion!!.summary = getString(R.string.loading)
            val version = withContext(Dispatchers.IO) {
                ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
            }
            preferences.edit().putString("ytdl-version", version).apply()
            ytdlVersion!!.summary = version
        }
    }

    // Perform the actual yt‑dlp update. Can specify a channel (source) override.
    private fun initYTDLUpdate(channel: String? = null) = lifecycleScope.launch {
        Snackbar.make(
            requireView(),
            requireContext().getString(R.string.ytdl_updating_started),
            Snackbar.LENGTH_LONG
        ).show()

        runCatching {
            val result = updateUtil!!.updateYTDL(channel)
            when (result.status) {
                UpdateUtil.YTDLPUpdateStatus.DONE -> {
                    Snackbar.make(requireView(), result.message, Snackbar.LENGTH_LONG).show()
                    setYTDLPVersion()
                }
                UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> {
                    Snackbar.make(
                        requireView(),
                        requireContext().getString(R.string.you_are_in_latest_version),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                    showErrorSnackbar(result.message)
                }
                else -> { /* no-op */ }
            }
        }.onFailure { error ->
            showErrorSnackbar(error.message ?: requireContext().getString(R.string.errored))
        }
    }

    // Show a long snackbar with an error message and a copy button.
    private fun showErrorSnackbar(message: String) {
        view?.apply {
            val snackBar = Snackbar.make(this, message, Snackbar.LENGTH_LONG)
            snackBar.setAction(R.string.copy_log) {
                UiUtil.copyToClipboard(message, requireActivity())
            }
            val snackbarView = snackBar.view
            val snackTextView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            snackTextView.maxLines = Int.MAX_VALUE
            snackBar.show()
        }
    }
}
