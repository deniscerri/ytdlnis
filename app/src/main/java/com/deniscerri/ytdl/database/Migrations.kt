package com.deniscerri.ytdl.database

import android.annotation.SuppressLint
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deniscerri.ytdl.database.models.Format
import com.google.gson.Gson


object Migrations {

    @SuppressLint("Range")
    val migrationList = arrayOf(
        //Moving from one file path to multiple file paths of a history item
        Migration(13, 14){database ->
            val cursor = database.query("SELECT * FROM history")
            while(cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndex("id"))
                val path = cursor.getString(cursor.getColumnIndex("downloadPath"))
                val newPath = "[\"${path.replace("\"", "\\\"").replace("'", "''")}\"]"
                database.execSQL("UPDATE history SET downloadPath = '${newPath}' WHERE id = $id")

            }

            database.execSQL("CREATE TABLE IF NOT EXISTS `observeSources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `downloadItemTemplate` TEXT NOT NULL, `status` TEXT NOT NULL, `everyNr` INTEGER NOT NULL, `everyCategory` TEXT NOT NULL, `everyWeekDay` TEXT NOT NULL, `everyMonthDay` INTEGER NOT NULL, `everyTime` INTEGER NOT NULL, `startsTime` INTEGER NOT NULL, `startsMonth` TEXT NOT NULL, `endsDate` INTEGER NOT NULL DEFAULT 0, `endsAfterCount` INTEGER NOT NULL DEFAULT 0, `runCount` INTEGER NOT NULL DEFAULT 0, `retryMissingDownloads` INTEGER NOT NULL, `alreadyProcessedLinks` TEXT NOT NULL)")
        },

//        Migration(17, 18 ){ database ->
//            database.execSQL("ALTER TABLE `sources` ADD COLUMN `syncWithSource` INTEGER NOT NULL DEFAULT 0")
//        }

        //add filesizes to history
        Migration(20, 21) { database ->
            val cursor = database.query("SELECT * FROM history")
            while(cursor.moveToNext()) {
                kotlin.runCatching {
                    val id = cursor.getLong(cursor.getColumnIndex("id"))
                    val format = cursor.getString(cursor.getColumnIndex("format"))
                    val parsed = Gson().fromJson(format, Format::class.java)
                    database.execSQL("UPDATE history SET filesize = ${parsed.filesize} WHERE id = $id")
                }
            }
        }
    )

    @DeleteTable.Entries(
        DeleteTable(
            tableName = "observeSources"
        )
    )
    class resetObserveSources : AutoMigrationSpec {
        @Override
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Invoked once auto migration is done
        }
    }


}