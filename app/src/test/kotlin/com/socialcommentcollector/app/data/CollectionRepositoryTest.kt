package com.socialcommentcollector.app.data

import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CollectionRepositoryTest {
    @Test
    fun draftPreservesInputWithoutResolvingPlatformOrClaimingCollection() {
        val repository = CollectionRepository(FakeCollectionTaskDao(), now = { 1234L })
        val task = repository.createTaskDraft("pasted content, not resolved")

        assertEquals("pasted content, not resolved", task.originalUrl)
        assertEquals(Platform.UNKNOWN, task.platform)
        assertEquals(CollectionStatus.QUEUED, task.status)
        assertEquals(0, task.displayedCommentCount)
        assertEquals(0, task.actualSavedCommentCount)
        assertNull(task.title)
        assertNull(task.failureReason)
        assertEquals(1234L, task.createdAt)
        assertEquals(1234L, task.updatedAt)
    }

    @Test
    fun creatingDraftDoesNotPersistOrStartCollection() = runTest {
        val repository = CollectionRepository(FakeCollectionTaskDao())
        val first = repository.createTaskDraft("input")
        val second = repository.createTaskDraft("input")
        assertNotEquals(first.id, second.id)
        assertTrue(repository.observeTasks().first().isEmpty())
    }
}
