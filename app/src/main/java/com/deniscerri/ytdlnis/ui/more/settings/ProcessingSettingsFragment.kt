package com.deniscerri.ytdlnis.ui.more.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.deniscerri.ytdlnis.R


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)

        val preferredFormatID : EditTextPreference? = findPreference("format_id")
        val preferredFormatIDAudio : EditTextPreference? = findPreference("format_id_audio")

        preferredFormatID?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.video)}]"
        preferredFormatID?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"

        preferredFormatIDAudio?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.audio)}]"
        preferredFormatIDAudio?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"
    }

}