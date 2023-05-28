package com.deniscerri.ytdlnis.ui.more.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import com.deniscerri.ytdlnis.R


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
    }

}