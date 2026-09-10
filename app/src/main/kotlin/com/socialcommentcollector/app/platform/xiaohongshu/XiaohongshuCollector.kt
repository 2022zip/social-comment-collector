package com.socialcommentcollector.app.platform.xiaohongshu

import com.socialcommentcollector.app.data.CollectionRepository

sealed interface XiaohongshuCollectionResult {
    val actualSavedCommentCount: Int

    data class Completed(
        override val actualSavedCommentCount: Int,
        val paginationEndConfirmed: Boolean = true,
    ) : XiaohongshuCollectionResult

    data class Incomplete(
        override val actualSavedCommentCount: Int,
        val reason: String,
    ) : XiaohongshuCollectionResult

    data class Failed(val reason: String) : XiaohongshuCollectionResult {
        override val actualSavedCommentCount: Int = 0
    }
}

class XiaohongshuCollector(
    private val repository: CollectionRepository,
    private val pageSource: XiaohongshuPageSource,
    private val contentParser: XiaohongshuContentParser,
    private val commentParser: XiaohongshuCommentParser,
) {
    suspend fun collect(taskId: String): XiaohongshuCollectionResult {
        repository.markCollecting(taskId)

        when (val contentResult = pageSource.content()) {
            is ContentPageResult.Available -> {
                val content = runCatching { contentParser.parse(contentResult.payload) }
                    .getOrElse { return terminateAfterFailure(taskId, specificReason("content parse failed", it)) }
                repository.saveContent(taskId, content)
            }
            is ContentPageResult.Failure -> return terminateAfterFailure(taskId, contentResult.reason)
            ContentPageResult.Unavailable -> Unit
        }

        while (true) {
            when (val page = pageSource.nextComments()) {
                is CommentPageResult.Batch -> {
                    val comments = runCatching { commentParser.parse(taskId, page.comments) }
                        .getOrElse { return terminateAfterFailure(taskId, specificReason("comment parse failed", it)) }
                    repository.persistCommentBatch(taskId, comments)
                }
                is CommentPageResult.Failure -> return terminateAfterFailure(taskId, page.reason)
                CommentPageResult.End -> {
                    repository.markCompleted(taskId)
                    return XiaohongshuCollectionResult.Completed(repository.getActualCommentCount(taskId))
                }
            }
        }
    }

    private suspend fun terminateAfterFailure(taskId: String, reason: String): XiaohongshuCollectionResult {
        val specificReason = reason.trim().takeIf(String::isNotEmpty) ?: "collection source failed"
        return if (repository.hasUsefulData(taskId)) {
            repository.markIncomplete(taskId, specificReason)
            XiaohongshuCollectionResult.Incomplete(repository.getActualCommentCount(taskId), specificReason)
        } else {
            repository.markFailed(taskId, specificReason)
            XiaohongshuCollectionResult.Failed(specificReason)
        }
    }

    private fun specificReason(prefix: String, error: Throwable): String =
        error.message?.takeIf(String::isNotBlank)?.let { "$prefix: $it" } ?: prefix
}
