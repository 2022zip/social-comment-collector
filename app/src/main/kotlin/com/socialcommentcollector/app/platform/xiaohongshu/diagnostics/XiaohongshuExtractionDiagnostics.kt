package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import kotlinx.coroutines.delay

interface DiagnosticPageEvaluator {
    suspend fun capture(): RawDiagnosticSnapshot
    suspend fun scrollOneViewport()
}

fun interface DiagnosticObservationWaiter {
    suspend fun awaitNextObservation()
}

object AndroidDiagnosticObservationWaiter : DiagnosticObservationWaiter {
    override suspend fun awaitNextObservation() {
        delay(250)
    }
}

class XiaohongshuExtractionDiagnostics(
    private val evaluator: DiagnosticPageEvaluator,
    private val waiter: DiagnosticObservationWaiter = AndroidDiagnosticObservationWaiter,
    private val sanitizer: DiagnosticSanitizer = DiagnosticSanitizer(),
    private val maxObservations: Int = 20,
) {
    init {
        require(maxObservations > 0)
    }

    suspend fun captureAndObserveScroll(): DiagnosticObservation {
        val before = sanitizer.sanitize(evaluator.capture())
        evaluator.scrollOneViewport()

        var after = before
        repeat(maxObservations) { index ->
            waiter.awaitNextObservation()
            after = sanitizer.sanitize(evaluator.capture())
            if (after.safeUrl != before.safeUrl) {
                return DiagnosticObservation.NavigationChanged(before, after, index + 1)
            }
            if (after.pageFingerprint() != before.pageFingerprint()) {
                return DiagnosticObservation.Changed(before, after, index + 1)
            }
        }
        return DiagnosticObservation.Stalled(before, after, maxObservations)
    }
}
