package com.deniscerri.ytdlnis.database.viewmodel

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
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
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.SearchHistoryItem
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import com.deniscerri.ytdlnis.database.repository.SearchHistoryRepository
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern


class ResultViewModel(private val application: Application) : AndroidViewModel(application) {
    private val tag: String = "ResultViewModel"
    val repository : ResultRepository
    private val searchHistoryRepository : SearchHistoryRepository
    val items : LiveData<List<ResultItem>>
    private val infoUtil: InfoUtil
    private val notificationUtil: NotificationUtil
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
        infoUtil = InfoUtil(application)
        notificationUtil = NotificationUtil(application)
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
                    if (updateResultDataJob?.isCancelled == false){
                        uiState.update {it.copy(
                            processing = false,
                            errorMessage = Pair(R.string.no_results, e.message.toString()),
                            actions = mutableListOf(Pair(R.string.copy_log, ResultAction.COPY_LOG))
                        )}
                        Log.e(tag, e.toString())
                    }

                }
            }
            if (!isForegrounded() && inputQueries.size > 1){
                notificationUtil.showQueriesFinished()
            }
            uiState.update {it.copy(processing = false)}
            res
        }
    }

    private fun isForegrounded(): Boolean {
        return kotlin.runCatching {
            val appProcessInfo = RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(appProcessInfo)
            appProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    appProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }.getOrElse { true }
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

    fun getItemByURL(url: String) : ResultItem? {
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

    val updatingData: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var updateResultData: MutableStateFlow<List<ResultItem?>?> = MutableStateFlow(null)
    private var updateResultDataJob : Job? = null

    val updatingFormats: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var updateFormatsResultData: MutableStateFlow<MutableList<Format>?> = MutableStateFlow(null)
    private var updateFormatsResultDataJob: Job? = null

    suspend fun updateItemData(res: ResultItem){
        if (updateResultDataJob == null || updateResultDataJob?.isCancelled == true || updateResultDataJob?.isCompleted == true){
            updateResultDataJob = viewModelScope.launch(Dispatchers.IO) {
                updatingData.emit(true)
                val result = parseQueries(listOf(res.url))
                updatingData.emit(false)
                updateResultData.emit(result)
            }

            updateResultDataJob?.start()
            updateResultDataJob?.invokeOnCompletion {
                if (it != null){
                    viewModelScope.launch(Dispatchers.IO) {
                        updatingData.emit(false)
                        updateResultData.emit(mutableListOf())
                    }
                }
            }
        }
    }

    suspend fun cancelUpdateItemData(){
        updateResultDataJob?.cancel()
        updatingData.emit(false)
        updateResultData.emit(null)
    }

    suspend fun cancelUpdateFormatsItemData(){
        updateFormatsResultDataJob?.cancel()
        updatingFormats.emit(false)
        updateFormatsResultData.emit(null)
    }

    suspend fun updateFormatItemData(result: ResultItem){
        if (updateFormatsResultDataJob == null || updateFormatsResultDataJob?.isCancelled == true || updateFormatsResultDataJob?.isCompleted == true) {
            updateFormatsResultDataJob = viewModelScope.launch(Dispatchers.IO) {
                updatingFormats.emit(true)
                val formats = kotlin.runCatching {
                    infoUtil.getFormats(result.url)
                }.onFailure { e ->
                    if (e !is kotlinx.coroutines.CancellationException){
                        uiState.update {it.copy(
                            processing = false,
                            errorMessage = Pair(R.string.no_results, e.message.toString()),
                            actions = mutableListOf(Pair(R.string.copy_log, ResultAction.COPY_LOG))
                        )}
                        Log.e(tag, e.toString())
                    }
                }.getOrNull()

                updatingFormats.emit(false)

                formats?.apply {
                    if (formats.isNotEmpty() && isActive) {
                        getItemByURL(result.url)?.apply {
                            this.formats = formats.toMutableList()
                            update(this)
                        }
                        updateFormatsResultData.emit(formats.toMutableList())
                    }
                }

            }
            updateFormatsResultDataJob?.start()
            updateFormatsResultDataJob?.invokeOnCompletion {
                if (it != null){
                    viewModelScope.launch(Dispatchers.IO) {
                        updatingFormats.emit(false)
                        updateFormatsResultData.emit(mutableListOf())
                    }
                }
            }
        }
    }
}