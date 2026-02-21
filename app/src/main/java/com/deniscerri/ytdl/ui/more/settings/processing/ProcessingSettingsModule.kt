package com.deniscerri.ytdl.ui.more.settings.processing

import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.util.UiUtil
import kotlin.collections.indexOf

object ProcessingSettingsModule : SettingModule {
    override fun bindLogic(
        pref: Preference,
        host: SettingHost
    ) {
        val context = pref.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        when(pref.key) {
            "format_id" -> {
                (pref as EditTextPreference).apply {
                    title = "${context.getString(R.string.preferred_format_id)} [${context.getString(R.string.video)}]"
                    dialogTitle = "${context.getString(R.string.preferred_format_id)} [${context.getString(R.string.video)}]"

                    val s = context.getString(R.string.preferred_format_id_summary)
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
                        host.refreshUI()
                        true
                    }
                }
            }
            "format_id_audio" -> {
                (pref as EditTextPreference).apply {
                    title = "${context.getString(R.string.preferred_format_id)} [${context.getString(R.string.audio)}]"
                    dialogTitle = "${context.getString(R.string.preferred_format_id)} [${context.getString(R.string.audio)}]"

                    val s = context.getString(R.string.preferred_format_id_summary)
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
                        host.refreshUI()
                        true
                    }
                }
            }
            "subs_lang" -> {
                pref.apply {
                    summary = prefs.getString("subs_lang", "en.*,.*-orig")!!
                    setOnPreferenceClickListener {
                        UiUtil.showSubtitleLanguagesDialog(host.getHostContext(), listOf(), prefs.getString("subs_lang", "en.*,.*-orig")!!){
                            prefs.edit(commit = true) {
                                putString(pref.key, it)
                            }
                            summary = it
                            host.refreshUI()
                        }
                        true
                    }
                }
            }
            "audio_bitrate" -> {
                pref.apply {
                    var currentValue = prefs.getString("audio_bitrate", "")!!
                    val entries = context.resources.getStringArray(R.array.audio_bitrate)
                    val entryValues = context.resources.getStringArray(R.array.audio_bitrate_values)

                    summary = if (currentValue.isNotBlank()) {
                        entries[entryValues.indexOf(currentValue)]
                    }else {
                        context.getString(R.string.defaultValue)
                    }

                    setOnPreferenceClickListener {
                        currentValue = prefs.getString("audio_bitrate", "")!!
                        UiUtil.showAudioBitrateDialog(host.getHostContext(), currentValue) {
                            prefs.edit(commit = true) {
                                putString("audio_bitrate", it)
                            }

                            summary = if (it.isNotBlank()) {
                                entries[entryValues.indexOf(it)]
                            }else {
                                context.getString(R.string.defaultValue)
                            }
                            host.refreshUI()
                        }
                        true
                    }
                }
            }
            "audio_codec" -> {
                updateCompatibleVideoConfig(host)
            }
            "video_codec" -> {
                updateCompatibleVideoConfig(host)
            }
            "video_format" -> {
                updateCompatibleVideoConfig(host)
            }
            "recode_video" -> {
                val compatibleVideoPreference = host.findPref("compatible_video") as SwitchPreferenceCompat

                pref.setOnPreferenceClickListener {
                    if (compatibleVideoPreference.isChecked && (pref as SwitchPreferenceCompat).isChecked) {
                        compatibleVideoPreference.performClick()
                    }
                    true
                }
            }
            "compatible_video" -> {
                updateCompatibleVideoConfig(host)
                val prefSwitch = pref as SwitchPreferenceCompat
                pref.setOnPreferenceClickListener {
                    if (prefSwitch.isChecked) {
                        val recodeVideoPreference = host.findPref("recode_video") as SwitchPreferenceCompat

                        if (recodeVideoPreference.isChecked) {
                            recodeVideoPreference.performClick()
                        }

                        val audioCodecPref = host.findPref("audio_codec") as? ListPreference
                        val videoCodecPref = host.findPref("video_codec") as? ListPreference
                        val videoContainerPref = host.findPref("video_format") as? ListPreference

                        prefs.edit(commit = true) {
                            putString("audio_codec_tmp", audioCodecPref?.value ?: "")
                            putString("video_codec_tmp", videoCodecPref?.value ?: "")
                            putString("video_format_tmp", videoContainerPref?.value ?: "")
                        }

                        val audioCodecs = context.getStringArray(R.array.audio_codec)
                        val audioCodecValues = context.getStringArray(R.array.audio_codec_values)
                        val videoCodecs = context.getStringArray(R.array.video_codec)
                        val videoCodecValues = context.getStringArray(R.array.video_codec_values)

                        val newAudioCodec = "M4A"
                        val newVideoCodec = "AVC (H264)"

                        prefs.edit(commit = true) {
                            putString("audio_codec", audioCodecValues[audioCodecs.indexOf(newAudioCodec)])
                            putString("video_codec", videoCodecValues[videoCodecs.indexOf(newVideoCodec)])
                            putString("video_format", "")
                        }
                        host.refreshUI()
                        host.requestRecreateActivity()
                    } else {
                        prefs.edit(commit = true) {
                            putString("audio_codec", prefs.getString("audio_codec_tmp", ""))
                            putString("video_codec", prefs.getString("video_codec_tmp", ""))
                            putString("video_format", prefs.getString("video_format_tmp", ""))
                        }
                        host.refreshUI()
                        host.requestRecreateActivity()
                    }
                    true
                }
            }
        }
    }

    fun updateCompatibleVideoConfig(host: SettingHost) {
        val audioCodecPref = host.findPref("audio_codec")
        val videoCodecPref = host.findPref("video_codec")
        val videoContainerPref = host.findPref("video_format")
        val compatibleVideoPreference = host.findPref("compatible_video") as SwitchPreferenceCompat

        audioCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoCodecPref?.isEnabled = !compatibleVideoPreference.isChecked
        videoContainerPref?.isEnabled = !compatibleVideoPreference.isChecked
    }
}