package com.deniscerri.ytdlnis.ui.more.settings

import androidx.preference.PreferenceFragmentCompat


abstract class BaseSettingsFragment : PreferenceFragmentCompat() {
    abstract val title: Int

    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }
}