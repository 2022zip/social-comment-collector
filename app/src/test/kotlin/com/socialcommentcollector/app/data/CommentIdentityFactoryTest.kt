package com.socialcommentcollector.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CommentIdentityFactoryTest {
    private val factory = CommentIdentityFactory()

    @Test
    fun `platform id produces the canonical platform key`() {
        assertEquals(
            "platform:comment-42",
            factory.dedupKey(
                taskId = "task-1",
                platformCommentId = " comment-42 ",
                parentIdentity = null,
                author = "Alice",
                content = "Hello",
                publishedAt = 10L,
                sourcePosition = "root/1",
            ),
        )
    }

    @Test
    fun `fallback key is deterministic across retries and normalization`() {
        val first = factory.dedupKey(
            taskId = "task-1",
            platformCommentId = null,
            parentIdentity = "platform:parent-1",
            author = " Alice ",
            content = "Hello   world\nagain",
            publishedAt = 10L,
            sourcePosition = "root/1/reply/2",
        )
        val retry = factory.dedupKey(
            taskId = "task-1",
            platformCommentId = null,
            parentIdentity = "platform:parent-1",
            author = "Alice",
            content = " Hello world again ",
            publishedAt = 10L,
            sourcePosition = "root/1/reply/2",
        )

        assertEquals(first, retry)
        assertEquals(true, first.startsWith("fallback:"))
    }

    @Test
    fun `fallback key is not based on comment text alone`() {
        val root = factory.dedupKey("task-1", null, null, "Alice", "Same", 10L, "root/1")
        val reply = factory.dedupKey("task-1", null, "platform:parent", "Alice", "Same", 10L, "reply/1")
        val anotherTask = factory.dedupKey("task-2", null, null, "Alice", "Same", 10L, "root/1")

        assertNotEquals(root, reply)
        assertNotEquals(root, anotherTask)
    }
}
