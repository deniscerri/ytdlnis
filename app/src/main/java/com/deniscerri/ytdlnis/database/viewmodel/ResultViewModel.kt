package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : ResultRepository
    val items : LiveData<List<ResultItem>>

    init {
        val dao = DBManager.getInstance(application).resultDao
        repository = ResultRepository(dao)
        items = repository.allResults
    }

    fun checkTrending() : Boolean{
        return items.value?.get(0)?.playlistTitle.equals("ytdlnis-TRENDING");
    }
    fun getTrending() = viewModelScope.launch(Dispatchers.IO){
        repository.updateTrending(getApplication<Application>().applicationContext);
    }

    fun insert(items: ArrayList<ResultItem?>) = viewModelScope.launch(Dispatchers.IO){
        items.forEach {
            repository.insert(it!!)
        }
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun update(item: ResultItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
    }
}