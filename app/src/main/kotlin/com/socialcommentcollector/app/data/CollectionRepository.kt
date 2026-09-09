package com.socialcommentcollector.app.data

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

    suspend fun getTask(id: String): CollectionTaskEntity? =
        taskDao.observeAll().first().firstOrNull { it.id == id }
}
