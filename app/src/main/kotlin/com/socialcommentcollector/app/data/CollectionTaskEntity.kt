package com.socialcommentcollector.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import java.util.UUID

@Entity(tableName = "collection_tasks")
data class CollectionTaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val originalUrl: String,
    val platform: Platform = Platform.UNKNOWN,
    val title: String? = null,
    val status: CollectionStatus = CollectionStatus.QUEUED,
    val displayedCommentCount: Int? = null,
    val actualSavedCommentCount: Int = 0,
    val failureReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
