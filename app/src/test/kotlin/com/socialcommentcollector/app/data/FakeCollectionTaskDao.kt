package com.socialcommentcollector.app.data

import kotlinx.coroutines.flow.MutableStateFlow

class FakeCollectionTaskDao : CollectionTaskDao {
    val tasks = MutableStateFlow<List<CollectionTaskEntity>>(emptyList())
    override fun observeAll() = tasks
    override suspend fun insert(task: CollectionTaskEntity) {
        tasks.value = tasks.value + task
    }
    override suspend fun deleteById(id: String) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }
}
