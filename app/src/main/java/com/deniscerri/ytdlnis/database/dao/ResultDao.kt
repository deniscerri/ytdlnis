package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface ResultDao {

    @Query("SELECT * FROM results")
    suspend fun getResults() : List<ResultItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToResults(results: ArrayList<ResultItem>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateResult(item: ResultItem)

    @Query("DELETE FROM results")
    suspend fun clearResults()
}