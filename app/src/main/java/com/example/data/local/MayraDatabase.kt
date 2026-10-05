package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MemoryItemEntity

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        MemoryItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MayraDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun memoryDao(): MemoryDao

    companion object {

        @Volatile
        private var INSTANCE: MayraDatabase? = null

        /**
         * Database migration from version 3 to 4.
         *
         * Adds conversation pin/archive support
         * and creates the memories table.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    "ALTER TABLE conversations " +
                        "ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE conversations " +
                        "ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memories (
                        id TEXT NOT NULL PRIMARY KEY,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        enabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Database migration from version 4 to 5.
         *
         * Adds generated-image storage to chat messages.
         * Existing messages remain unchanged because both
         * new columns are nullable.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    "ALTER TABLE chat_messages " +
                        "ADD COLUMN generatedImageBase64 TEXT"
                )

                db.execSQL(
                    "ALTER TABLE chat_messages " +
                        "ADD COLUMN generatedImageMimeType TEXT"
                )
            }
        }

        fun getDatabase(context: Context): MayraDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MayraDatabase::class.java,
                    "mayra_ai_database"
                )
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )
                    .fallbackToDestructiveMigration(
                        dropAllTables = false
                    )
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
