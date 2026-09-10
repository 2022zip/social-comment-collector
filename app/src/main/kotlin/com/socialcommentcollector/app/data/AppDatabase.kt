package com.socialcommentcollector.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CollectionTaskEntity::class, CommentEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collectionTaskDao(): CollectionTaskDao
    abstract fun commentDao(): CommentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE collection_tasks ADD COLUMN author TEXT")
                database.execSQL("ALTER TABLE collection_tasks ADD COLUMN body TEXT")
                database.execSQL("ALTER TABLE collection_tasks ADD COLUMN publishedAt INTEGER")
                database.execSQL("ALTER TABLE collection_tasks ADD COLUMN collectedAt INTEGER")
                database.execSQL("ALTER TABLE comments ADD COLUMN dedupKey TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """
                    UPDATE comments
                    SET dedupKey = CASE
                        WHEN platformCommentId IS NOT NULL AND TRIM(platformCommentId) <> ''
                            THEN 'platform:' || TRIM(platformCommentId)
                        ELSE 'legacy:' || id
                    END
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_comments_taskId_dedupKey " +
                        "ON comments(taskId, dedupKey)",
                )
            }
        }
    }
}
