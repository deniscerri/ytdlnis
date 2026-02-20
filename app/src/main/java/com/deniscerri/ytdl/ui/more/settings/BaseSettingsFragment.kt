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

// Base class for all settings fragments, providing common functionality:
// - Building a flat list of all preferences for searching/filtering
// - Showing/hiding preferences based on a search query
// - Custom dialogs for ListPreference, MultiSelectListPreference, EditTextPreference
// - Reset preferences for the current screen
abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
    // Title to show in the toolbar when this fragment is visible
    abstract val title: Int
    
    // Holds a flat representation of every preference in this screen,
    // including its parent group, title, summary, and key.
    data class PreferenceData(
        val preference: Preference,
        val parent: PreferenceGroup?,
        val title: String,
        val summary: String,
        val key: String
    )
    
    protected val allPreferences = mutableListOf<PreferenceData>()
    
    // Recursively walk the preference hierarchy and collect all preferences into allPreferences.
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
    
    // Check if any preference matches the given query (used to decide whether to show this fragment in search results)
    fun hasMatchingPreferences(query: String): Boolean {
        if (query.isEmpty()) return true
        
        val lowerQuery = query.lowercase()
        return allPreferences.any { data ->
            data.title.lowercase().contains(lowerQuery) ||
            data.summary.lowercase().contains(lowerQuery) ||
            data.key.lowercase().contains(lowerQuery)
        }
    }
    
    // Filter visible preferences to only those matching the query.
    // Overridden in MainSettingsFragment to show search results across categories.
    open fun filterPreferences(query: String) {
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
    
    protected fun restoreAllPreferences() {
        allPreferences.forEach { it.preference.isVisible = true }
    }
    
    // Hide any PreferenceGroup that has no visible children after filtering.
    protected fun hideEmptyCategories() {
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
        // Update the toolbar title to match the current fragment
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    // Recursively collect all leaf preferences from a given Preference (could be a group or a single preference).
    // Used by resetPreferences to know which keys to clear.
    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            for (i in 0 until pGroup.preferenceCount) {
                getPreferences(pGroup.getPreference(i), list)
            }
        } else {
            list.add(p)
        }
        return list
    }

    // Reset all preferences in the current screen to their default values.
    // The key parameter is the XML resource ID for the screen's preferences.
    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    // Custom dialog handling for ListPreference, MultiSelectListPreference, and EditTextPreference.
    // This gives a more consistent Material look and better UX (e.g., auto‑focus for EditText).
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