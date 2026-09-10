package com.socialcommentcollector.app.platform.xiaohongshu

import com.socialcommentcollector.app.data.CollectionContent
import com.socialcommentcollector.app.data.CommentEntity
import com.socialcommentcollector.app.data.CommentIdentityFactory

class XiaohongshuContentParser {
    fun parse(payload: XiaohongshuContentPayload): CollectionContent {
        require(payload.protocolVersion == SUPPORTED_PROTOCOL_VERSION) {
            "Unsupported Xiaohongshu content protocol: ${payload.protocolVersion}"
        }
        require(payload.displayedCommentCount == null || payload.displayedCommentCount >= 0) {
            "Displayed comment count cannot be negative"
        }
        return CollectionContent(
            title = payload.title?.trim()?.takeIf(String::isNotEmpty),
            author = payload.author?.trim()?.takeIf(String::isNotEmpty),
            body = payload.body?.trim()?.takeIf(String::isNotEmpty),
            publishedAt = payload.publishedAt,
            collectedAt = payload.collectedAt,
            displayedCommentCount = payload.displayedCommentCount,
        )
    }

    private companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1
    }
}

class XiaohongshuCommentParser(
    private val identities: CommentIdentityFactory = CommentIdentityFactory(),
) {
    fun parse(taskId: String, payloads: List<XiaohongshuCommentPayload>): List<CommentEntity> =
        payloads.map { payload ->
            require(payload.protocolVersion == SUPPORTED_PROTOCOL_VERSION) {
                "Unsupported Xiaohongshu comment protocol: ${payload.protocolVersion}"
            }
            require(payload.content.isNotBlank()) { "Comment content cannot be blank" }
            require(payload.sourcePosition.isNotBlank()) { "Comment source position cannot be blank" }
            val dedupKey = identities.dedupKey(
                taskId = taskId,
                platformCommentId = payload.platformCommentId,
                parentIdentity = payload.parentIdentity,
                author = payload.author,
                content = payload.content,
                publishedAt = payload.publishedAt,
                sourcePosition = payload.sourcePosition,
            )
            CommentEntity(
                id = identities.entityId(taskId, dedupKey),
                taskId = taskId,
                platformCommentId = payload.platformCommentId?.trim()?.takeIf(String::isNotEmpty),
                dedupKey = dedupKey,
                parentCommentId = payload.parentIdentity?.let { identities.entityId(taskId, it) },
                author = payload.author?.trim()?.takeIf(String::isNotEmpty),
                content = payload.content.trim(),
                publishedAt = payload.publishedAt,
                collectedAt = payload.collectedAt,
                sortOrder = payload.sortOrder,
            )
        }

    private companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1
    }
}
