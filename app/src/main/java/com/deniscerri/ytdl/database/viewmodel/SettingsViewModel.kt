package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.RestoreAppDataItem
import com.deniscerri.ytdl.database.repository.CommandTemplateRepository
import com.deniscerri.ytdl.database.repository.CookieRepository
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.repository.SearchHistoryRepository
import com.deniscerri.ytdl.util.BackupSettingsUtil
import com.deniscerri.ytdl.util.FileUtil
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar


class SettingsViewModel(private val application: Application) : AndroidViewModel(application) {
    private val workManager : WorkManager = WorkManager.getInstance(application)
    private val preferences : SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val historyRepository : HistoryRepository
    private val downloadRepository : DownloadRepository
    private val cookieRepository : CookieRepository
    private val commandTemplateRepository : CommandTemplateRepository
    private val searchHistoryRepository : SearchHistoryRepository
    private val observeSourcesRepository : ObserveSourcesRepository

    init {
        val dbManager = DBManager.getInstance(application)
        historyRepository = HistoryRepository(dbManager.historyDao)
        downloadRepository = DownloadRepository(dbManager.downloadDao)
        cookieRepository = CookieRepository(dbManager.cookieDao)
        commandTemplateRepository = CommandTemplateRepository(dbManager.commandTemplateDao)
        searchHistoryRepository = SearchHistoryRepository(dbManager.searchHistoryDao)
        observeSourcesRepository = ObserveSourcesRepository(dbManager.observeSourcesDao, workManager, preferences)
    }

    suspend fun backup(items: List<String> = listOf()) : Result<String> {
        var list = items
        if (list.isEmpty()) {
            list = listOf("settings", "downloads", "queued", "scheduled", "cancelled", "errored", "saved", "cookies", "templates", "shortcuts", "searchHistory", "observeSources")
        }

        val json = JsonObject()
        json.addProperty("app", "YTDLnis_backup")
        list.forEach {
            runCatching {
                when(it){
                    "settings" -> json.add("settings", BackupSettingsUtil.backupSettings(preferences))
                    "downloads" -> json.add("downloads", BackupSettingsUtil.backupHistory(historyRepository))
                    "queued" -> json.add("queued", BackupSettingsUtil.backupQueuedDownloads(downloadRepository))
                    "scheduled" -> json.add("scheduled", BackupSettingsUtil.backupScheduledDownloads(downloadRepository))
                    "cancelled" -> json.add("cancelled", BackupSettingsUtil.backupCancelledDownloads(downloadRepository))
                    "errored" -> json.add("errored", BackupSettingsUtil.backupErroredDownloads(downloadRepository))
                    "saved" -> json.add("saved", BackupSettingsUtil.backupSavedDownloads(downloadRepository))
                    "cookies" -> json.add("cookies", BackupSettingsUtil.backupCookies(cookieRepository))
                    "templates" -> json.add("templates", BackupSettingsUtil.backupCommandTemplates(commandTemplateRepository))
                    "shortcuts" -> json.add("shortcuts", BackupSettingsUtil.backupShortcuts(commandTemplateRepository))
                    "searchHistory" -> json.add("search_history", BackupSettingsUtil.backupSearchHistory(searchHistoryRepository))
                    "observeSources" -> json.add("observe_sources", BackupSettingsUtil.backupObserveSources(observeSourcesRepository))
                }
            }.onFailure {err ->
                return Result.failure(err)
            }
        }

        val currentTime = Calendar.getInstance()
        val dir = File(FileUtil.getCachePath(application) + "/Backups")
        dir.mkdirs()

        val saveFile = File("${dir.absolutePath}/YTDLnis_Backup_${BuildConfig.VERSION_NAME}_${currentTime.get(
            Calendar.YEAR)}-${currentTime.get(Calendar.MONTH) + 1}-${currentTime.get(
            Calendar.DAY_OF_MONTH)}_${currentTime.get(Calendar.HOUR)}-${currentTime.get(Calendar.MINUTE)}-${currentTime.get(Calendar.SECOND)}.json")

        saveFile.delete()
        withContext(Dispatchers.IO) {
            saveFile.createNewFile()
        }
        saveFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(json))

        val res = withContext(Dispatchers.IO) {
            FileUtil.moveFile(saveFile.parentFile!!, application, FileUtil.getBackupPath(application), false) {}
        }

        return Result.success(res[0])
    }

    suspend fun restoreData(data: RestoreAppDataItem, context: Context, resetData: Boolean = false) : Boolean {
        val result = kotlin.runCatching {
            data.settings?.apply {
                val prefs = this
                PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true){
                    clear()
                    prefs.forEach {
                        val key = it.key
                        when(it.type){
                            "String" -> {
                                putString(key, it.value)
                            }
                            "Boolean" -> {
                                putBoolean(key, it.value.toBoolean())
                            }
                            "Int" -> {
                                putInt(key, it.value.toInt())
                            }
                            "HashSet" -> {
                                val value = it.value.replace("(\")|(\\[)|(])|([ \\t])".toRegex(), "").split(",")
                                putStringSet(key, value.toHashSet())
                            }
                        }
                    }
                }
            }


            data.downloads?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) historyRepository.deleteAll(false)
                    data.downloads!!.forEach {
                        historyRepository.insert(it)
                    }
                }
            }

            data.queued?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) downloadRepository.deleteQueued()
                    data.queued!!.forEach {
                        downloadRepository.insert(it)
                    }
                    downloadRepository.startDownloadWorker(listOf(), application)
                }
            }

            data.cancelled?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) downloadRepository.deleteCancelled()
                    data.cancelled!!.forEach {
                        downloadRepository.insert(it)
                    }
                }
            }

            data.errored?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) downloadRepository.deleteErrored()
                    data.errored!!.forEach {
                        downloadRepository.insert(it)
                    }
                }
            }

            data.saved?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) downloadRepository.deleteSaved()
                    data.saved!!.forEach {
                        downloadRepository.insert(it)
                    }
                }
            }

            data.cookies?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) cookieRepository.deleteAll()
                    data.cookies!!.forEach {
                        cookieRepository.insert(it)
                    }
                }
            }

            data.templates?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) commandTemplateRepository.deleteAll()
                    data.templates!!.forEach {
                        commandTemplateRepository.insert(it)
                    }
                }
            }

            data.shortcuts?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) commandTemplateRepository.deleteAllShortcuts()
                    data.shortcuts!!.forEach {
                        commandTemplateRepository.insertShortcut(it)
                    }
                }
            }

            data.searchHistory?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) searchHistoryRepository.deleteAll()
                    data.searchHistory!!.forEach {
                        searchHistoryRepository.insert(it.query)
                    }
                }
            }

            data.observeSources?.apply {
                withContext(Dispatchers.IO){
                    if (resetData) observeSourcesRepository.deleteAll()
                    data.observeSources!!.forEach {
                        observeSourcesRepository.insert(it)
                    }
                }
            }

        }

        return result.isSuccess
    }

}