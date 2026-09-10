package com.socialcommentcollector.app.data

import kotlinx.coroutines.flow.MutableStateFlow

class FakeCollectionTaskDao : CollectionTaskDao {
    val tasks = MutableStateFlow<List<CollectionTaskEntity>>(emptyList())
    override fun observeAll() = tasks
    override suspend fun insert(task: CollectionTaskEntity) {
        tasks.value = tasks.value + task
    }
    override suspend fun update(task: CollectionTaskEntity) {
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }
    override suspend fun getById(id: String): CollectionTaskEntity? = tasks.value.firstOrNull { it.id == id }
    override suspend fun updateActualCount(id: String, count: Int, updatedAt: Long) {
        tasks.value = tasks.value.map {
            if (it.id == id) it.copy(actualSavedCommentCount = count, updatedAt = updatedAt) else it
        }
    }
    override suspend fun deleteById(id: String) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }
}
