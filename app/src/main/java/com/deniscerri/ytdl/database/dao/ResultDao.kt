package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deniscerri.ytdl.database.models.ResultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Query("SELECT * FROM results order by id")
    fun getResults() : Flow<List<ResultItem>>

    @Query("SELECT * FROM results WHERE playlistTitle LIKE '%' || :playlistName || '%'  order by id")
    fun getResultsWithPlaylistName(playlistName: String) : List<ResultItem>

    @Query("SELECT COUNT(id) FROM results")
    fun getCount() : Flow<Int>

    @Query("SELECT COUNT(id) FROM results")
    fun getCountInt() :Int

    @Query("SELECT * FROM results LIMIT 1")
    fun getFirstResult() : ResultItem

    @Query("SELECT * FROM results ORDER BY id DESC LIMIT 1")
    fun getLastResult(): ResultItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ResultItem) : Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultiple(items: List<ResultItem?>) : List<Long>

    @Transaction
    suspend fun insertMultipleNoDuplicates(items: List<ResultItem>) : List<Long> {
        return insertMultiple(items.filter { getResultByURL(it.url) == null })
    }

    @Query("SELECT * FROM results WHERE id IN (:ids)")
    fun getAllByIDs(ids: List<Long>) : List<ResultItem>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ResultItem)

    @Query("DELETE FROM results")
    suspend fun deleteAll()

    @Query("DELETE FROM results WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM results WHERE url=:url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM results WHERE url=:url LIMIT 1")
    fun getResultByURL(url: String) : ResultItem?

    @Query("SELECT * FROM results WHERE url=:url")
    fun getAllByURL(url: String) : List<ResultItem>

    @Query("SELECT * FROM results where id=:id LIMIT 1")
    fun getResultByID(id: Long): ResultItem?

    @Query("SELECT * from results WHERE id > :item1 AND id < :item2 ORDER BY id")
    fun getResultsBetweenTwoItems(item1: Long, item2: Long) : List<ResultItem>

}