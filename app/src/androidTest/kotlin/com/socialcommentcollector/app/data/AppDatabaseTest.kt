package com.socialcommentcollector.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun initializeAndRoundTripUnknownCountsAndEnums() = runBlocking {
        assertTrue(database.openHelper.writableDatabase.isOpen)
        val task = CollectionTaskEntity(
            id = "task-1", originalUrl = "https://example.com/content",
            platform = Platform.JIKE, title = "Fixture", status = CollectionStatus.INCOMPLETE,
            displayedCommentCount = null, actualSavedCommentCount = 3,
            failureReason = null, createdAt = 10, updatedAt = 20,
        )
        val repository = CollectionRepository(
            database.collectionTaskDao(),
            database.commentDao(),
            RoomTransactionRunner(database),
        )
        repository.saveTask(task)
        assertEquals(task, repository.getTask("task-1"))
        assertNull(repository.getTask("missing"))
        assertNull(repository.observeTasks().first().single().displayedCommentCount)
    }

    @Test fun commentsRoundTripSortCountAndStayScopedToTask() = runBlocking {
        val tasks = database.collectionTaskDao()
        tasks.insert(CollectionTaskEntity(id = "a", originalUrl = "a", createdAt = 1, updatedAt = 1))
        tasks.insert(CollectionTaskEntity(id = "b", originalUrl = "b", createdAt = 2, updatedAt = 2))
        val first = CommentEntity(
            id = "c1", taskId = "a", platformCommentId = "remote-1", dedupKey = "platform:remote-1", author = "Author",
            content = "First", publishedAt = 10, collectedAt = 20, sortOrder = 1,
        )
        val second = CommentEntity(
            id = "c2", taskId = "a", dedupKey = "fallback:c2", parentCommentId = "c1",
            content = "Reply", collectedAt = 21, sortOrder = 2,
        )
        val other = CommentEntity(id = "c3", taskId = "b", dedupKey = "fallback:c3", content = "Other", collectedAt = 22, sortOrder = 0)
        database.commentDao().insert(first)
        database.commentDao().insertAll(listOf(other, second))
        assertEquals(listOf(first, second), database.commentDao().getByTaskId("a"))
        assertEquals(2, database.commentDao().countByTaskId("a"))
        assertEquals(1, database.commentDao().countByTaskId("b"))
        assertEquals(0, database.commentDao().countByTaskId("missing"))
        tasks.deleteById("a")
        assertEquals(0, database.commentDao().countByTaskId("a"))
        assertEquals(listOf(other), database.commentDao().getByTaskId("b"))
    }

    @Test fun incrementalBatchesDeduplicateAndRecountInsideDatabase() = runBlocking {
        val task = CollectionTaskEntity(id = "task", originalUrl = "url", createdAt = 1, updatedAt = 1)
        database.collectionTaskDao().insert(task)
        val repository = CollectionRepository(
            database.collectionTaskDao(),
            database.commentDao(),
            RoomTransactionRunner(database),
            now = { 50L },
        )
        val parent = CommentEntity(
            id = "parent", taskId = "task", platformCommentId = "p1",
            dedupKey = "platform:p1", content = "Parent", collectedAt = 2, sortOrder = 1,
        )
        val reply = CommentEntity(
            id = "reply", taskId = "task", platformCommentId = null,
            dedupKey = "fallback:reply", parentCommentId = "parent",
            content = "Reply", collectedAt = 3, sortOrder = 2,
        )

        assertEquals(1, repository.persistCommentBatch("task", listOf(parent)))
        assertEquals(2, repository.persistCommentBatch("task", listOf(parent, reply)))
        assertEquals(2, repository.persistCommentBatch("task", listOf(reply.copy(id = "reply-reloaded"))))

        val saved = database.commentDao().getByTaskId("task")
        assertEquals(2, saved.size)
        assertEquals("parent", saved.single { it.id == "reply" }.parentCommentId)
        assertEquals(2, database.collectionTaskDao().getById("task")?.actualSavedCommentCount)
    }
}
