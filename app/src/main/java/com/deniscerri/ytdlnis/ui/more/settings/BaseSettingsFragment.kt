package com.deniscerri.ytdlnis.ui.more.settings

import android.content.DialogInterface
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.databinding.TextinputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout


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
            is MultiSelectListPreference -> {
                val selectedItems = preference.entryValues.map {
                    preference.values.contains(it)
                }.toBooleanArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setMultiChoiceItems(preference.entries, selectedItems) { _, which, isChecked ->
                        selectedItems[which] = isChecked
                    }
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val newValues = preference.entryValues
                            .filterIndexed { index, _ -> selectedItems[index] }
                            .map { it.toString() }
                            .toMutableSet()
                        if (preference.callChangeListener(newValues)) {
                            preference.values = newValues
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            is EditTextPreference -> {
                val binding = TextinputBinding.inflate(layoutInflater)
                binding.urlEdittext.setText(preference.text)
                binding.urlTextinput.findViewById<TextInputLayout>(R.id.url_textinput).hint = preference.title
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val newValue = binding.urlEdittext.text.toString()
                        if (preference.callChangeListener(newValue)) {
                            preference.text = newValue
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                dialog.show()
                val imm = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                binding.urlEdittext.setSelection(binding.urlEdittext.text!!.length)
                binding.urlEdittext.postDelayed({
                    binding.urlEdittext.requestFocus()
                    imm.showSoftInput(binding.urlEdittext, 0)
                }, 300)
            }
            /**
             * Otherwise show the normal dialog, dialogs for other preference types are not supported yet
             */
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}