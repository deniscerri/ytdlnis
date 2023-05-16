package com.deniscerri.ytdlnis.ui.more.settings

import android.content.DialogInterface
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.databinding.DialogTextPreferenceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder


abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
    abstract val title: Int

    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    //Thanks libretube
    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            /**
             * Show a [MaterialAlertDialogBuilder] when the preference is a [ListPreference]
             */
            is ListPreference -> {
                // get the index of the previous selected item
                val prefIndex = preference.entryValues.indexOf(preference.value)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setSingleChoiceItems(preference.entries, prefIndex) { dialog, index ->
                        // get the new ListPreference value
                        val newValue = preference.entryValues[index].toString()
                        // invoke the on change listeners
                        if (preference.callChangeListener(newValue)) {
                            preference.value = newValue
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            is EditTextPreference -> {
                val binding = DialogTextPreferenceBinding.inflate(layoutInflater)
                binding.input.setText(preference.text)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val newValue = binding.input.text.toString()
                        if (preference.callChangeListener(newValue)) {
                            preference.text = newValue
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            /**
             * Otherwise show the normal dialog, dialogs for other preference types are not supported yet
             */
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}