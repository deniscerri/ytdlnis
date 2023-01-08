package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : HistoryRepository
    private val allHistory : LiveData<List<HistoryItem>>

    init {
        val dao = DBManager.getInstance(application).historyDao
        repository = HistoryRepository(dao)
        allHistory = repository.allHistory
    }

    fun insert(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun delete(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun deleteDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDuplicates()
    }

    fun update(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
    }

    fun clearDeleted() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearDeletedHistory()
    }
}