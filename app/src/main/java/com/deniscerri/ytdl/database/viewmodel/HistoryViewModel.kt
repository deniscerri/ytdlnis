package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository.HistorySortType
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : HistoryRepository
    val sortOrder = MutableStateFlow(SORTING.DESC)
    val sortType = MutableStateFlow(HistorySortType.DATE)
    val websiteFilter = MutableStateFlow("")
    val statusFilter = MutableStateFlow(HistoryStatus.ALL)
    private val queryFilter = MutableStateFlow("")
    private val typeFilter = MutableStateFlow("")

    var filterCount : StateFlow<Int> = combine(
        websiteFilter,
        statusFilter
    ) { website, status ->
        val activeFilters = listOf(
            website.isNotEmpty(),
            status != HistoryStatus.ALL
        )
        activeFilters.count { it }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    enum class HistoryStatus {
        UNSET, DELETED, NOT_DELETED, ALL
    }

    private var _items = MediatorLiveData<List<HistoryItem>>()

    var paginatedItems : Flow<PagingData<HistoryItem>>
    var websites : Flow<List<String>>
    var totalCount = MutableStateFlow(0)

    data class HistoryFilters(
        var type: String = "",
        var sortType: HistorySortType = HistorySortType.DATE,
        var sortOrder: SORTING = SORTING.DESC,
        var query: String = "",
        var status: HistoryStatus = HistoryStatus.ALL,
        var website: String = ""
    )

    init {
        val dao = DBManager.getInstance(application).historyDao
        repository = HistoryRepository(dao)
        websites = repository.websites

        val filters = listOf(dao.getAllHistory(), sortOrder, sortType, websiteFilter, statusFilter, queryFilter, typeFilter)
        paginatedItems = combine(filters) { f ->
            val sortOrder = f[1] as SORTING
            val sortType = f[2] as HistorySortType
            val website = f[3] as String
            val status = f[4] as HistoryStatus
            val query = f[5] as String
            val type = f[6] as String

            var pager = Pager(
                config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
                pagingSourceFactory = {
                    repository.getPaginatedSource(query, type, website, sortType, sortOrder)
                }
            ).flow

            when(status) {
                HistoryStatus.DELETED -> {
                    pager = pager.map {
                        it.filter { it2 ->
                            it2.downloadPath.any { it3 ->
                                !FileUtil.exists(it3)
                            }
                        }
                    }
                }
                HistoryStatus.NOT_DELETED -> {
                    pager = pager.map {
                        it.filter { it2 ->
                            it2.downloadPath.any { it3 ->
                                FileUtil.exists(it3)
                            }
                        }
                    }
                }
                else -> {}
            }

            withContext(Dispatchers.IO) {
                totalCount.value = repository.getFilteredIDs(query, type, website, sortType, sortOrder, status).count()
            }

            pager
        }.flatMapLatest { it }
    }

    fun setSorting(sort: HistorySortType){
        if (sortType.value != sort){
            sortOrder.value = SORTING.DESC
        }else{
            sortOrder.value = if (sortOrder.value == SORTING.DESC) {
                SORTING.ASC
            } else SORTING.DESC
        }
        sortType.value = sort
    }

    fun setWebsiteFilter(filter : String){
        websiteFilter.value = filter
    }

    fun setQueryFilter(filter: String){
        queryFilter.value = filter
    }

    fun setTypeFilter(filter: String){
        typeFilter.value = filter
    }

    fun setStatusFilter(status: HistoryStatus) {
        statusFilter.value = status
    }

    fun getIDsBetweenTwoItems(firstID: Long, secondID: Long): List<Long> {
        val ids = repository.getFilteredIDs(queryFilter.value, typeFilter.value, websiteFilter.value, sortType.value, sortOrder.value, statusFilter.value)
        val firstIndex = ids.indexOf(firstID)
        val secondIndex = ids.indexOf(secondID)
        return if (firstIndex > secondIndex) {
            ids.filterIndexed {index, _ -> index < firstIndex && index > secondIndex }
        }else {
            ids.filterIndexed {index, _ -> index > firstIndex && index < secondIndex }
        }
    }

    fun getItemIDsNotPresentIn(not: List<Long>) : List<Long> {
        val ids = repository.getFilteredIDs(queryFilter.value, typeFilter.value, websiteFilter.value, sortType.value, sortOrder.value, statusFilter.value)
        return ids.filter { !not.contains(it) }
    }

    fun getAll() : List<HistoryItem> {
        return repository.getAll()
    }

    fun getByID(id: Long) : HistoryItem {
        return repository.getItem(id)
    }

    fun insert(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun delete(item: HistoryItem, deleteFile: Boolean) = viewModelScope.launch(Dispatchers.IO){
        repository.delete(item, deleteFile)
    }

    fun deleteAllWithIDs(ids: List<Long>, deleteFile: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAllWithIDs(ids, deleteFile)
    }

    fun deleteAllWithIDsCheckFiles(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAllWithIDsCheckFiles(ids)
    }

    fun getDownloadPathsFromIDs(ids: List<Long>) : List<List<String>> {
        return repository.getDownloadPathsFromIDs(ids)
    }

    fun deleteAll(deleteFile: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll(deleteFile)
    }

    fun deleteDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDuplicates()
    }

    fun update(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item)
    }

    fun clearDeleted() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearDeletedHistory()
    }

}