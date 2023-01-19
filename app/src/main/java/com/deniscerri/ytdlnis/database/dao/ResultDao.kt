package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.ResultItem

@Dao
interface ResultDao {
    @Query("SELECT * FROM results")
    fun getResults() : LiveData<List<ResultItem>>

    @Query("SELECT * FROM results LIMIT 1")
    fun getFirstResult() : ResultItem

    @Query("SELECT * FROM results ORDER BY id DESC LIMIT 1")
    fun getLastResult(): ResultItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ResultItem) : Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ResultItem)

    @Query("DELETE FROM results")
    suspend fun deleteAll()

}