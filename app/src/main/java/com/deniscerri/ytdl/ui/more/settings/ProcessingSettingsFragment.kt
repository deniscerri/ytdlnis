package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
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
            UiUtil.showSubtitleLanguagesDialog(requireActivity(), listOf(), prefs.getString("subs_lang", "en.*,.*-orig")!!){
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

        findPreference<Preference>("audio_bitrate")?.apply {
            var currentValue = prefs.getString("audio_bitrate", "")!!
            val entries = context.resources.getStringArray(R.array.audio_bitrate)
            val entryValues = context.resources.getStringArray(R.array.audio_bitrate_values)

            summary = if (currentValue.isNotBlank()) {
                entries[entryValues.indexOf(currentValue)]
            }else {
                getString(R.string.defaultValue)
            }

            setOnPreferenceClickListener {
                currentValue = prefs.getString("audio_bitrate", "")!!
                UiUtil.showAudioBitrateDialog(requireActivity(), currentValue) {
                    editor.putString("audio_bitrate", it).apply()
                    summary = if (it.isNotBlank()) {
                        entries[entryValues.indexOf(it)]
                    }else {
                        getString(R.string.defaultValue)
                    }
                }
                true
            }
        }

        val audioCodecPref = findPreference<ListPreference>("audio_codec")
        val videoCodecPref = findPreference<ListPreference>("video_codec")
        val videoContainerPref = findPreference<ListPreference>("video_format")

        val recodeVideoPreference = findPreference<SwitchPreferenceCompat>("recode_video")!!
        val compatibleVideoPreference = findPreference<SwitchPreferenceCompat>("compatible_video")!!

        audioCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoContainerPref?.isEnabled = !compatibleVideoPreference.isChecked

        recodeVideoPreference.setOnPreferenceClickListener {
            if (compatibleVideoPreference.isChecked && recodeVideoPreference.isChecked) {
                compatibleVideoPreference.performClick()
            }
            true
        }

        compatibleVideoPreference.setOnPreferenceClickListener {
            audioCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
            videoCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
            videoContainerPref?.isEnabled = !compatibleVideoPreference.isChecked

            if (compatibleVideoPreference.isChecked) {
                if (recodeVideoPreference.isChecked) {
                    recodeVideoPreference.performClick()
                }

                editor.putString("audio_codec_tmp", audioCodecPref?.value ?: "").apply()
                editor.putString("video_codec_tmp", videoCodecPref?.value ?: "").apply()
                editor.putString("video_format_tmp", videoContainerPref?.value ?: "").apply()

                val audioCodecs = requireContext().getStringArray(R.array.audio_codec)
                val audioCodecValues = requireContext().getStringArray(R.array.audio_codec_values)
                val videoCodecs = requireContext().getStringArray(R.array.video_codec)
                val videoCodecValues = requireContext().getStringArray(R.array.video_codec_values)

                val newAudioCodec = "M4A"
                val newVideoCodec = "AVC (H264)"

                editor.putString("audio_codec", audioCodecValues[audioCodecs.indexOf(newAudioCodec)]).apply()
                editor.putString("video_codec", videoCodecValues[videoCodecs.indexOf(newVideoCodec)]).apply()
                editor.putString("video_format", "").apply()
                requireActivity().recreate()
            } else {
                editor.putString("audio_codec", prefs.getString("audio_codec_tmp", "")).apply()
                editor.putString("video_codec", prefs.getString("video_codec_tmp", "")).apply()
                editor.putString("video_format", prefs.getString("video_format_tmp", "")).apply()
                requireActivity().recreate()
            }
            true
        }

        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, R.xml.processing_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!,true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }
}