package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ObserveSourcesDao {
    @Query("SELECT * FROM sources ORDER BY id DESC")
    fun getAllSources() : List<ObserveSourcesItem>

    @Query("SELECT * FROM sources WHERE url = :url LIMIT 1")
    fun getByURL(url: String) : ObserveSourcesItem

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    fun getByID(id: Long) : ObserveSourcesItem

    @Query("SELECT * FROM sources ORDER BY id DESC")
    fun getAllSourcesFlow() : Flow<List<ObserveSourcesItem>>

    @Query("SELECT EXISTS(SELECT * FROM sources WHERE url=:url LIMIT 1)")
    fun checkIfExistsWithSameURL(url: String) : Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ObserveSourcesItem) : Long

    @Query("DELETE FROM sources")
    suspend fun deleteAll()

    @Query("DELETE FROM sources WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ObserveSourcesItem)
}