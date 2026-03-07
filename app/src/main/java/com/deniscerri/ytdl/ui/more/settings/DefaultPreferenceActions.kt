package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.TextinputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

object DefaultPreferenceActions {
    fun onPreferenceDisplayDialog(context: Context, preference: Preference, callback: () -> Unit) : Boolean {
        val layoutInflater = LayoutInflater.from(context)

        return when (preference) {
            /**
             * Show a [MaterialAlertDialogBuilder] when the preference is a [ListPreference]
             */
            is ListPreference -> {
                // get the index of the previous selected item
                val prefIndex = preference.entryValues.indexOf(preference.value)
                MaterialAlertDialogBuilder(context)
                    .setTitle(preference.title)
                    .setSingleChoiceItems(preference.entries, prefIndex) { dialog, index ->
                        // get the new ListPreference value
                        val newValue = preference.entryValues[index].toString()
                        // invoke the on change listeners
                        if (preference.callChangeListener(newValue)) {
                            preference.value = newValue
                        }
                        callback()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()

                true
            }
            is MultiSelectListPreference -> {
                val selectedItems = preference.entryValues.map {
                    preference.values.contains(it)
                }.toBooleanArray()
                MaterialAlertDialogBuilder(context)
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
                        callback()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()

                true
            }
            is EditTextPreference -> {
                val binding = TextinputBinding.inflate(layoutInflater)
                binding.urlEdittext.setText(preference.text)
                binding.urlTextinput.findViewById<TextInputLayout>(R.id.url_textinput).hint = preference.title
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(preference.title)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val newValue = binding.urlEdittext.text.toString()
                        if (preference.callChangeListener(newValue)) {
                            preference.text = newValue
                        }
                        callback()
                    }
                    .setNegativeButton(R.string.cancel, null)
                dialog.show()
                val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                binding.urlEdittext.setSelection(binding.urlEdittext.text!!.length)
                binding.urlEdittext.postDelayed({
                    binding.urlEdittext.requestFocus()
                    imm.showSoftInput(binding.urlEdittext, 0)
                }, 300)

                true
            }
            else -> false
        }
    }
}