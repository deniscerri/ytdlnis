package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface CommandTemplateDao {
    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplates() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplatesLiveData() : LiveData<List<CommandTemplate>>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcutsLiveData() : LiveData<List<TemplateShortcut>>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcuts() : List<TemplateShortcut>

    @Query("SELECT COUNT(id) FROM commandTemplates")
    fun getTotalNumber() : Int

    @Query("SELECT * FROM commandTemplates WHERE id=:id LIMIT 1")
    fun getTemplate(id: Long) : CommandTemplate

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC LIMIT 1")
    fun getFirst() : CommandTemplate

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CommandTemplate)

    @Query("DELETE FROM commandTemplates")
    suspend fun deleteAll()

    @Query("DELETE FROM commandTemplates WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Query("INSERT INTO templateShortcuts(content) VALUES(:content)")
    suspend fun insertShortcut(content: String)

    @Query("DELETE FROM templateShortcuts WHERE id=:itemId")
    suspend fun deleteShortcut(itemId: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: CommandTemplate)
}