package com.deniscerri.ytdlnis.database

import android.content.Context
import androidx.room.*
import com.deniscerri.ytdlnis.database.dao.*
import com.deniscerri.ytdlnis.database.models.*

@TypeConverters(Converters::class)
@Database(
    entities = [ResultItem::class, HistoryItem::class, DownloadItem::class, CommandTemplate::class, SearchHistoryItem::class, TemplateShortcut::class, CookieItem::class],
    version = 6,
    autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5),
        AutoMigration (from = 5, to = 6)
    ]
)
abstract class DBManager : RoomDatabase(){
    abstract val resultDao : ResultDao
    abstract val historyDao : HistoryDao
    abstract val downloadDao : DownloadDao
    abstract val commandTemplateDao : CommandTemplateDao
    abstract val searchHistoryDao: SearchHistoryDao
    abstract val cookieDao: CookieDao

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