package com.socialcommentcollector.app.model

import com.socialcommentcollector.app.data.DatabaseConverters
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionStatusTest {
    @Test
    fun persistedStatusesDecodeWithoutLosingIncompleteOrFailedStates() {
        val converters = DatabaseConverters()
        val fixtures = listOf(
            "QUEUED" to CollectionStatus.QUEUED,
            "COLLECTING" to CollectionStatus.COLLECTING,
            "COMPLETED" to CollectionStatus.COMPLETED,
            "INCOMPLETE" to CollectionStatus.INCOMPLETE,
            "FAILED" to CollectionStatus.FAILED,
        )
        fixtures.forEach { (stored, status) ->
            assertEquals(status, converters.toStatus(stored))
            assertEquals(stored, converters.fromStatus(status))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownStoredStatusIsNotSilentlyReportedAsCompleted() {
        DatabaseConverters().toStatus("INVALID")
    }
}
