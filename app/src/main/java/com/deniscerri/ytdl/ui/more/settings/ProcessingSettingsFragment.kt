package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.UiUtil


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
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


        findPreference<EditTextPreference>("format_id")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) {
                s
            }else {
                "${s}\n[${text}]"
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) {
                    s
                }else {
                    "${s}\n[${newValue}]"
                }
                true
            }
        }

        findPreference<EditTextPreference>("format_id_audio")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) {
                s
            }else {
                "${s}\n[${text}]"
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) {
                    s
                }else {
                    "${s}\n[${newValue}]"
                }
                true
            }
        }

        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, R.xml.processing_preferences)
                requireActivity().recreate()
            }
            true
        }
    }
}