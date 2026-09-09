package com.socialcommentcollector.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "comments",
    foreignKeys = [ForeignKey(
        entity = CollectionTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["taskId", "sortOrder"])],
)
data class CommentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val platformCommentId: String? = null,
    val parentCommentId: String? = null,
    val author: String? = null,
    val content: String,
    val publishedAt: Long? = null,
    val collectedAt: Long,
    val sortOrder: Long,
)
