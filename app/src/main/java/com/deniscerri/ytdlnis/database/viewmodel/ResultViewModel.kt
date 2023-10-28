package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class ResultViewModel(application: Application) : AndroidViewModel(application) {
    private val tag: String = "ResultViewModel"
    val repository : ResultRepository
    private val searchHistoryRepository : SearchHistoryRepository
    val items : LiveData<List<ResultItem>>
    data class ResultsUiState(
        var processing: Boolean,
        var errorMessage: Pair<Int, String>?,
        var actions: MutableList<Pair<Int, ResultAction>>?
    )

    enum class ResultAction {
        COPY_LOG
    }

    val uiState: MutableStateFlow<ResultsUiState> = MutableStateFlow(ResultsUiState(
        processing = false,
        errorMessage = null,
        actions = null
    ))
    private val sharedPreferences: SharedPreferences

    init {
        val dao = DBManager.getInstance(application).resultDao
        repository = ResultRepository(dao, getApplication<Application>().applicationContext)
        searchHistoryRepository = SearchHistoryRepository(DBManager.getInstance(application).searchHistoryDao)
        items = repository.allResults.asLiveData()
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
    suspend fun parseQueries(inputQueries: List<String>) : List<ResultItem?> {
        if (inputQueries.size > 1){
            repository.itemCount.value = inputQueries.size
        }
        val resetResults = inputQueries.size == 1

        uiState.update {it.copy(processing = true, errorMessage = null, actions = null)}
        return withContext(Dispatchers.IO){
            val res = mutableListOf<ResultItem?>()
            inputQueries.forEach { inputQuery ->
                val type = getQueryType(inputQuery)
                try {
                     when (type) {
                        "Search" -> res.addAll(repository.search(inputQuery, resetResults))
                        "YT_Video" -> res.addAll(repository.getYoutubeVideo(inputQuery, resetResults))
                        "YT_Playlist" -> res.addAll(repository.getPlaylist(inputQuery, resetResults))
                        else -> res.addAll(repository.getDefault(inputQuery, resetResults))
                    }
                } catch (e: Exception) {
                    uiState.update {it.copy(
                        processing = false,
                        errorMessage = Pair(R.string.no_results, e.message.toString()),
                        actions = mutableListOf(Pair(R.string.copy_log, ResultAction.COPY_LOG))
                    )}
                    Log.e(tag, e.toString())
                }
            }
            uiState.update {it.copy(processing = false)}
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