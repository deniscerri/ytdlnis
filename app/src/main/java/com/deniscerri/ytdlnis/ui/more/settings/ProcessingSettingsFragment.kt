package com.deniscerri.ytdlnis.ui.more.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.UiUtil


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()

        val preferredFormatID : EditTextPreference? = findPreference("format_id")
        val preferredFormatIDAudio : EditTextPreference? = findPreference("format_id_audio")
        val subtitleLanguages : Preference? = findPreference("subs_lang")

        preferredFormatID?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.video)}]"
        preferredFormatID?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"

        preferredFormatIDAudio?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.audio)}]"
        preferredFormatIDAudio?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"

        subtitleLanguages?.summary = prefs.getString("subs_lang", "en.*,.*-orig")!!
        subtitleLanguages?.setOnPreferenceClickListener {
            UiUtil.showSubtitleLanguagesDialog(requireActivity(), prefs.getString("subs_lang", "en.*,.*-orig")!!){
                editor.putString("subs_lang", it)
                editor.apply()
                subtitleLanguages.summary = it
            }
            true
        }
    }

}