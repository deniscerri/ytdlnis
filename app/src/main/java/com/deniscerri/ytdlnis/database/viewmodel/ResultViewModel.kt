package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.SearchHistoryItem
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import com.deniscerri.ytdlnis.database.repository.SearchHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val tag: String = "ResultViewModel"
    private val repository : ResultRepository
    private val searchHistoryRepository : SearchHistoryRepository
    val items : LiveData<List<ResultItem>>
    val loadingItems = MutableLiveData<Boolean>()
    var itemCount : LiveData<Int>
    private val sharedPreferences: SharedPreferences

    init {
        val dao = DBManager.getInstance(application).resultDao
        val commandDao = DBManager.getInstance(application).commandTemplateDao
        repository = ResultRepository(dao, commandDao, getApplication<Application>().applicationContext)
        searchHistoryRepository = SearchHistoryRepository(DBManager.getInstance(application).searchHistoryDao)
        items = repository.allResults
        loadingItems.postValue(false)
        itemCount = repository.itemCount
        sharedPreferences = application.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
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
            e.printStackTrace()
            getTrending()
        }
    }
    fun getTrending() = viewModelScope.launch(Dispatchers.IO){
        if (sharedPreferences.getBoolean("home_recommendations", false)){
            repository.updateTrending()
        }else{
            deleteAll()
        }
    }
    suspend fun parseQueries(inputQueries: List<String>){
        if (inputQueries.size == 1){
            parseQuery(inputQueries[0], true)
        }else {
            repository.itemCount.postValue(2)
            loadingItems.postValue(true)
            inputQueries.forEach {
                parseQuery(it, false)
            }
            loadingItems.postValue(false)
        }
    }

    suspend fun parseQuery(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?> {
        if (resetResults)
            loadingItems.postValue(true)
        val type = getQueryType(inputQuery)
        var res = arrayListOf<ResultItem?>()
        return withContext(Dispatchers.IO){
            try {
                when (type) {
                    "Search" -> {
                        res = repository.search(inputQuery, resetResults)
                    }
                    "Video" -> {
                        res = repository.getOne(inputQuery, resetResults)
                    }
                    "Playlist" -> {
                        res = repository.getPlaylist(inputQuery, resetResults)

                    }
                    "Default" -> {
                        res = repository.getDefault(inputQuery, resetResults)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, e.toString())
            }
            loadingItems.postValue(false)
            res
        }
    }
    fun getQueryType(inputQuery: String) : String {
        var type = "Search"
        val p = Pattern.compile("^(https?)://(www.)?(music.)?youtu(.be)?")
        val m = p.matcher(inputQuery)
        if (m.find()) {
            type = "Video"
            if (inputQuery.contains("playlist?list=")) {
                type = "Playlist"
            }
        } else if (inputQuery.contains("http")) {
            type = "Default"
        }
        return type
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
        repository.update(item)
    }

    fun getItemByURL(url: String) : ResultItem {
        return repository.getItemByURL(url)
    }

    fun addSearchQueryToHistory(query: String) = viewModelScope.launch(Dispatchers.IO) {
        val allQueries = searchHistoryRepository.getAll()
        if (allQueries.none { it.query == query }){
            searchHistoryRepository.insert(query)
        }

    }

    fun removeSearchQueryFromHistory(query: String) = viewModelScope.launch(Dispatchers.IO) {
        val allQueries = searchHistoryRepository.getAll()
        if (allQueries.any { it.query == query }){
            searchHistoryRepository.delete(query)
        }

    }

    fun deleteAllSearchQueryHistory() = viewModelScope.launch(Dispatchers.IO){
        searchHistoryRepository.deleteAll()
    }

    fun getSearchHistory() : List<SearchHistoryItem> {
        return searchHistoryRepository.getAll()
    }

    fun deleteSelected(selectedItems : List<ResultItem>) = viewModelScope.launch(Dispatchers.IO) {
        selectedItems.forEach {
            repository.delete(it)
        }
    }
}