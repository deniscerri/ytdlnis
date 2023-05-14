package com.deniscerri.ytdlnis.ui.more.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import com.deniscerri.ytdlnis.R


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
        findPreference<EditTextPreference>("file_name_template")?.title = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"
        findPreference<EditTextPreference>("file_name_template")?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"

        findPreference<EditTextPreference>("file_name_template_audio")?.title = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
        findPreference<EditTextPreference>("file_name_template_audio")?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
    }

}