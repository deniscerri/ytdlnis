package com.deniscerri.ytdl.database.viewmodel

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.dao.ResultDao
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.SearchHistoryItem
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.repository.SearchHistoryRepository
import com.deniscerri.ytdl.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CancellationException


class ResultViewModel(private val application: Application) : AndroidViewModel(application) {
    private val tag: String = "ResultViewModel"
    val repository : ResultRepository
    private val searchHistoryRepository : SearchHistoryRepository
    val items : LiveData<List<ResultItem>>
    private var _items = MediatorLiveData<List<ResultItem>>()
    val playlistFilter = MutableLiveData("")

    private val notificationUtil: NotificationUtil
    private val dao: ResultDao
    data class ResultsUiState(
        var processing: Boolean,
        var errorMessage: String?,
        var canContinueOnError: Boolean = false
    )

    val uiState: MutableStateFlow<ResultsUiState> = MutableStateFlow(ResultsUiState(
        processing = false,
        errorMessage = null,
    ))


    val updatingData: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var updateResultData: MutableStateFlow<List<ResultItem?>?> = MutableStateFlow(null)
    private var updateResultDataJob : Job? = null

    val updatingFormats: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var updateFormatsResultData: MutableStateFlow<MutableList<Format>?> = MutableStateFlow(null)
    private var updateFormatsResultDataJob: Job? = null

    private var parsingQueries: Job? = null
    private var parsingQueriesJobList : MutableList<Job> = mutableListOf()

    private val sharedPreferences: SharedPreferences

