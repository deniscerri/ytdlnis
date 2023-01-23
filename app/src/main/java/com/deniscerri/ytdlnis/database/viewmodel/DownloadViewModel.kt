package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : DownloadRepository
    private val allDownloads : LiveData<List<DownloadItem>>

    init {
        val dao = DBManager.getInstance(application).downloadDao
        repository = DownloadRepository(dao)
        allDownloads = repository.allDownloads
    }

    fun startWork(items: List<DownloadItem>){
        items.forEach {
            insertDownload(it)
        }

    }

    fun insertDownload(item: DownloadItem) : LiveData<Long> {
        val result = MutableLiveData<Long>()
        viewModelScope.launch(Dispatchers.IO){
            val id = repository.insert(item)
            result.postValue(id)
        }
        return result
    }

    fun deleteDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
    }

    fun updateDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
    }

    fun getItemByID(id: Long) : DownloadItem {
        return repository.getItemByID(id)
    }
}