package com.deniscerri.ytdl.database

import androidx.room.TypeConverter
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesMonthlyConfig
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesWeeklyConfig
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.google.gson.Gson


class Converters {
    @TypeConverter
    fun stringToListOfFormats(value: String) = Gson().fromJson(value, Array<Format>::class.java).toMutableList()
    @TypeConverter
    fun listOfFormatsToString(list: List<Format?>?) = Gson().toJson(list).toString()

    @TypeConverter
    fun stringToListOfStrings(value: String) = Gson().fromJson(value, Array<String>::class.java).toList()

    @TypeConverter
    fun listOfStringsToString(list: List<String>) = Gson().toJson(list).toString()

    @TypeConverter
    fun formatToString(format: Format): String = Gson().toJson(format)

    @TypeConverter
    fun stringToFormat(string: String): Format = Gson().fromJson(string, Format::class.java)


    @TypeConverter
    fun typeToString(type: DownloadViewModel.Type) : String = type.toString()
    @TypeConverter
    fun stringToType(string: String) : DownloadViewModel.Type {
        return when(string){
            "audio" -> DownloadViewModel.Type.audio
            "video" -> DownloadViewModel.Type.video
            else -> DownloadViewModel.Type.command
        }
    }

    @TypeConverter
    fun audioPreferencesToString(audioPreferences: AudioPreferences): String = Gson().toJson(audioPreferences)
    @TypeConverter
    fun stringToAudioPreferences(string: String): AudioPreferences = Gson().fromJson(string, AudioPreferences::class.java)

    @TypeConverter
    fun videoPreferencesToString(videoPreferences: VideoPreferences): String = Gson().toJson(videoPreferences)
    @TypeConverter
    fun stringToVideoPreferences(string: String): VideoPreferences = Gson().fromJson(string, VideoPreferences::class.java)

    @TypeConverter
    fun stringToListOfChapters(value: String?) = Gson().fromJson(value, Array<ChapterItem>::class.java).toMutableList()

    @TypeConverter
    fun listOfChaptersToString(list: List<ChapterItem?>?): String {
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun downloadItemToString(downloadItem: DownloadItem) : String {
        val gson = Gson()
        return gson.toJson(downloadItem)
    }

    @TypeConverter
    fun stringToDownloadItem(s: String) = Gson().fromJson(s, DownloadItem::class.java)

    @TypeConverter
    fun mutableListOfStringsToString(s: MutableList<String>) = Gson().toJson(s)

    @TypeConverter
    fun stringtoMutableListofStrings(s: String) = Gson().fromJson(s, Array<String>::class.java).toMutableList()

    @TypeConverter
    fun observeSourcesWeeklyConfigToString(c: ObserveSourcesWeeklyConfig) = Gson().toJson(c)

    @TypeConverter
    fun stringToObserveSourcesWeeklyConfig(s: String) = Gson().fromJson(s, ObserveSourcesWeeklyConfig::class.java)

    @TypeConverter
    fun observeSourcesMonthlyConfigToString(c: ObserveSourcesMonthlyConfig) = Gson().toJson(c)

    @TypeConverter
    fun stringToObserveSourcesMonthlyConfig(s: String) = Gson().fromJson(s, ObserveSourcesMonthlyConfig::class.java)

}