    init {
        val dbManager = DBManager.getInstance(application)
        dao = dbManager.resultDao
        val commandTemplateDao = dbManager.commandTemplateDao
        repository = ResultRepository(dao, commandTemplateDao, getApplication<Application>().applicationContext)
        searchHistoryRepository = SearchHistoryRepository(DBManager.getInstance(application).searchHistoryDao)

        items = repository.allResults.asLiveData()
        _items.addSource(items){
            filter()
        }
        _items.addSource(playlistFilter) {
            filter()
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        notificationUtil = NotificationUtil(application)
    }

    fun filter() = viewModelScope.launch(Dispatchers.IO){
        _items.postValue(repository.getFiltered(playlistFilter.value ?: ""))
    }

    fun setPlaylistFilter(p: String){
        playlistFilter.value = p
    }

    private fun resetPlaylistFilter() = viewModelScope.launch(Dispatchers.Main) {
        _items.removeSource(playlistFilter)
        playlistFilter.value = ""
        _items.addSource(playlistFilter) {
            filter()
        }
    }

    fun getFilteredList() : LiveData<List<ResultItem>> {
        return _items
    }

    fun checkTrending() = viewModelScope.launch(Dispatchers.IO){
        try {
            val item = repository.getFirstResult()
            if (
                item.playlistTitle == getApplication<App>().getString(R.string.trendingPlaylist)
                && item.creationTime < (System.currentTimeMillis() / 1000) - 86400
            ){
                getHomeRecommendations()
            }
        }catch (e : Exception){
            e.printStackTrace()
            getHomeRecommendations()
        }
    }
    fun getHomeRecommendations() = viewModelScope.launch(Dispatchers.IO){
        val homeRecommendations = sharedPreferences.getString("recommendations_home", "")
        val customHomeRecommendations = sharedPreferences.getString("custom_home_recommendation_url", "")
        val emptyCustomRecommendations = customHomeRecommendations.isNullOrBlank() && homeRecommendations == "custom"

        if (!homeRecommendations.isNullOrBlank() && !emptyCustomRecommendations){
            kotlin.runCatching {
                uiState.update {it.copy(processing = true)}
                repository.getHomeRecommendations()
                uiState.update {it.copy(processing = false)}
            }.onFailure { t ->
                uiState.update {it.copy(
                    processing = false,
                    errorMessage =  t.message.toString(),
                )}
            }
        }else{
            deleteAll()
        }
    }

    fun cancelParsingQueries(){
        parsingQueries?.cancel(CancellationException())
        parsingQueriesJobList.forEach { it.cancel(CancellationException()) }
        uiState.update {it.copy(processing = false)}
    }

    private suspend fun parseQueriesImpl(inputQueries: List<String>, onResult: (list: List<ResultItem?>) -> Unit) {
        if (inputQueries.size > 1){
            repository.itemCount.value = inputQueries.size
        }
        val resetResults = inputQueries.size == 1
        uiState.update {it.copy(processing = true, errorMessage = null)}
        val res = mutableListOf<ResultItem?>()

        val requestSemaphore = Semaphore(10)
        inputQueries.forEach { inputQuery ->
            parsingQueriesJobList.add(viewModelScope.launch(Dispatchers.IO){
                requestSemaphore.withPermit {
                    try {
                        res.addAll(repository.getResultsFromSource(inputQuery, resetResults))
                    } catch (e: Exception) {
                        if (updateResultDataJob?.isCancelled == false || e is YoutubeDLException){
                            uiState.update {it.copy(
                                processing = false,
                                errorMessage = e.message.toString(),
                            )}
                            Log.e(tag, e.toString())
                        }

                    }
                }
            })
        }
        parsingQueriesJobList.joinAll()
        if (!isForegrounded() && inputQueries.size > 1){
            notificationUtil.showQueriesFinished()
        }
        uiState.update {it.copy(processing = false)}
        onResult(res)
    }

    suspend fun parseQueries(inputQueries: List<String>, onResult: (list: List<ResultItem?>) -> Unit) {
        if (parsingQueries == null || parsingQueries?.isCancelled == true || parsingQueries?.isCompleted == true) {
            parsingQueries = viewModelScope.launch(Dispatchers.IO) {
                parseQueriesImpl(inputQueries) {
                    onResult(it)
                }
            }
        }else{
            onResult(listOf())
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



    fun insert(items: ArrayList<ResultItem?>) = viewModelScope.launch(Dispatchers.IO){
        items.forEach {
            repository.insert(it!!)
        }
    }

    suspend fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
        resetPlaylistFilter()
    }

    fun update(item: ResultItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item)
    }

    fun getItemByURL(url: String) : ResultItem? {
        return repository.getItemByURL(url)
    }

    fun getAllByURL(url: String) : List<ResultItem> {
        return repository.getAllByURL(url)
    }

    fun getByID(id: Long) : ResultItem? {
        return repository.getItemByID(id)
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


    suspend fun updateItemData(res: ResultItem){
        if (updateResultDataJob == null || updateResultDataJob?.isCancelled == true || updateResultDataJob?.isCompleted == true){
            updateResultDataJob = viewModelScope.launch(Dispatchers.IO) {
                updatingData.emit(true)
                updatingData.value = true
                parseQueriesImpl(listOf(res.url)){ result ->
                    viewModelScope.launch(Dispatchers.IO){
                        updatingData.emit(false)
                        updatingData.value = false
                        updateResultData.emit(result)
                    }
                }
            }

            //updateResultDataJob?.start()
            updateResultDataJob?.invokeOnCompletion {
                if (it != null){
                    viewModelScope.launch(Dispatchers.IO) {
                        updatingData.emit(false)
                        updatingData.value = false
                        updateResultData.emit(null)
                        updateResultData.value = null
                    }
                }
            }
        }
    }

    suspend fun cancelUpdateItemData() = viewModelScope.launch(Dispatchers.IO) {
        updateResultDataJob?.cancel(CancellationException())
        updatingData.emit(false)
        updatingData.value = false
        updateResultData.emit(null)
        updateResultData.value = null
    }

    suspend fun cancelUpdateFormatsItemData() = viewModelScope.launch(Dispatchers.IO) {
        updatingFormats.emit(false)
        updatingFormats.value = false
        updateFormatsResultData.emit(null)
        updateFormatsResultData.value = null
        updateFormatsResultDataJob?.cancel(CancellationException())
    }

    suspend fun updateFormatItemData(result: ResultItem){
        if (updateFormatsResultDataJob == null || updateFormatsResultDataJob?.isCancelled == true || updateFormatsResultDataJob?.isCompleted == true) {
            updateFormatsResultDataJob = viewModelScope.launch(Dispatchers.IO) {
                updatingFormats.emit(true)
                try {
                    val formats = getFormats(result.url)
                    updatingFormats.emit(false)
                    formats.apply {
                        if (formats.isNotEmpty() && updateFormatsResultDataJob?.isCancelled == false) {
                            getItemByURL(result.url)?.apply {
                                this.formats = formats.toMutableList()
                                update(this)
                            }
                            updateFormatsResultData.emit(formats.toMutableList())
                        }
                    }
                }catch(e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        uiState.update {
                            it.copy(
                                processing = false,
                                errorMessage = e.message.toString(),
                            )
                        }
                        Log.e(tag, e.toString())
                    }
                    updatingFormats.emit(false)
                }
            }
            updateFormatsResultDataJob?.start()
            updateFormatsResultDataJob?.invokeOnCompletion {
                if (it != null){
                    viewModelScope.launch(Dispatchers.IO) {
                        updatingFormats.emit(false)
                        updatingFormats.value = false
                        updateFormatsResultData.emit(null)
                        updateFormatsResultData.value = null
                    }
                }
            }
        }
    }

    data class MultipleFormatProgress(
        val url: String,
        val formats: List<Format>,
        val unavailable : Boolean = false,
        val unavailableMessage: String = ""
    )


    fun getFormats(url: String, source: String? = null) : List<Format> {
        return repository.getFormats(url, source)
    }

    suspend fun getFormatsMultiple(urls: List<String>, source: String? = null, progress: (progress: MultipleFormatProgress) -> Unit) : MutableList<MutableList<Format>> {
        val res = repository.getFormatsMultiple(urls, source) {
            progress(it)
        }
        return res
    }

    fun getResultsBetweenTwoItems(item1: Long, item2: Long) : List<ResultItem>{
        return dao.getResultsBetweenTwoItems(item1, item2)
    }

    fun getSearchSuggestions(searchQuery: String) : ArrayList<String> {
        return repository.getSearchSuggestions(searchQuery)
    }

    fun getStreamingUrlAndChapters(url: String) : Pair<List<String>, List<ChapterItem>?> {
        return repository.getStreamingUrlAndChapters(url)
    }

    fun reverseResults(resultItems: List<Long>): List<Long> {
        val latestResult = resultItems.max()
        val newIdsMap = mutableListOf<Pair<Long, Long>>()

        var i = 0
        resultItems.reversed().forEach {
            newIdsMap.add(Pair(it, latestResult + (++i)))
        }

        viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            newIdsMap.forEach {
                repository.updateID(it.first, it.second)
            }
        }

        return newIdsMap.map { it.second }
    }
}