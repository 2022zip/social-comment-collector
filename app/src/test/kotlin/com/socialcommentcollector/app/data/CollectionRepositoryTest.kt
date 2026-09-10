package com.socialcommentcollector.app.data

import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CollectionRepositoryTest {
    private fun repository(
        taskDao: FakeCollectionTaskDao = FakeCollectionTaskDao(),
        commentDao: FakeCommentDao = FakeCommentDao(),
        now: () -> Long = { 1234L },
    ) = CollectionRepository(taskDao, commentDao, ImmediateTransactionRunner, now)

    @Test
    fun draftPreservesInputWithoutResolvingPlatformOrClaimingCollection() {
        val repository = repository()
        val task = repository.createTaskDraft("pasted content, not resolved")

        assertEquals("pasted content, not resolved", task.originalUrl)
        assertEquals(Platform.UNKNOWN, task.platform)
        assertEquals(CollectionStatus.QUEUED, task.status)
        assertNull(task.displayedCommentCount)
        assertEquals(0, task.actualSavedCommentCount)
        assertNull(task.title)
        assertNull(task.failureReason)
        assertEquals(1234L, task.createdAt)
        assertEquals(1234L, task.updatedAt)
    }

    @Test
    fun creatingDraftDoesNotPersistOrStartCollection() = runTest {
        val repository = repository(now = System::currentTimeMillis)
        val first = repository.createTaskDraft("input")
        val second = repository.createTaskDraft("input")
        assertNotEquals(first.id, second.id)
        assertTrue(repository.observeTasks().first().isEmpty())
    }

    @Test
    fun createTaskPersistsOneQueuedXiaohongshuTaskPerRequest() = runTest {
        val repository = repository()

        val first = repository.createTask("https://www.xiaohongshu.com/a", Platform.XIAOHONGSHU)
        val second = repository.createTask("https://www.xiaohongshu.com/a", Platform.XIAOHONGSHU)

        assertNotEquals(first.id, second.id)
        assertEquals(2, repository.observeTasks().first().size)
        assertTrue(repository.observeTasks().first().all { it.status == CollectionStatus.QUEUED })
        assertTrue(repository.observeTasks().first().all { it.platform == Platform.XIAOHONGSHU })
    }

    @Test
    fun taskCanMoveFromQueuedToCollecting() = runTest {
        val repository = repository()
        val task = repository.createTask("https://www.xiaohongshu.com/a", Platform.XIAOHONGSHU)

        repository.updateStatus(task.id, CollectionStatus.COLLECTING)

        assertEquals(CollectionStatus.COLLECTING, repository.getTask(task.id)?.status)
    }

    @Test
    fun `content is persisted independently before comments`() = runTest {
        val tasks = FakeCollectionTaskDao()
        val repository = repository(tasks)
        val task = repository.createTask("https://www.xiaohongshu.com/explore/a", Platform.XIAOHONGSHU)

        repository.saveContent(
            task.id,
            CollectionContent(
                title = "Title",
                author = "Author",
                body = "Body",
                publishedAt = 100L,
                collectedAt = 200L,
                displayedCommentCount = null,
            ),
        )

        val saved = repository.getTask(task.id)!!
        assertEquals("Title", saved.title)
        assertEquals("Author", saved.author)
        assertEquals("Body", saved.body)
        assertEquals(100L, saved.publishedAt)
        assertEquals(200L, saved.collectedAt)
        assertNull(saved.displayedCommentCount)
    }

    @Test
    fun `batches are idempotent and actual count is derived from stored rows`() = runTest {
        val tasks = FakeCollectionTaskDao()
        val comments = FakeCommentDao()
        var timestamp = 2000L
        val repository = repository(tasks, comments) { timestamp++ }
        val task = repository.createTask("https://www.xiaohongshu.com/explore/a", Platform.XIAOHONGSHU)
        val first = comment(task.id, "platform:first", "first")
        val second = comment(task.id, "platform:second", "second")

        assertEquals(1, repository.persistCommentBatch(task.id, listOf(first)))
        assertEquals(1, repository.persistCommentBatch(task.id, listOf(first)))
        assertEquals(2, repository.persistCommentBatch(task.id, listOf(first, second)))

        assertEquals(2, comments.countByTaskId(task.id))
        assertEquals(2, repository.getTask(task.id)?.actualSavedCommentCount)
        assertEquals(2003L, repository.getTask(task.id)?.updatedAt)
    }

    @Test(expected = IllegalStateException::class)
    fun `repository rejects invalid status transition`() = runTest {
        val repository = repository()
        val task = repository.createTask("https://www.xiaohongshu.com/explore/a", Platform.XIAOHONGSHU)

        repository.markCompleted(task.id)
    }

    private fun comment(taskId: String, dedupKey: String, content: String) = CommentEntity(
        id = "id:$dedupKey",
        taskId = taskId,
        dedupKey = dedupKey,
        content = content,
        collectedAt = 1L,
        sortOrder = 1L,
    )
}
