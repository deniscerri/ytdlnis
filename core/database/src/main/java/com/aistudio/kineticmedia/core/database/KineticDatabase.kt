package com.aistudio.kineticmedia.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aistudio.kineticmedia.core.database.dao.BookmarkDao
import com.aistudio.kineticmedia.core.database.dao.DownloadDao
import com.aistudio.kineticmedia.core.database.dao.HistoryDao
import com.aistudio.kineticmedia.core.database.dao.TabDao
import com.aistudio.kineticmedia.core.database.model.Bookmark
import com.aistudio.kineticmedia.core.database.model.DownloadItem
import com.aistudio.kineticmedia.core.database.model.HistoryItem
import com.aistudio.kineticmedia.core.database.model.TabEntity

@Database(
    entities = [TabEntity::class, HistoryItem::class, Bookmark::class, DownloadItem::class],
    version = 1,
    exportSchema = false
)
abstract class KineticDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: KineticDatabase? = null

        fun getInstance(context: Context): KineticDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KineticDatabase::class.java,
                    "kinetic_media.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
