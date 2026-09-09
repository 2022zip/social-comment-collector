package com.socialcommentcollector.app.data

import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CollectionRepository(
    private val taskDao: CollectionTaskDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeTasks(): Flow<List<CollectionTaskEntity>> = taskDao.observeAll()

    fun createTaskDraft(originalUrl: String): CollectionTaskEntity {
        val timestamp = now()
        return CollectionTaskEntity(
            originalUrl = originalUrl,
            displayedCommentCount = 0,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    suspend fun saveTask(task: CollectionTaskEntity) = taskDao.insert(task)

    suspend fun createTask(originalUrl: String, platform: Platform): CollectionTaskEntity {
        val task = createTaskDraft(originalUrl).copy(platform = platform)
        taskDao.insert(task)
        return task
    }

    suspend fun updateStatus(id: String, status: CollectionStatus, failureReason: String? = null) {
        val task = getTask(id) ?: return
        taskDao.update(task.copy(status = status, failureReason = failureReason, updatedAt = now()))
    }

    suspend fun getTask(id: String): CollectionTaskEntity? =
        taskDao.observeAll().first().firstOrNull { it.id == id }
}
