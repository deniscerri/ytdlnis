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
            is MultiSelectListPreference -> {
                val values = preference.entryValues
                val entries = preference.entries
                val checkedItems : ArrayList<Boolean> = arrayListOf()
                values.forEach {
                    if (preference.values.contains(it)) {
                        checkedItems.add(true)
                    }else{
                        checkedItems.add(false)
                    }
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setMultiChoiceItems(entries, checkedItems.toBooleanArray()) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                        preference.values.clear()
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                preference.values.add(values[i].toString())
                            }
                        }
                    }
                    .show()
            }
            /**
             * Otherwise show the normal dialog, dialogs for other preference types are not supported yet
             */
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}