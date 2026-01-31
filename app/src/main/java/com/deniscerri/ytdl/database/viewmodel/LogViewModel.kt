package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.LogRepository
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: LogRepository
    private val downloadRepository: DownloadRepository
    val items: LiveData<List<LogItem>>

    init {
        repository = LogRepository(DBManager.getInstance(application).logDao)
        downloadRepository = DownloadRepository(DBManager.getInstance(application).downloadDao)
        items = repository.items.asLiveData()
    }


    fun getLogFlowByID(id: Long) : LiveData<LogItem?> {
        return repository.getLogFlowByID(id).asLiveData()
    }

    fun getItemById(id: Long): LogItem? {
        return repository.getItem(id)
    }

    fun getAll(): List<LogItem> {
        return repository.getAll()
    }

    suspend fun insert(item: LogItem) : Long {
        return repository.insert(item)
    }

    fun delete(item: LogItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
        downloadRepository.removeLogID(item.id)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
        downloadRepository.removeAllLogID()
    }

    fun update(newLine: String, id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(newLine, id)
    }


    fun exportToFile(id: Long, exported: (File?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try{
            val log = repository.getLogByID(id)
            val dir = File("${FileUtil.getCachePath(application)}/Logs/")
            dir.mkdirs()
            val tmp = File("${dir.absolutePath}/[YTDLnis Log] ${log!!.title}.txt")
            tmp.delete()
            tmp.createNewFile()
            tmp.writeText(log.content)
            val res = withContext(Dispatchers.IO) {
                FileUtil.moveFile(tmp.parentFile!!, application, FileUtil.getDefaultApplicationPath() + "/Exported Logs", false) {}
            }

            exported(File(res[0]))
        }catch (e: Exception){
            e.printStackTrace()
            exported(null)
        }
    }

}