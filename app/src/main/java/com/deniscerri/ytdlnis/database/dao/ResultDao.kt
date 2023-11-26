package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.ResultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Query("SELECT * FROM results")
    fun getResults() : Flow<List<ResultItem>>

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

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ResultItem)

    @Query("DELETE FROM results")
    suspend fun deleteAll()

    @Query("DELETE FROM results WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM results WHERE url=:url LIMIT 1")
    fun getResultByURL(url: String) : ResultItem?

}