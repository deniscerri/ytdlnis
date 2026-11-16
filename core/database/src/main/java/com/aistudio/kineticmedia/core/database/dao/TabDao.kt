package com.aistudio.kineticmedia.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.kineticmedia.core.database.model.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity): Long

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Delete
    suspend fun deleteTab(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteTabById(tabId: Long)

    @Query("DELETE FROM tabs")
    suspend fun deleteAllTabs()
}
