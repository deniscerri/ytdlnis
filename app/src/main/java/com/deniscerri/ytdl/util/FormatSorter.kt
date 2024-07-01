package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.json.JSONObject
import java.text.Normalizer.Form
import java.util.regex.Pattern

class FormatSorter(private var context: Context) {
    private val sharedPreferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val videoFormatIDPreference : List<String> = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
    private val audioFormatIDPreference : List<String> = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }
    private val videoQualityPreference : String = sharedPreferences.getString("video_quality", "best").toString()
    private val audioLanguagePreference : String = sharedPreferences.getString("audio_language", "").toString()
    private val audioCodecPreference : String =  sharedPreferences.getString("audio_codec", "").toString()
    private val videoCodecPreference : String =  sharedPreferences.getString("video_codec", "").toString()
    private val audioContainerPreference : String = sharedPreferences.getString("audio_format", "").toString()
    private val videoContainerPreference : String = sharedPreferences.getString("video_format", "").toString()
    @SuppressLint("RestrictedApi")
    private val videoResolutionOrder = context.getStringArray(R.array.video_formats_values).filter { it.contains("_") }.map{ it.split("_")[0].dropLast(1) }.reversed()
    private val preferSmallerFormats = sharedPreferences.getBoolean("prefer_smaller_formats", false)

    @SuppressLint("RestrictedApi")
    fun getAudioFormatImportance() : Set<String> {
        val itemValues = context.getStringArray(R.array.format_importance_audio_values).toSet()
        val orderPreferences = sharedPreferences.getString("format_importance_audio", itemValues.joinToString(","))!!.split(",").toMutableSet()
        if (preferSmallerFormats) {
            orderPreferences.add("smallsize")
        }

        return orderPreferences
    }

    @SuppressLint("RestrictedApi")
    fun getVideoFormatImportance() : Set<String> {
        val itemValues = context.getStringArray(R.array.format_importance_video_values).toSet()
        val orderPreferences = sharedPreferences.getString("format_importance_video", itemValues.joinToString(","))!!.split(",").toMutableSet()
        if (preferSmallerFormats) {
            orderPreferences.add("smallsize")
        }

        return orderPreferences
    }


    @SuppressLint("RestrictedApi")
    fun sortAudioFormats(formats: List<Format>) : List<Format> {
        val orderPreferences = getAudioFormatImportance()

        val fieldSorter: Comparator<Format> = Comparator { a, b ->
            for (order in orderPreferences) {
                when(order) {
                    "smallsize" -> {
                        (a.filesize).compareTo(b.filesize)
                    }
                    "id" -> {
                        (audioFormatIDPreference.contains(b.format_id)).compareTo(audioFormatIDPreference.contains(a.format_id))
                    }
                    "language" -> {
                        if (audioLanguagePreference.isBlank())  {
                            0
                        }
                        else {
                            (b.lang?.contains(audioLanguagePreference) == true).compareTo(a.lang?.contains(audioLanguagePreference) == true)
                        }
                    }
                    "codec" -> {
                        ("^(${audioCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE).matches(b.acodec))
                                .compareTo("^(${audioCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE).matches(a.acodec))
                    }
                    "container" -> {
                        (audioContainerPreference == b.container).compareTo(audioContainerPreference == a.container)
                    }
                }
            }
            0
        }
        return formats.sortedWith(fieldSorter)
    }

    @SuppressLint("RestrictedApi")
    fun sortVideoFormats(formats: List<Format>): List<Format> {
        val orderPreferences = getVideoFormatImportance()

        val fieldSorter = object : Comparator<Format> {
            override fun compare(a: Format, b: Format): Int {
                for (order in orderPreferences) {
                    val comparison = when (order) {
                        "smallsize" -> {
                            val result = a.filesize.compareTo(b.filesize)
                            result
                        }
                        "id" -> {
                            videoFormatIDPreference.contains(b.format_id).compareTo(videoFormatIDPreference.contains(a.format_id))
                        }
                        "codec" -> {
                           "^(${videoCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE).matches(b.vcodec.uppercase())
                                .compareTo("^(${videoCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE).matches(a.vcodec.uppercase()))
                        }
                        "resolution" -> {
                            when (videoQualityPreference) {
                                "worst" -> {
                                    b.format_note.contains("worst", ignoreCase = true)
                                        .compareTo(a.format_note.contains("worst", ignoreCase = true))
                                }
                                "best" -> {
                                    b.format_note.contains("best", ignoreCase = true)
                                        .compareTo(a.format_note.contains("best", ignoreCase = true))
                                }
                                else -> {
                                    val preferenceIndex = videoResolutionOrder.indexOfFirst { videoQualityPreference.contains(it, true) }

                                    val aIndex = videoResolutionOrder.indexOfFirst { a.format_note.contains(it, ignoreCase = true) }
                                    val bIndex = videoResolutionOrder.indexOfFirst { b.format_note.contains(it, ignoreCase = true) }

                                    if (aIndex > preferenceIndex || bIndex > preferenceIndex) {
                                        -1
                                    }else if(aIndex == -1 && bIndex == -1){
                                        -1
                                    }else{
                                        bIndex.compareTo(aIndex)
                                    }

                                }
                            }
                        }
                        "no_audio" -> {
                            (b.acodec == "none" || b.acodec == "").compareTo(a.acodec == "none" || a.acodec == "")
                        }
                        "container" -> {
                            videoContainerPreference.equals(b.container, ignoreCase = true)
                                .compareTo(videoContainerPreference.equals(a.container, ignoreCase = true))
                        }
                        else -> 0
                    }
                    if (comparison != 0) return comparison
                }
                return 0
            }
        }

        return formats.sortedWith(fieldSorter)
    }

}
