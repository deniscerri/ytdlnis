package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.BackupSettingsItem
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.RestoreAppDataItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchHistoryItem
import com.deniscerri.ytdl.database.models.TemplateShortcut
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.CommandTemplateRepository
import com.deniscerri.ytdl.database.repository.CookieRepository
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.repository.SearchHistoryRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupSettingsUtil {
    data class Result(val data: RestoreAppDataItem, val summary: String)

    suspend fun parse(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val json = Gson().fromJson(text, JsonObject::class.java)
        val gson = Gson()
        val summary = StringBuilder()

        // Reads one section of the backup. Rows get fresh ids so they can't clash with existing ones.
        fun <T> section(key: String, label: Int, type: Class<T>, resetId: ((T) -> Unit)? = null): List<T>? {
            if (!json.has(key)) return null
            val items = json.getAsJsonArray(key).map { element ->
                gson.fromJson(element.toString(), type).also { resetId?.invoke(it) }
            }
            summary.appendLine("${context.getString(label)}: ${items.size}")
            return items
        }

        val data = RestoreAppDataItem(
            settings = section("settings", R.string.settings, BackupSettingsItem::class.java),
            searchResults = section("searchResults", R.string.search_results, ResultItem::class.java) { it.id = 0L },
            downloads = section("downloads", R.string.downloads, HistoryItem::class.java) { it.id = 0L },
            queued = section("queued", R.string.queue, DownloadItem::class.java) { it.id = 0L },
            scheduled = section("scheduled", R.string.scheduled, DownloadItem::class.java) { it.id = 0L },
            cancelled = section("cancelled", R.string.cancelled, DownloadItem::class.java) { it.id = 0L },
            errored = section("errored", R.string.errored, DownloadItem::class.java) { it.id = 0L },
            saved = section("saved", R.string.saved, DownloadItem::class.java) { it.id = 0L },
            cookies = section("cookies", R.string.cookies, CookieItem::class.java) { it.id = 0L },
            templates = section("templates", R.string.command_templates, CommandTemplate::class.java) { it.id = 0L },
            shortcuts = section("shortcuts", R.string.shortcuts, TemplateShortcut::class.java) { it.id = 0L },
            searchHistory = section("search_history", R.string.search_history, SearchHistoryItem::class.java) { it.id = 0L },
            observeSources = section("observe_sources", R.string.observe_sources, ObserveSourcesItem::class.java) { it.id = 0L },
        )

        Result(data, summary.toString())
    }

    fun backupSettings(preferences: SharedPreferences) : JsonArray {
        runCatching {
            val prefs = preferences.all
            prefs.remove("dlpVersion")
            prefs.remove("app_language")
            prefs.remove("cache_downloads")
            prefs.remove("use_alarm_for_scheduling")
            prefs.remove("use_bgutils_potoken_generator")
            prefs.remove("ytdlnis_icon")

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

    suspend fun backupSearchResults(resultRepository: ResultRepository) : JsonArray {
        runCatching {
            val items = withContext(Dispatchers.IO) {
                resultRepository.getAll()
            }
            val arr = JsonArray()
            items.forEach {
                it.creationTime = Long.MAX_VALUE
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
            historyItems.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            items.reversed().forEach {
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
            historyItems.reversed().forEach {
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
            observeSourcesItems.reversed().forEach {
                arr.add(JsonParser.parseString(Gson().toJson(it)).asJsonObject)
            }
            return arr
        }
        return JsonArray()
    }

}