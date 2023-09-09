package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
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
    val repository : ResultRepository
    private val searchHistoryRepository : SearchHistoryRepository
    val items : LiveData<List<ResultItem>>
    val loadingItems = MutableLiveData<Boolean>()
    private val sharedPreferences: SharedPreferences
     var state: ResultsState = ResultsState.IDLE
    enum class ResultsState {
        PROCESSING, IDLE
    }

    init {
        val dao = DBManager.getInstance(application).resultDao
        repository = ResultRepository(dao, getApplication<Application>().applicationContext)
        searchHistoryRepository = SearchHistoryRepository(DBManager.getInstance(application).searchHistoryDao)
        items = repository.allResults.asLiveData()
        loadingItems.postValue(false)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
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
        state = ResultsState.PROCESSING
        if (inputQueries.size == 1){
            parseQuery(inputQueries[0], true)
        }else {
            repository.itemCount.value = inputQueries.size
            loadingItems.postValue(true)
            inputQueries.forEach {
                parseQuery(it, false)
            }
            state = ResultsState.IDLE
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
                    "YT_Video" -> {
                        res = repository.getOne(inputQuery, resetResults)
                    }
                    "YT_Playlist" -> {
                        res = repository.getPlaylist(inputQuery, resetResults)
                    }
                    "Default" -> {
                        res = repository.getDefault(inputQuery, resetResults)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, e.toString())
            }
            if (resetResults) {
                state = ResultsState.IDLE
                loadingItems.postValue(false)
            }
            res
        }
    }
    private fun getQueryType(inputQuery: String) : String {
        var type = "Search"
        val p = Pattern.compile("(^(https?)://(www.)?youtu(.be)?)|(^(https?)://(www.)?piped.video)")
        val m = p.matcher(inputQuery)
        if (m.find()) {
            type = "YT_Video"
            if (inputQuery.contains("playlist?list=")) {
                type = "YT_Playlist"
            }
        } else if (Patterns.WEB_URL.matcher(inputQuery).matches()) {
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