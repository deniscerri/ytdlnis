package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.TemplateShortcut
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandTemplateDao {
    @Query("SELECT * FROM commandTemplates WHERE (title LIKE '%'||:query||'%' OR content LIKE '%'||:query||'%') ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    fun getCommandsSortedByID(query : String, sort : String) : List<CommandTemplate>


    @Query("SELECT * FROM commandTemplates WHERE (title LIKE '%'||:query||'%' OR content LIKE '%'||:query||'%') ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN title COLLATE NOCASE END ASC," +
            "CASE WHEN :sort = 'DESC' THEN title COLLATE NOCASE END DESC," +
            "CASE WHEN :sort = '' THEN title COLLATE NOCASE END DESC ")
    fun getCommandsSortedByTitle(query : String, sort : String) : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates WHERE (title LIKE '%'||:query||'%' OR content LIKE '%'||:query||'%') ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN length(content) END ASC," +
            "CASE WHEN :sort = 'DESC' THEN length(content) END DESC," +
            "CASE WHEN :sort = '' THEN length(content) END DESC ")
    fun getCommandsSortedByContentLength(query : String,  sort : String) : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplates() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplatesFlow() : Flow<List<CommandTemplate>>
    @Query("SELECT content FROM commandTemplates WHERE useAsExtraCommand is 1")
    fun getAllTemplatesAsExtraCommands() : List<String>

    @Query("SELECT * FROM commandTemplates WHERE useAsExtraCommandDataFetching is 1")
    fun getAllTemplatesAsDataFetchingExtraCommands() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates WHERE useAsExtraCommand is 1 AND useAsExtraCommandAudio = 1")
    fun getAllTemplatesAsExtraCommandsForAudio() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates WHERE useAsExtraCommand is 1 AND useAsExtraCommandVideo = 1")
    fun getAllTemplatesAsExtraCommandsForVideo() : List<CommandTemplate>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcutsFlow() : Flow<List<TemplateShortcut>>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcuts() : List<TemplateShortcut>

    @Query("SELECT COUNT(id) FROM commandTemplates")
    fun getTotalNumber() : Int

    @Query("SELECT COUNT(id) FROM templateShortcuts")
    fun getTotalShortcutNumber() : Int

    @Query("SELECT * FROM commandTemplates WHERE id=:id LIMIT 1")
    fun getTemplate(id: Long) : CommandTemplate?

    @Query("SELECT * FROM commandTemplates WHERE content=:content LIMIT 1")
    fun getTemplateByContent(content: String) : CommandTemplate?

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC LIMIT 1")
    fun getFirst() : CommandTemplate?

    @Query("SELECT * FROM commandTemplates WHERE preferredCommandTemplate is 1")
    fun getPreferredCommandTemplates() : List<CommandTemplate>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CommandTemplate)

    @Query("DELETE FROM commandTemplates")
    suspend fun deleteAll()

    @Query("DELETE FROM commandTemplates WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Insert(TemplateShortcut::class, OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: TemplateShortcut)

    @Query("SELECT COUNT(id) FROM templateShortcuts WHERE content=:content")
    suspend fun checkExistingShortcut(content: String) : Int

    @Query("DELETE FROM templateShortcuts WHERE id=:itemId")
    suspend fun deleteShortcut(itemId: Long)

    @Query("DELETE FROM templateShortcuts")
    suspend fun deleteAllShortcuts()

    @Update
    suspend fun update(item: CommandTemplate)
}