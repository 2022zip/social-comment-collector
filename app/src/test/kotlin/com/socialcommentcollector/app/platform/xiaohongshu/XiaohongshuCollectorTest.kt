package com.socialcommentcollector.app.platform.xiaohongshu

import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import com.socialcommentcollector.app.data.FakeCommentDao
import com.socialcommentcollector.app.data.ImmediateTransactionRunner
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuCollectorTest {
    @Test
    fun `content one batch and explicit end completes`() = runTest {
        val fixture = Fixture(
            content = content(),
            pages = listOf(batch(root()), CommentPageResult.End),
        )

        val result = fixture.collect()

        assertTrue(result is XiaohongshuCollectionResult.Completed)
        assertEquals(CollectionStatus.COMPLETED, fixture.task().status)
        assertEquals(1, fixture.task().actualSavedCommentCount)
        assertEquals("Body", fixture.task().body)
    }

    @Test
    fun `multiple overlapping batches persist unique comments and replies`() = runTest {
        val parent = root()
        val reply = reply()
        val fixture = Fixture(
            content = content(displayedCount = 2),
            pages = listOf(batch(parent), batch(parent, reply), CommentPageResult.End),
        )

        fixture.collect()

        val comments = fixture.comments.commentsFor("task-1")
        assertEquals(2, comments.size)
        assertEquals(comments.first { it.dedupKey == "platform:parent-1" }.id, comments.single { it.platformCommentId == "reply-1" }.parentCommentId)
        assertEquals(2, fixture.task().actualSavedCommentCount)
    }

    @Test
    fun `content followed by comment failure is incomplete`() = runTest {
        val fixture = Fixture(content(), listOf(CommentPageResult.Failure("pagination interrupted")))

        val result = fixture.collect()

        assertTrue(result is XiaohongshuCollectionResult.Incomplete)
        assertEquals(CollectionStatus.INCOMPLETE, fixture.task().status)
        assertEquals("pagination interrupted", fixture.task().failureReason)
        assertEquals("Body", fixture.task().body)
    }

    @Test
    fun `partial comments followed by failure are retained and incomplete`() = runTest {
        val fixture = Fixture(content = null, pages = listOf(batch(root()), CommentPageResult.Failure("session expired")))

        val result = fixture.collect()

        assertTrue(result is XiaohongshuCollectionResult.Incomplete)
        assertEquals(1, fixture.comments.commentsFor("task-1").size)
        assertEquals(1, fixture.task().actualSavedCommentCount)
    }

    @Test
    fun `failure before useful data is failed`() = runTest {
        val fixture = Fixture(content = null, pages = listOf(CommentPageResult.Failure("page unavailable")))

        val result = fixture.collect()

        assertTrue(result is XiaohongshuCollectionResult.Failed)
        assertEquals(CollectionStatus.FAILED, fixture.task().status)
        assertEquals(0, fixture.task().actualSavedCommentCount)
    }

    private class Fixture(content: XiaohongshuContentPayload?, pages: List<CommentPageResult>) {
        private val tasks = FakeCollectionTaskDao()
        val comments = FakeCommentDao()
        private val repository = CollectionRepository(tasks, comments, ImmediateTransactionRunner, now = { 100L })
        private val source = FixturePageSource(content, pages)
        private val collector = XiaohongshuCollector(
            repository = repository,
            pageSource = source,
            contentParser = XiaohongshuContentParser(),
            commentParser = XiaohongshuCommentParser(),
        )

        init {
            tasks.tasks.value = listOf(
                com.socialcommentcollector.app.data.CollectionTaskEntity(
                    id = "task-1",
                    originalUrl = "https://www.xiaohongshu.com/explore/note-1",
                    platform = Platform.XIAOHONGSHU,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
        }

        suspend fun collect() = collector.collect("task-1")
        fun task() = tasks.tasks.value.single()
    }
}

private class FixturePageSource(
    private val content: XiaohongshuContentPayload?,
    pages: List<CommentPageResult>,
) : XiaohongshuPageSource {
    private val remaining = ArrayDeque(pages)
    override suspend fun content(): ContentPageResult = content?.let(ContentPageResult::Available) ?: ContentPageResult.Unavailable
    override suspend fun nextComments(): CommentPageResult = remaining.removeFirst()
}

private fun content(displayedCount: Int? = null) = XiaohongshuContentPayload(
    protocolVersion = 1,
    title = "Fixture title",
    author = "Fixture author",
    body = "Body",
    publishedAt = 10L,
    collectedAt = 20L,
    displayedCommentCount = displayedCount,
)

private fun root() = XiaohongshuCommentPayload(
    protocolVersion = 1,
    platformCommentId = "parent-1",
    parentIdentity = null,
    author = "Alice",
    content = "Root",
    publishedAt = 11L,
    collectedAt = 21L,
    sortOrder = 1L,
    sourcePosition = "root/1",
)

private fun reply() = XiaohongshuCommentPayload(
    protocolVersion = 1,
    platformCommentId = "reply-1",
    parentIdentity = "platform:parent-1",
    author = "Bob",
    content = "Reply",
    publishedAt = 12L,
    collectedAt = 22L,
    sortOrder = 2L,
    sourcePosition = "root/1/reply/1",
)

private fun batch(vararg comments: XiaohongshuCommentPayload) = CommentPageResult.Batch(comments.toList())
