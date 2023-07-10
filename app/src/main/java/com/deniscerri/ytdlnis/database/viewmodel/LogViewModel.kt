package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: LogRepository
    val items: LiveData<List<LogItem>>

    init {
        val dao = DBManager.getInstance(application).logDao
        repository = LogRepository(dao)
        items = repository.items.asLiveData()
    }


    fun getItemById(id: Long): LogItem{
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
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun update(newLine: String, id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(newLine, id)
    }


}