package com.socialcommentcollector.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionTaskDao {
    @Insert
    suspend fun insert(task: CollectionTaskEntity)

    @Update
    suspend fun update(task: CollectionTaskEntity)

    @Query("SELECT * FROM collection_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CollectionTaskEntity?

    @Query("UPDATE collection_tasks SET actualSavedCommentCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateActualCount(id: String, count: Int, updatedAt: Long)

    @Query("SELECT * FROM collection_tasks ORDER BY createdAt DESC, id ASC")
    fun observeAll(): Flow<List<CollectionTaskEntity>>

    @Query("DELETE FROM collection_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
