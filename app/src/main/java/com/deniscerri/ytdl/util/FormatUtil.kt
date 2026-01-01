package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.Format
import kotlin.math.max
import kotlin.math.min

class FormatUtil(private var context: Context) {
    private val sharedPreferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val videoFormatIDPreference : List<String> = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
    private val audioFormatIDPreference : List<String> = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }
    private val videoQualityPreference : String = sharedPreferences.getString("video_quality", "best").toString()
    private val audioLanguagePreference : String = sharedPreferences.getString("audio_language", "").toString()
    private val audioCodecPreference : String =  sharedPreferences.getString("audio_codec", "").toString()
    private val videoCodecPreference : String =  sharedPreferences.getString("video_codec", "").toString()
    private val audioContainerPreference : String = sharedPreferences.getString("audio_format", "").toString()
    private val videoContainerPreference : String = sharedPreferences.getString("video_format", "").toString()
    private val videoResolutionOrder = context.getStringArray(R.array.video_formats_values)
                                            .filter { it.contains("_") }
                                            .map{ it.split("_")[0].dropLast(1) }.toMutableList().apply {
                                                this.reverse()
                                            }

    @SuppressLint("RestrictedApi")
    fun getAudioFormatImportance() : List<String> {
        val preferredFormatSize = sharedPreferences.getString("preferred_format_size", "")

        if (sharedPreferences.getBoolean("use_format_sorting", false)) {
            val itemValues = context.getStringArray(R.array.format_importance_audio_values).toMutableList()
            val orderPreferences = sharedPreferences.getString("format_importance_audio", itemValues.joinToString(","))!!.split(",").toMutableList()

            if (preferredFormatSize == "smallest") {
                orderPreferences.remove("file_size")
                orderPreferences.add(0,"smallsize")
            }

            val preferContainerOverCodec = sharedPreferences.getBoolean("prefer_container_over_codec_audio", false)
            if(preferContainerOverCodec) {
                orderPreferences.remove("codec")
            }

            return orderPreferences
        }else {
            val formatImportance = mutableListOf("id", "language", "codec", "container")
            if (preferredFormatSize == "smallest") {
                formatImportance.add("smallsize")
            }else if (preferredFormatSize == "largest") {
                formatImportance.add("file_size")
            }

            val preferContainerOverCodec = sharedPreferences.getBoolean("prefer_container_over_codec_audio", false)
            if(preferContainerOverCodec) {
                formatImportance.remove("codec")
            }

            val preferDRC = sharedPreferences.getBoolean("prefer_drc_audio", false)
            if (preferDRC) {
                formatImportance.add(0,"prefer_drc")
            }

            return formatImportance
        }
    }

    @SuppressLint("RestrictedApi")
    fun getVideoFormatImportance() : List<String> {
        val preferredFormatSize = sharedPreferences.getString("preferred_format_size", "")

        if (sharedPreferences.getBoolean("use_format_sorting", false)) {
            val itemValues = context.getStringArray(R.array.format_importance_video_values).toList()
            val orderPreferences = sharedPreferences.getString("format_importance_video", itemValues.joinToString(","))!!.split(",").toMutableList()

            if (preferredFormatSize == "smallest") {
                orderPreferences.remove("file_size")
                orderPreferences.add("smallsize")
            }

            return orderPreferences
        }else {
            val formatImportance = mutableListOf("id","resolution", "codec", "container")
            if (preferredFormatSize == "smallest") {
                formatImportance.add("smallsize")
            }else if (preferredFormatSize == "largest") {
                formatImportance.add("file_size")
            }

            return formatImportance
        }
    }


    @SuppressLint("RestrictedApi")
    fun sortAudioFormats(formats: List<Format>) : List<Format> {
        val orderPreferences = getAudioFormatImportance()

        val comparator = Comparator<Format> { a, b ->
            if ("prefer_drc" in orderPreferences) {
                val comparison = (b.format_note.contains("drc", ignoreCase = true)).compareTo(
                    a.format_note.contains("drc", ignoreCase = true)
                )

                if (comparison != 0) return@Comparator comparison
            }

            for (order in orderPreferences) {
                val comparison = when (order) {
                    "smallsize" -> {
                        (a.filesize).compareTo(b.filesize)
                    }
                    "file_size" -> {
                        b.filesize.compareTo(a.filesize)
                    }
                    "id" -> {
                        if (audioFormatIDPreference.isNotEmpty()) {
                            (audioFormatIDPreference.contains(b.format_id)).compareTo(
                                audioFormatIDPreference.contains(a.format_id)
                            )
                        } else 0
                    }
                    "language" -> {
                        if (audioLanguagePreference.isBlank()) {
                            (b.format_note.contains("default", true)).compareTo(
                                a.format_note.contains(
                                    "default", true
                                )
                            )
                        } else {
                            val res = (b.lang?.contains(audioLanguagePreference) == true).compareTo(
                                a.lang?.contains(
                                    audioLanguagePreference
                                ) == true
                            )
                            if(res == 0) {
                                (b.format_note.contains("default", true)).compareTo(
                                    a.format_note.contains(
                                        "default", true
                                    )
                                )
                            }else {
                                res
                            }
                        }
                    }
                    "codec" -> {
                        if (audioCodecPreference.isNotBlank()) {
                            ("^(${audioCodecPreference}).*$".toRegex(RegexOption.IGNORE_CASE)
                                .matches(b.acodec))
                                .compareTo(
                                    "^(${audioCodecPreference}).*$".toRegex(RegexOption.IGNORE_CASE)
                                        .matches(a.acodec)
                                )
                        } else 0
                    }

                    "container" -> {
                        if (audioContainerPreference.isNotBlank()) {
                            (audioContainerPreference == b.container).compareTo(
                                audioContainerPreference == a.container
                            )
                        } else 0
                    }
                    else -> 0
                }
                if (comparison != 0) return@Comparator comparison
            }
            return@Comparator 0
        }
        return formats.sortedWith(comparator)
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
                        "file_size" -> {
                            val result = b.filesize.compareTo(a.filesize)
                            result
                        }
                        "id" -> {
                            if (videoFormatIDPreference.isEmpty()) {
                                0
                            }else {
                                videoFormatIDPreference.contains(b.format_id).compareTo(videoFormatIDPreference.contains(a.format_id))
                            }
                        }
                        "codec" -> {
                            if (videoCodecPreference.isBlank()) {
                                0
                            }else {
                                val first = videoCodecPreference.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(b.vcodec.uppercase())
                                val second = videoCodecPreference.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(a.vcodec.uppercase())
                                first.compareTo(second)
                            }
                        }
                        "resolution" -> {
                            when (videoQualityPreference) {
                                "worst" -> {
                                    val containsWorst = b.format_note.contains("worst", ignoreCase = true)
                                        .compareTo(a.format_note.contains("worst", ignoreCase = true))

                                    val worstFilesize = a.filesize.compareTo(b.filesize)

                                    min(containsWorst, worstFilesize)
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
                                        0
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
                            if (videoContainerPreference.isBlank()) {
                                0
                            }else {
                                videoContainerPreference.equals(b.container, ignoreCase = true)
                                    .compareTo(videoContainerPreference.equals(a.container, ignoreCase = true))
                            }
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

    fun getGenericAudioFormats(resources: Resources) : MutableList<Format>{
        val audioFormatIDPreference = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }
        val audioFormats = resources.getStringArray(R.array.audio_formats)
        val audioFormatsValues = resources.getStringArray(R.array.audio_formats_values)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("audio_format", "")
        val acodecPreference = sharedPreferences.getString("audio_codec", "")!!.run {
            if (this.isEmpty()){
                resources.getString(R.string.defaultValue)
            }else{
                val audioCodecs = resources.getStringArray(R.array.audio_codec)
                val audioCodecsValues = resources.getStringArray(R.array.audio_codec_values)
                audioCodecs[audioCodecsValues.indexOf(this)]
            }
        }
        audioFormats.forEachIndexed { idx, it -> formats.add(Format(audioFormatsValues[idx], containerPreference!!,"",acodecPreference!!, "",0, it)) }
        audioFormatIDPreference.forEach { formats.add(Format(it, containerPreference!!,"",resources.getString(R.string.preferred_format_id), "",1, it)) }
        return formats
    }

    fun getGenericVideoFormats(resources: Resources) : MutableList<Format>{
        val formatIDPreference = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
        val videoFormatsValues = resources.getStringArray(R.array.video_formats_values)
        val videoFormats = resources.getStringArray(R.array.video_formats)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("video_format", "")
        val audioCodecPreference = sharedPreferences.getString("audio_codec", "")!!.run {
            if (this.isNotEmpty()){
                val audioCodecs = resources.getStringArray(R.array.audio_codec)
                val audioCodecsValues = resources.getStringArray(R.array.audio_codec_values)
                audioCodecs[audioCodecsValues.indexOf(this)]
            }else this
        }
        val videoCodecPreference = sharedPreferences.getString("video_codec", "")!!.run {
            if (this.isEmpty()){
                resources.getString(R.string.defaultValue)
            }else{
                val videoCodecs = resources.getStringArray(R.array.video_codec)
                val videoCodecsValues = resources.getStringArray(R.array.video_codec_values)
                videoCodecs[videoCodecsValues.indexOf(this)]
            }
        }
        videoFormatsValues.forEachIndexed { index, it ->  formats.add(Format(it, containerPreference!!,videoCodecPreference,audioCodecPreference, "",0, videoFormats[index])) }
        formatIDPreference.forEach { formats.add(Format(it, containerPreference!!,resources.getString(R.string.preferred_format_id),"", "",1, it)) }
        return formats
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
