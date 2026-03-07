package com.deniscerri.ytdl.ui.more.settings.folder

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsRegistry
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FolderSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.directories
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceXMLRes = R.xml.folders_preference
        setPreferencesFromResource(preferenceXMLRes, rootKey)
        SettingsRegistry.bindFragment(this, preferenceXMLRes)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()

        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, preferenceXMLRes)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!,true)
                findNavController().navigate(fragmentId)
            }
            true
        }

    }

    override fun onResume() {
        if((Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
            Build.VERSION.SDK_INT < 30) {
            findPreference<Preference>("access_all_files")!!.isVisible = false
            findPreference<Preference>("cache_downloads")!!.isEnabled = true
        }else{
            editor.putBoolean("cache_downloads", true).apply()
            findPreference<Preference>("cache_downloads")!!.isEnabled = false
        }
        super.onResume()
    }
}