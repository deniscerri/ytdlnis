package com.deniscerri.ytdlnis.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.deniscerri.ytdlnis.R

class MoreFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey)
    }

    companion object {
        const val TAG = "MoreFragment"
    }
}