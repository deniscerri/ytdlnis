package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : ResultRepository
    private val allResults : LiveData<List<ResultItem>>

    init {
        val dao = DBManager.getInstance(application).resultDao
        repository = ResultRepository(dao)
        allResults = repository.allResults
    }

    fun insert(items: List<ResultItem>) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(items)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun update(item: ResultItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
    }
}