package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ScanRecordEntity::class], version = 2, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_history ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN dataSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN errorCorrection TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN processingTimeMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "scanvault_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
