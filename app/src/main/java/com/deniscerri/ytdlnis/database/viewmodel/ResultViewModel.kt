package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val tag: String = "ResultViewModel"
    private val repository : ResultRepository
    val items : LiveData<List<ResultItem>>
    val loadingItems = MutableLiveData<Boolean>()

    init {
        val dao = DBManager.getInstance(application).resultDao
        val commandDao = DBManager.getInstance(application).commandTemplateDao
        repository = ResultRepository(dao, commandDao, getApplication<Application>().applicationContext)
        items = repository.allResults
        loadingItems.postValue(false)
    }

    fun checkTrending() = viewModelScope.launch(Dispatchers.IO){
        try {
            val item = repository.getFirstResult()
            if (
                item.playlistTitle == getApplication<App>().getString(R.string.trendingPlaylist)
                && item.creationTime < (System.currentTimeMillis() / 1000) - 86400
            ){
                getTrending()
            }
        }catch (e : Exception){
            getTrending()
        }
    }
    fun getTrending() = viewModelScope.launch(Dispatchers.IO){
        loadingItems.postValue(true)
        repository.updateTrending();
        loadingItems.postValue(false)
    }

    fun parseQuery(inputQuery: String, resetResults: Boolean) = viewModelScope.launch(Dispatchers.IO){
        if (resetResults) loadingItems.postValue(true)
        var type = "Search"
        val p = Pattern.compile("^(https?)://(www.)?youtu(.be)?")
        val m = p.matcher(inputQuery!!)
        if (m.find()) {
            type = "Video"
            if (inputQuery!!.contains("playlist?list=")) {
                type = "Playlist"
            }
        } else if (inputQuery!!.contains("http")) {
            type = "Default"
        }
        try {
            when (type) {
                "Search" -> {
                    repository.search(inputQuery, resetResults);
                }
                "Video" -> {
                    repository.getOne(inputQuery, resetResults);
                }
                "Playlist" -> {
                    repository.getPlaylist(inputQuery, resetResults);
                }
                "Default" -> {
                    repository.getDefault(inputQuery, resetResults);
                }
            }
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        loadingItems.postValue(false)
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



    fun getItemByURL(url: String) : ResultItem {
        return repository.getItemByURL(url)
    }
}