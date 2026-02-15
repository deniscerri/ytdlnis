package com.deniscerri.ytdl.ui.more.settings

import android.content.SharedPreferences
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.TextinputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout


abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
    abstract val title: Int
    
    data class PreferenceData(
        val preference: Preference,
        val parent: PreferenceGroup?,
        val title: String,
        val summary: String,
        val key: String
    )
    
    protected val allPreferences = mutableListOf<PreferenceData>()
    
    protected fun buildPreferenceList(group: PreferenceGroup, parent: PreferenceGroup? = null) {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            
            when (preference) {
                is PreferenceGroup -> {
                    allPreferences.add(
                        PreferenceData(
                            preference = preference,
                            parent = parent,
                            title = preference.title?.toString() ?: "",
                            summary = preference.summary?.toString() ?: "",
                            key = preference.key ?: ""
                        )
                    )
                    buildPreferenceList(preference, group)
                }
                else -> {
                    allPreferences.add(
                        PreferenceData(
                            preference = preference,
                            parent = group,
                            title = preference.title?.toString() ?: "",
                            summary = preference.summary?.toString() ?: "",
                            key = preference.key ?: ""
                        )
                    )
                }
            }
        }
    }
    
    fun filterPreferences(query: String) {
        if (query.isEmpty()) {
            restoreAllPreferences()
            return
        }

        val lowerCaseQuery = query.lowercase()
        
        hideAllPreferences()
        
        val matchingPreferences = allPreferences.filter { data ->
            data.title.lowercase().contains(lowerCaseQuery) ||
            data.summary.lowercase().contains(lowerCaseQuery) ||
            data.key.lowercase().contains(lowerCaseQuery)
        }

        matchingPreferences.forEach { data ->
            data.preference.isVisible = true
            data.parent?.isVisible = true
        }
        
        hideEmptyCategories()
    }
    
    private fun hideAllPreferences() {
        allPreferences.forEach { it.preference.isVisible = false }
    }
    
    private fun restoreAllPreferences() {
        allPreferences.forEach { it.preference.isVisible = true }
    }
    
    private fun hideEmptyCategories() {
        allPreferences.forEach { data ->
            if (data.preference is PreferenceGroup) {
                val hasVisibleChildren = (0 until data.preference.preferenceCount).any {
                    data.preference.getPreference(it).isVisible
                }
                data.preference.isVisible = hasVisibleChildren
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferences(pGroup.getPreference(i), list)
            }
        } else {
            list.add(p)
        }
        return list
    }

    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference -> {
                val prefIndex = preference.entryValues.indexOf(preference.value)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setSingleChoiceItems(preference.entries, prefIndex) { dialog, index ->
                        val newValue = preference.entryValues[index].toString()
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
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}
