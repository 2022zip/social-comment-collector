package com.socialcommentcollector.app.data

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val databaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To2PreservesRowsAndBackfillsStableDedupKeys() {
        helper.createDatabase(databaseName, 1).apply {
            insert("collection_tasks", 0, taskValues())
            insert("comments", 0, commentValues("with-platform", "remote-1"))
            insert("comments", 0, commentValues("legacy-comment", null))
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT title, author, body, publishedAt, collectedAt FROM collection_tasks WHERE id = 'task-1'").use {
            it.moveToFirst()
            assertEquals("Existing title", it.getString(0))
            assertEquals(true, it.isNull(1))
            assertEquals(true, it.isNull(2))
            assertEquals(true, it.isNull(3))
            assertEquals(true, it.isNull(4))
        }
        migrated.query("SELECT id, dedupKey, content FROM comments ORDER BY id").use {
            it.moveToFirst()
            assertEquals("legacy-comment", it.getString(0))
            assertEquals("legacy:legacy-comment", it.getString(1))
            assertEquals("Old content", it.getString(2))
            it.moveToNext()
            assertEquals("with-platform", it.getString(0))
            assertEquals("platform:remote-1", it.getString(1))
            assertEquals("Old content", it.getString(2))
        }
        migrated.close()
    }

    private fun taskValues() = ContentValues().apply {
        put("id", "task-1")
        put("originalUrl", "https://www.xiaohongshu.com/explore/note-1")
        put("platform", "XIAOHONGSHU")
        put("title", "Existing title")
        put("status", "INCOMPLETE")
        putNull("displayedCommentCount")
        put("actualSavedCommentCount", 2)
        put("failureReason", "old interruption")
        put("createdAt", 1L)
        put("updatedAt", 2L)
    }

    private fun commentValues(id: String, platformId: String?) = ContentValues().apply {
        put("id", id)
        put("taskId", "task-1")
        if (platformId == null) putNull("platformCommentId") else put("platformCommentId", platformId)
        putNull("parentCommentId")
        put("author", "Old author")
        put("content", "Old content")
        putNull("publishedAt")
        put("collectedAt", 3L)
        put("sortOrder", if (id == "with-platform") 1L else 2L)
    }
}
