package com.socialcommentcollector.app.data

import java.security.MessageDigest
import java.util.Locale

class CommentIdentityFactory {
    fun dedupKey(
        taskId: String,
        platformCommentId: String?,
        parentIdentity: String?,
        author: String?,
        content: String,
        publishedAt: Long?,
        sourcePosition: String,
    ): String {
        platformCommentId?.trim()?.takeIf(String::isNotEmpty)?.let { return "platform:$it" }
        val canonical = listOf(
            taskId.trim(),
            parentIdentity?.trim().orEmpty(),
            normalize(author),
            normalize(content),
            publishedAt?.toString().orEmpty(),
            sourcePosition.trim(),
        ).joinToString("\u001f")
        return "fallback:${sha256(canonical)}"
    }

    fun entityId(taskId: String, dedupKey: String): String =
        "comment:${sha256("${taskId.trim()}\u001f${dedupKey.trim()}")}"

    private fun normalize(value: String?): String = value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase(Locale.ROOT)
        .orEmpty()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
