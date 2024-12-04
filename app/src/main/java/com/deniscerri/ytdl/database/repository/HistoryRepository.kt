package com.deniscerri.ytdl.database.repository

import androidx.compose.runtime.internal.isLiveLiteralsEnabled
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.filter
import com.deniscerri.ytdl.database.DBManager.SORTING
import com.deniscerri.ytdl.database.dao.HistoryDao
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import java.io.File

class HistoryRepository(private val historyDao: HistoryDao) {
    val items : Flow<List<HistoryItem>> = historyDao.getAllHistory()
    val websites : Flow<List<String>> = historyDao.getWebsites()
    val count : Flow<Int> = historyDao.getCount()

    enum class HistorySortType {
        DATE, TITLE, AUTHOR, FILESIZE
    }

    fun getItem(id: Long) : HistoryItem {
        return historyDao.getHistoryItem(id)
    }

    fun getAll() : List<HistoryItem> {
        return historyDao.getAllHistoryList()
    }

    fun getAllByURL(url: String) : List<HistoryItem> {
        return historyDao.getAllHistoryByURL(url)
    }

    fun getAllByIDs(ids: List<Long>) : List<HistoryItem> {
        return historyDao.getAllHistoryByIDs(ids)
    }

    data class HistoryIDsAndPaths(
        val id: Long,
        val downloadPath: List<String>
    )

    fun getFilteredIDs (query : String, type : String, site : String, sortType: HistorySortType, sort: SORTING, statusFilter: HistoryViewModel.HistoryStatus) : List<Long> {
        var filtered = when(sortType){
            HistorySortType.DATE ->  historyDao.getHistoryIDsSortedByID(query, type, site, sort.toString())
            HistorySortType.TITLE ->  historyDao.getHistoryIDsSortedByTitle(query, type, site, sort.toString())
            HistorySortType.AUTHOR ->  historyDao.getHistoryIDsSortedByAuthor(query, type, site, sort.toString())
            HistorySortType.FILESIZE -> historyDao.getHistoryIDsSortedByFilesize(query, type, site, sort.toString())
        }

        when(statusFilter) {
            HistoryViewModel.HistoryStatus.DELETED -> {
                filtered = filtered.filter { it.downloadPath.any { it2 -> !FileUtil.exists(it2) } }
            }
            HistoryViewModel.HistoryStatus.NOT_DELETED -> {
                filtered = filtered.filter { it.downloadPath.any { it2 -> FileUtil.exists(it2) } }
            }
            else -> {}
        }
        return filtered.map { it.id }
    }

    fun getPaginatedSource(query : String, type : String, site : String, sortType: HistorySortType, sort: SORTING) : PagingSource<Int, HistoryItem> {
        val source = when(sortType){
            HistorySortType.DATE ->  historyDao.getHistorySortedByIDPaginated(query, type, site, sort.toString())
            HistorySortType.TITLE ->  historyDao.getHistorySortedByTitlePaginated(query, type, site, sort.toString())
            HistorySortType.AUTHOR ->  historyDao.getHistorySortedByAuthorPaginated(query, type, site, sort.toString())
            HistorySortType.FILESIZE ->  {
                historyDao.getHistorySortedByFilesizePaginated(query, type, site, sort.toString())
            }
        }

        return source
    }


    suspend fun insert(item: HistoryItem){
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem, deleteFile: Boolean){
        historyDao.delete(item.id)
        if (deleteFile){
            item.downloadPath.forEach {
                FileUtil.deleteFile(it)
            }
        }
    }

    suspend fun deleteAll(deleteFile: Boolean = false){
        if (deleteFile){
            historyDao.getAllHistoryList().forEach { item ->
                item.downloadPath.forEach {
                    FileUtil.deleteFile(it)
                }
            }
        }
        historyDao.deleteAll()
    }

    suspend fun deleteAllWithIDs(ids: List<Long>, deleteFile: Boolean = false){
        if (deleteFile){
            historyDao.getAllHistoryByIDs(ids).forEach { item ->
                item.downloadPath.forEach {
                    FileUtil.deleteFile(it)
                }
            }
        }
        historyDao.deleteAllByIDs(ids)
    }

    suspend fun deleteAllWithIDsCheckFiles(ids: List<Long>){
        val idsToDelete = mutableListOf<Long>()
        historyDao.getAllHistoryByIDs(ids).forEach { item ->
            val filesNotPresent = item.downloadPath.all { !File(it).exists() && it.isNotBlank()}
            if (filesNotPresent) {
                idsToDelete.add(item.id)
            }
        }
        if (idsToDelete.isNotEmpty()) {
            historyDao.deleteAllByIDs(idsToDelete)
        }
    }

    data class HistoryItemDownloadPaths(
        val downloadPath: List<String>
    )

    fun getDownloadPathsFromIDs(ids: List<Long>) : List<List<String>> {
        val res = historyDao.getDownloadPathsFromIDs(ids)
        return res.map { it.downloadPath }
    }

    suspend fun deleteDuplicates(){
        historyDao.deleteDuplicates()
    }

    suspend fun update(item: HistoryItem){
        historyDao.update(item)
    }

    suspend fun clearDeletedHistory(){
        items.collectLatest {
            it.forEach { item ->
                if (item.downloadPath.all { path -> !FileUtil.exists(path) }){
                    historyDao.delete(item.id)
                }
            }
        }
    }

}