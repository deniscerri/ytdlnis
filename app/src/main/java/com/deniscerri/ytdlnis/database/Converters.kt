package com.deniscerri.ytdlnis.database

import androidx.room.TypeConverter
import com.deniscerri.ytdlnis.database.models.AudioPreferences
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.VideoPreferences
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Calendar


class Converters {
    @TypeConverter
    fun stringToListOfFormats(value: String?): ArrayList<Format> {
        val listType: Type = object : TypeToken<ArrayList<Format?>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun listOfFormatsToString(list: ArrayList<Format?>?): String {
        val gson = Gson()
        return gson.toJson(list)
    }

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
            "command" -> DownloadViewModel.Type.command
            else -> DownloadViewModel.Type.terminal
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
}