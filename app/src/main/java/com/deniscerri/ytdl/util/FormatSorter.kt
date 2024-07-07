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

        val fieldSorter: Comparator<Format> = object : Comparator<Format> {
            override fun compare(a: Format, b: Format): Int {
                for (order in orderPreferences) {
                    val comparison = when (order) {
                        "smallsize" -> {
                            (a.filesize).compareTo(b.filesize)
                        }

                        "id" -> {
                            (audioFormatIDPreference.contains(b.format_id)).compareTo(
                                audioFormatIDPreference.contains(a.format_id)
                            )
                        }

                        "language" -> {
                            if (audioLanguagePreference.isBlank()) {
                                0
                            } else {
                                (b.lang?.contains(audioLanguagePreference) == true).compareTo(
                                    a.lang?.contains(
                                        audioLanguagePreference
                                    ) == true
                                )
                            }
                        }

                        "codec" -> {
                            ("^(${audioCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE)
                                    .matches(b.acodec))
                                    .compareTo(
                                        "^(${audioCodecPreference}).+$".toRegex(RegexOption.IGNORE_CASE)
                                            .matches(a.acodec)
                                    )
                        }

                        "container" -> {
                            (audioContainerPreference == b.container).compareTo(
                                audioContainerPreference == a.container
                            )
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

    //OLD CODE JUST FOR STORAGE / REFERENCE

    /*
    * fun getPreferredAudioRequirements(): MutableList<(Format) -> Int> {
        val requirements: MutableList<(Format) -> Int> = mutableListOf()

        val itemValues = resources.getStringArray(R.array.format_importance_audio_values).toSet()
        val prefAudio = sharedPreferences.getString("format_importance_audio", itemValues.joinToString(","))!!

        prefAudio.split(",").forEachIndexed { idx, s ->
            val importance = (itemValues.size - idx) * 10
            when(s) {
                "id" -> {
                    requirements.add {it: Format -> if (audioFormatIDPreference.contains(it.format_id)) importance else 0}
                }
                "language" -> {
                    sharedPreferences.getString("audio_language", "")?.apply {
                        if (this.isNotBlank()){
                            requirements.add { it: Format -> if (it.lang?.contains(this) == true) importance else 0 }
                        }
                    }
                }
                "codec" -> {
                    requirements.add {it: Format -> if ("^(${audioCodec}).+$".toRegex(RegexOption.IGNORE_CASE).matches(it.acodec)) importance else 0}
                }
                "container" -> {
                    requirements.add {it: Format -> if (it.container == audioContainer) importance else 0 }
                }
            }
        }

        return requirements
    }

    //requirement and importance
    @SuppressLint("RestrictedApi")
    fun getPreferredVideoRequirements(): MutableList<(Format) -> Int> {
        val requirements: MutableList<(Format) -> Int> = mutableListOf()

        val itemValues = resources.getStringArray(R.array.format_importance_video_values).toSet()
        val prefVideo = sharedPreferences.getString("format_importance_video", itemValues.joinToString(","))!!

        prefVideo.split(",").forEachIndexed { idx, s ->
            var importance = (itemValues.size - idx) * 10

            when(s) {
                "id" -> {
                    requirements.add { it: Format -> if (formatIDPreference.contains(it.format_id)) importance else 0 }
                }
                "resolution" -> {
                    context.getStringArray(R.array.video_formats_values)
                        .filter { it.contains("_") }
                        .map{ it.split("_")[0].dropLast(1)
                        }.toMutableList().apply {
                            when(videoQualityPreference) {
                                "worst" -> {
                                    requirements.add { it: Format -> if (it.format_note.contains("worst", ignoreCase = true)) (importance) else 0 }
                                }
                                "best" -> {
                                    requirements.add { it: Format -> if (it.format_note.contains("best", ignoreCase = true)) (importance) else 0 }
                                }
                                else -> {
                                    val preferenceIndex = this.indexOfFirst { videoQualityPreference.contains(it) }
                                    val preference = this[preferenceIndex]
                                    for(i in 0..preferenceIndex){
                                        removeAt(0)
                                    }
                                    add(0, preference)
                                    forEachIndexed { index, res ->
                                        requirements.add { it: Format -> if (it.format_note.contains(res, ignoreCase = true)) (importance - index - 1) else 0 }
                                    }
                                }
                            }

                        }
                }
                "codec" -> {
                    requirements.add { it: Format -> if ("^(${videoCodec})(.+)?$".toRegex(RegexOption.IGNORE_CASE).matches(it.vcodec)) importance else 0 }
                }
                "no_audio" -> {
                    requirements.add { it: Format -> if (it.acodec == "none" || it.acodec == "") importance else 0 }
                }
                "container" -> {
                    requirements.add { it: Format ->
                        if (videoContainer == "mp4")
                            if (it.container.equals("mpeg_4", true)) importance else 0
                        else
                            if (it.container.equals(videoContainer, true)) importance else 0
                    }
                }
            }
        }

        return  requirements
    }
    * */

}
