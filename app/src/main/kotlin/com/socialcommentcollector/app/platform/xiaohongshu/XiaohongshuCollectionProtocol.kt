package com.socialcommentcollector.app.platform.xiaohongshu

data class XiaohongshuContentPayload(
    val protocolVersion: Int,
    val title: String?,
    val author: String?,
    val body: String?,
    val publishedAt: Long?,
    val collectedAt: Long,
    val displayedCommentCount: Int?,
)

data class XiaohongshuCommentPayload(
    val protocolVersion: Int,
    val platformCommentId: String?,
    /** Parent comment's canonical dedup key from this normalized protocol. */
    val parentIdentity: String?,
    val author: String?,
    val content: String,
    val publishedAt: Long?,
    val collectedAt: Long,
    val sortOrder: Long,
    /** Stable source path/cursor position, used only when the platform id is unavailable. */
    val sourcePosition: String,
)

sealed interface ContentPageResult {
    data class Available(val payload: XiaohongshuContentPayload) : ContentPageResult
    data class Failure(val reason: String) : ContentPageResult
    data object Unavailable : ContentPageResult
}

sealed interface CommentPageResult {
    data class Batch(val comments: List<XiaohongshuCommentPayload>) : CommentPageResult
    data class Failure(val reason: String) : CommentPageResult
    data object End : CommentPageResult
}

interface XiaohongshuPageSource {
    suspend fun content(): ContentPageResult
    suspend fun nextComments(): CommentPageResult
}
