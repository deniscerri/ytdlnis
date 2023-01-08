package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface ResultDao {
    @Query("SELECT * FROM results")
    fun getResults() : LiveData<List<ResultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ResultItem)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ResultItem)

    @Query("DELETE FROM results")
    suspend fun deleteAll()
}