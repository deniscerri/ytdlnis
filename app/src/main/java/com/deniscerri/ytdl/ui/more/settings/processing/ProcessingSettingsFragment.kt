package com.deniscerri.ytdl.ui.more.settings.processing

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.BaseSettingsFragment
import com.deniscerri.ytdl.ui.more.settings.SettingsRegistry
import com.deniscerri.ytdl.util.UiUtil

class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceXMLRes = R.xml.processing_preferences
        setPreferencesFromResource(preferenceXMLRes, rootKey)
        SettingsRegistry.bindFragment(this, preferenceXMLRes)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val editor = prefs.edit()


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
}