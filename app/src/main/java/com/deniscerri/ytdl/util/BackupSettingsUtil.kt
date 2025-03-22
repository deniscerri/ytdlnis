package com.deniscerri.ytdl.util

import android.content.SharedPreferences
import com.deniscerri.ytdl.database.models.BackupSettingsItem
import com.deniscerri.ytdl.database.repository.CommandTemplateRepository
import com.deniscerri.ytdl.database.repository.CookieRepository
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.repository.SearchHistoryRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupSettingsUtil {
    fun backupSettings(preferences: SharedPreferences) : JsonArray {
        runCatching {
            val prefs = preferences.all
            prefs.remove("app_language")

            val res = prefs.map { BackupSettingsItem(
                key = it.key,
                value = it.value.toString(),
                type = it.value!!::class.simpleName
            ) }

            val arr = JsonArray()
            res.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupHistory(historyRepository: HistoryRepository) : JsonArray {
        runCatching {
            val historyItems = withContext(Dispatchers.IO) {
                historyRepository.getAll()
            }
            val arr = JsonArray()
            historyItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupQueuedDownloads(downloadRepository: DownloadRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadRepository.getQueuedDownloads()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupScheduledDownloads(downloadRepository: DownloadRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadRepository.getScheduledDownloads()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupCancelledDownloads(downloadRepository: DownloadRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadRepository.getCancelledDownloads()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupErroredDownloads(downloadRepository: DownloadRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadRepository.getErroredDownloads()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupSavedDownloads(downloadRepository: DownloadRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                downloadRepository.getSavedDownloads()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupCookies(cookieRepository: CookieRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                cookieRepository.getAll()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupCommandTemplates(commandTemplateRepository: CommandTemplateRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                commandTemplateRepository.getAll()
            }
            val arr = JsonArray()
            items.forEach {
                it.useAsExtraCommand = false
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupShortcuts(commandTemplateRepository: CommandTemplateRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                commandTemplateRepository.getAllShortCuts()
            }
            val arr = JsonArray()
            items.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupSearchHistory(searchHistoryRepository: SearchHistoryRepository) : JsonArray {
        runCatching {
            val historyItems = withContext(Dispatchers.IO) {
                searchHistoryRepository.getAll()
            }
            val arr = JsonArray()
            historyItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

    suspend fun backupObserveSources(observeSourcesRepository: ObserveSourcesRepository) : JsonArray {
        runCatching {
            val observeSourcesItems = withContext(Dispatchers.IO) {
                observeSourcesRepository.getAll()
            }
            val arr = JsonArray()
            observeSourcesItems.forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

}