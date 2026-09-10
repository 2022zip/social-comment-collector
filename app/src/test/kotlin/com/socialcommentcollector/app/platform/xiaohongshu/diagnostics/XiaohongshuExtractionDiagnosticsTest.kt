package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuExtractionDiagnosticsTest {
    @Test
    fun `reports meaningful page change after requested scroll`() = runTest {
        val evaluator = FakeEvaluator(
            snapshots = ArrayDeque(listOf(raw(scrollHeight = 1000), raw(scrollHeight = 1400))),
        )
        val diagnostics = XiaohongshuExtractionDiagnostics(
            evaluator = evaluator,
            waiter = NoWait,
            maxObservations = 3,
        )

        val result = diagnostics.captureAndObserveScroll()

        assertTrue(result is DiagnosticObservation.Changed)
        assertEquals(1, (result as DiagnosticObservation.Changed).observationCount)
        assertEquals(1, evaluator.scrollRequests)
    }

    @Test
    fun `reports navigation change without exposing query values`() = runTest {
        val evaluator = FakeEvaluator(
            snapshots = ArrayDeque(
                listOf(
                    raw(url = "https://www.xiaohongshu.com/explore/note?token=one"),
                    raw(url = "https://www.xiaohongshu.com/login?redirect=secret"),
                ),
            ),
        )
        val diagnostics = XiaohongshuExtractionDiagnostics(evaluator, NoWait, maxObservations = 2)

        val result = diagnostics.captureAndObserveScroll()

        assertTrue(result is DiagnosticObservation.NavigationChanged)
        result as DiagnosticObservation.NavigationChanged
        assertEquals("https://www.xiaohongshu.com/explore/note", result.before.safeUrl)
        assertEquals("https://www.xiaohongshu.com/login", result.after.safeUrl)
    }

    @Test
    fun `bounded no-change observation is stalled and never an end signal`() = runTest {
        val snapshot = raw()
        val evaluator = FakeEvaluator(ArrayDeque(listOf(snapshot, snapshot, snapshot, snapshot)))
        val diagnostics = XiaohongshuExtractionDiagnostics(evaluator, NoWait, maxObservations = 3)

        val result = diagnostics.captureAndObserveScroll()

        assertTrue(result is DiagnosticObservation.Stalled)
        assertEquals(3, (result as DiagnosticObservation.Stalled).observationCount)
    }
}

private class FakeEvaluator(
    private val snapshots: ArrayDeque<RawDiagnosticSnapshot>,
) : DiagnosticPageEvaluator {
    var scrollRequests = 0

    override suspend fun capture(): RawDiagnosticSnapshot = snapshots.removeFirst()

    override suspend fun scrollOneViewport() {
        scrollRequests += 1
    }
}

private object NoWait : DiagnosticObservationWaiter {
    override suspend fun awaitNextObservation() = Unit
}

private fun raw(
    url: String = "https://www.xiaohongshu.com/explore/note",
    scrollHeight: Int = 1000,
) = RawDiagnosticSnapshot(
    url = url,
    title = "Reference",
    readyState = "complete",
    elementCount = 10,
    scrollHeight = scrollHeight,
    viewportHeight = 600,
    structuredStateKeys = listOf("__INITIAL_STATE__"),
    semanticEntries = listOf(RawSemanticEntry("H1", "heading", null, "Title")),
)
