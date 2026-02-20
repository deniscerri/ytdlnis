package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.UiUtil

// Fragment for processing settings (format selection, audio bitrate, recoding, codecs, etc.)
class ProcessingSettingsFragment : SearchableSettingsFragment() {
    override val title: Int = R.string.processing

    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
        buildPreferenceList(preferenceScreen)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val editor = prefs.edit()

        // Preferred format IDs for video and audio – update titles to distinguish them.
        val preferredFormatID = findPreference<EditTextPreference>("format_id")
        val preferredFormatIDAudio = findPreference<EditTextPreference>("format_id_audio")
        val subtitleLanguages = findPreference<Preference>("subs_lang")

        preferredFormatID?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.video)}]"
        preferredFormatID?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"

        preferredFormatIDAudio?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.audio)}]"
        preferredFormatIDAudio?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"

        // Subtitle languages – show current value and open multi‑select dialog on click.
        subtitleLanguages?.summary = prefs.getString("subs_lang", "en.*,.*-orig")!!
        subtitleLanguages?.setOnPreferenceClickListener {
            UiUtil.showSubtitleLanguagesDialog(
                requireActivity(),
                listOf(),
                prefs.getString("subs_lang", "en.*,.*-orig")!!
            ) { newValue ->
                editor.putString("subs_lang", newValue).apply()
                subtitleLanguages?.summary = newValue
            }
            true
        }

        // Video format ID – show current value in summary and update on changes.
        findPreference<EditTextPreference>("format_id")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) s else "$s\n[$text]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "$s\n[$newValue]"
                true
            }
        }

        // Audio format ID – same as above.
        findPreference<EditTextPreference>("format_id_audio")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) s else "$s\n[$text]"
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) s else "$s\n[$newValue]"
                true
            }
        }

        // Audio bitrate – show selected entry in summary, open custom dialog on click.
        findPreference<Preference>("audio_bitrate")?.apply {
            var currentValue = prefs.getString("audio_bitrate", "")!!
            val entries = context.resources.getStringArray(R.array.audio_bitrate)
            val entryValues = context.resources.getStringArray(R.array.audio_bitrate_values)

            summary = if (currentValue.isNotBlank()) {
                entries[entryValues.indexOf(currentValue)]
            } else {
                getString(R.string.defaultValue)
            }

            setOnPreferenceClickListener {
                currentValue = prefs.getString("audio_bitrate", "")!!
                UiUtil.showAudioBitrateDialog(requireActivity(), currentValue) { newValue ->
                    editor.putString("audio_bitrate", newValue).apply()
                    summary = if (newValue.isNotBlank()) {
                        entries[entryValues.indexOf(newValue)]
                    } else {
                        getString(R.string.defaultValue)
                    }
                }
                true
            }
        }

        // Codec and container preferences (newly added)
        val audioCodecPref = findPreference<ListPreference>("audio_codec")
        val videoCodecPref = findPreference<ListPreference>("video_codec")
        val videoContainerPref = findPreference<ListPreference>("video_format")

        // Recode video and compatible video switches – they are mutually exclusive,
        // and compatible video toggles codec/container availability.
        val recodeVideoPreference = findPreference<SwitchPreferenceCompat>("recode_video")!!
        val compatibleVideoPreference = findPreference<SwitchPreferenceCompat>("compatible_video")!!

        // Initially set enabled state of codec preferences based on compatible video.
        audioCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoContainerPref?.isEnabled = !compatibleVideoPreference.isChecked

        recodeVideoPreference.setOnPreferenceClickListener {
            // If both are checked, turn off the other one.
            if (compatibleVideoPreference.isChecked && recodeVideoPreference.isChecked) {
                compatibleVideoPreference.performClick()
            }
            true
        }

        compatibleVideoPreference.setOnPreferenceClickListener {
            // Update enabled state of codec/container preferences.
            audioCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
            videoCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
            videoContainerPref?.isEnabled = !compatibleVideoPreference.isChecked

            if (compatibleVideoPreference.isChecked) {
                // When enabling compatible video, if recode is on, turn it off.
                if (recodeVideoPreference.isChecked) {
                    recodeVideoPreference.performClick()
                }

                // Save current codec values to temporary keys before overriding.
                editor.putString("audio_codec_tmp", audioCodecPref?.value ?: "").apply()
                editor.putString("video_codec_tmp", videoCodecPref?.value ?: "").apply()
                editor.putString("video_format_tmp", videoContainerPref?.value ?: "").apply()

                // Force specific codecs for compatibility.
                val audioCodecs = resources.getStringArray(R.array.audio_codec)
                val audioCodecValues = resources.getStringArray(R.array.audio_codec_values)
                val videoCodecs = resources.getStringArray(R.array.video_codec)
                val videoCodecValues = resources.getStringArray(R.array.video_codec_values)

                val newAudioCodec = "M4A"
                val newVideoCodec = "AVC (H264)"
                val newAudioValue = audioCodecValues[audioCodecs.indexOf(newAudioCodec)]
                val newVideoValue = videoCodecValues[videoCodecs.indexOf(newVideoCodec)]

                editor.putString("audio_codec", newAudioValue).apply()
                editor.putString("video_codec", newVideoValue).apply()
                editor.putString("video_format", "").apply() // Clear container preference.
                
                // Update UI — just set the value; ListPreference's built-in
                // SummaryProvider updates the summary automatically.
                audioCodecPref?.value = newAudioValue
                videoCodecPref?.value = newVideoValue
                videoContainerPref?.value = ""
            } else {
                // Restore previously saved codec values.
                val savedAudio = prefs.getString("audio_codec_tmp", "")
                val savedVideo = prefs.getString("video_codec_tmp", "")
                val savedFormat = prefs.getString("video_format_tmp", "")
                
                editor.putString("audio_codec", savedAudio).apply()
                editor.putString("video_codec", savedVideo).apply()
                editor.putString("video_format", savedFormat).apply()
                
                // Update UI — just set the value; ListPreference's built-in
                // SummaryProvider updates the summary automatically.
                if (savedAudio?.isNotBlank() == true) {
                    audioCodecPref?.value = savedAudio
                }
                if (savedVideo?.isNotBlank() == true) {
                    videoCodecPref?.value = savedVideo
                }
                // Always update format, even if empty
                videoContainerPref?.value = savedFormat ?: ""
            }
            true
        }

        // Reset all preferences in this screen.
        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(
                requireContext(),
                getString(R.string.reset),
                getString(R.string.reset_preferences_in_screen)
            ) {
                resetPreferences(editor, R.xml.processing_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!, true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }
}
