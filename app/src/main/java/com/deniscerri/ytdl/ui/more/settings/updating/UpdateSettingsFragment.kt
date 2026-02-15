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
        buildPreferenceList(preferenceScreen)
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
            val res = updateUtil!!.updateYoutubeDL(channel)
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


}
