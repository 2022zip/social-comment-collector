package com.socialcommentcollector.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionStatusPolicyTest {
    @Test
    fun `core collection transitions are allowed`() {
        assertTrue(CollectionStatusPolicy.canTransition(CollectionStatus.QUEUED, CollectionStatus.COLLECTING))
        assertTrue(CollectionStatusPolicy.canTransition(CollectionStatus.COLLECTING, CollectionStatus.COMPLETED))
        assertTrue(CollectionStatusPolicy.canTransition(CollectionStatus.COLLECTING, CollectionStatus.INCOMPLETE))
        assertTrue(CollectionStatusPolicy.canTransition(CollectionStatus.COLLECTING, CollectionStatus.FAILED))
    }

    @Test
    fun `terminal states and arbitrary jumps are rejected`() {
        assertFalse(CollectionStatusPolicy.canTransition(CollectionStatus.QUEUED, CollectionStatus.COMPLETED))
        assertFalse(CollectionStatusPolicy.canTransition(CollectionStatus.COMPLETED, CollectionStatus.COLLECTING))
        assertFalse(CollectionStatusPolicy.canTransition(CollectionStatus.INCOMPLETE, CollectionStatus.COLLECTING))
        assertFalse(CollectionStatusPolicy.canTransition(CollectionStatus.FAILED, CollectionStatus.QUEUED))
        assertFalse(CollectionStatusPolicy.canTransition(CollectionStatus.COLLECTING, CollectionStatus.COLLECTING))
    }
}
