package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface CommandTemplateDao {

    @Query("SELECT * FROM commandTemplates")
    fun getAllTemplates() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates WHERE id=:id LIMIT 1")
    fun getTemplate(id: Long) : CommandTemplate

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CommandTemplate)

    @Query("DELETE FROM commandTemplates")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(item: CommandTemplate)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: CommandTemplate)
}