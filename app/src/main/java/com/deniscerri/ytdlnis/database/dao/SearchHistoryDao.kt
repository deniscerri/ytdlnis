package com.deniscerri.ytdlnis.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.deniscerri.ytdlnis.database.models.SearchHistoryItem

@Dao
interface SearchHistoryDao {
    @Query("SELECT * from searchHistory ORDER BY id DESC LIMIT 10")
    fun getAll() : List<SearchHistoryItem>

    @Query("SELECT * from searchHistory WHERE query COLLATE NOCASE ='%'||:keyword||'%'")
    fun getAllByKeyword(keyword: String) : List<SearchHistoryItem>

    @Insert
    suspend fun insert(new: SearchHistoryItem)

    @Query("DELETE FROM searchHistory")
    suspend fun deleteAll()
}