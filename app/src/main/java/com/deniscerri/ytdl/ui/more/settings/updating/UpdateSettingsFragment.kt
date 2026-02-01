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
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.runtimes.Aria2c
import com.deniscerri.ytdl.core.runtimes.BaseRuntime
import com.deniscerri.ytdl.core.runtimes.FFmpeg
import com.deniscerri.ytdl.core.runtimes.NodeJS
import com.deniscerri.ytdl.core.runtimes.Python
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class UpdateSettingsFragment : BaseSettingsFragment() {
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
        updateUtil = UpdateUtil(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updateYTDL = findPreference("update_ytdl")
        ytdlVersion = findPreference("ytdl-version")
        ytdlSource = findPreference("ytdlp_source_label")

        ytdlpViewModel = ViewModelProvider(this)[YTDLPViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        ytdlSource?.apply {
            summary = preferences.getString("ytdlp_source_label", "")!!.ifEmpty { getString(R.string.update_ytdl_stable) }
            setOnPreferenceClickListener {
                UiUtil.showYTDLSourceBottomSheet(requireActivity(), preferences) { t, r ->
                    summary = t
                    preferences.edit().putString("ytdlp_source", r).apply()
                    preferences.edit().putString("ytdlp_source_label", t).apply()
                    initYTDLUpdate(r)
                }
                true
            }
        }

        ytdlVersion?.apply {
            lifecycleScope.launch {
                summary = getString(R.string.loading)
                summary = withContext(Dispatchers.IO){
                    ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
                }
                if (summary?.isBlank() == true) {
                    setYTDLPVersion()
                }
                setOnPreferenceClickListener {
                    initYTDLUpdate()
                    true
                }
            }
        }
        
        updateYTDL!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                initYTDLUpdate()
                true
            }


        findPreference<Preference>("changelog")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.changeLogFragment)
            false
        }


        version = findPreference("version")
        val nativeLibraryDir = context?.applicationInfo?.nativeLibraryDir
        version!!.summary = "${BuildConfig.VERSION_NAME} (${nativeLibraryDir?.split("/lib/")?.get(1)})"
        version!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                lifecycleScope.launch{
                    val res = withContext(Dispatchers.IO){
                        updateUtil!!.tryGetNewVersion()
                    }
                    if (res.isFailure) {
                        Snackbar.make(requireView(), res.exceptionOrNull()?.message ?: getString(R.string.network_error), Snackbar.LENGTH_LONG).show()
                    }else{
                        if (preferences.getBoolean("automatic_backup", false)) {
                            withContext(Dispatchers.IO){
                                settingsViewModel.backup()
                            }
                        }
                        UiUtil.showNewAppUpdateDialog(res.getOrNull()!!, requireActivity(), preferences)
                    }
                }
                true
            }

        //packages
        findPreference<Preference>("package_python")?.apply {
            val instance = Python.getInstance()
            summary = instance.getVersion(requireContext())
        }
        findPreference<Preference>("package_ffmpeg")?.apply {
            val instance = FFmpeg.getInstance()
            summary = instance.getVersion(requireContext())
        }
        findPreference<Preference>("package_aria2c")?.apply {
            val instance = Aria2c.getInstance()
            summary = instance.getVersion(requireContext())
        }

        handlePackage(NodeJS.getInstance(), findPreference<Preference>("package_nodejs"))

        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(preferences.edit(), R.xml.updating_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!,true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }

    private fun setYTDLPVersion() {
        lifecycleScope.launch {
            ytdlVersion!!.summary = getString(R.string.loading)
            val version = withContext(Dispatchers.IO){
                ytdlpViewModel.getVersion(preferences.getString("ytdlp_source", "stable")!!)
            }
            preferences.edit().apply {
                putString("ytdl-version", version)
                apply()
            }
            ytdlVersion!!.summary = version
        }
    }

    private fun initYTDLUpdate(channel: String? = null) = lifecycleScope.launch {
        Snackbar.make(requireView(),
            requireContext().getString(R.string.ytdl_updating_started),
            Snackbar.LENGTH_LONG).show()
        runCatching {
            val res = updateUtil!!.updateYTDL(channel)
            when (res.status) {
                UpdateUtil.YTDLPUpdateStatus.DONE -> {
                    Snackbar.make(requireView(), res.message, Snackbar.LENGTH_LONG).show()
                    setYTDLPVersion()
                }
                UpdateUtil.YTDLPUpdateStatus.ALREADY_UP_TO_DATE -> Snackbar.make(requireView(),
                    requireContext().getString(R.string.you_are_in_latest_version),
                    Snackbar.LENGTH_LONG).show()
                UpdateUtil.YTDLPUpdateStatus.ERROR -> {
                    val msg = res.message
                    view?.apply {
                        val snackBar = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
                        snackBar.setAction(R.string.copy_log){
                            UiUtil.copyToClipboard(msg, requireActivity())
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
            val msg = it.message ?: requireContext().getString(R.string.errored)
            view?.apply {
                val snackBar = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
                snackBar.setAction(R.string.copy_log){
                    UiUtil.copyToClipboard(msg, requireActivity())
                }
                val snackbarView: View = snackBar.view
                val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                snackTextView.maxLines = 9999999
                snackBar.show()
            }

        }
    }


    private fun handlePackage(instance: BaseRuntime, preference: Preference?) {
        //TODO REWRITE THIS LOL
        preference?.apply {
            summary = instance.getVersion(requireContext())
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                summary = getString(R.string.loading)
                val response = instance.checkForUpdates(requireContext())
                if (response == null) {
                    Snackbar.make(requireView(), getString(R.string.failed_download), Snackbar.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val success = instance.downloadAndInstall(requireContext(), response.downloadUrl, response.version) { progress, total ->
                                val downloadedMB = progress / 1048576
                                val totalMB = total / 1048576

                                lifecycleScope.launch {
                                    withContext(Dispatchers.Main) {
                                        summary = "${getString(R.string.downloading)} $downloadedMB MB / $totalMB MB"
                                    }
                                }
                            }

                            if (success) {
                                RuntimeManager.reInit(requireContext())
                            }
                        }
                    }
                }
                summary = instance.getVersion(requireContext())
                true
            }
        }
    }

}