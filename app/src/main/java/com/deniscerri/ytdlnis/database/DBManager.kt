package com.deniscerri.ytdlnis.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.deniscerri.ytdlnis.database.dao.*
import com.deniscerri.ytdlnis.database.models.*

@TypeConverters(Converters::class)
@Database(
    entities = [ResultItem::class, HistoryItem::class, DownloadItem::class, CommandTemplate::class, SearchHistoryItem::class, TemplateShortcut::class],
    version = 1,
    autoMigrations = []
)
abstract class DBManager : RoomDatabase(){
    abstract val resultDao : ResultDao
    abstract val historyDao : HistoryDao
    abstract val downloadDao : DownloadDao
    abstract val commandTemplateDao : CommandTemplateDao
    abstract val searchHistoryDao: SearchHistoryDao

    companion object {
        //prevents multiple instances of db getting created at the same time
        @Volatile
        private var instance : DBManager? = null
        //if its not null return it, otherwise create db
        fun getInstance(context: Context) : DBManager {
            return instance ?: synchronized(this){
                val dbInstance = Room.databaseBuilder(
                    context.applicationContext,
                    DBManager::class.java,
                    "YTDLnisDatabase"
                )
                        .build()
                instance = dbInstance
                dbInstance
            }
        }
    }

}