package com.socialcommentcollector.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CommentDao {
    @Insert
    suspend fun insert(comment: CommentEntity)

    @Insert
    suspend fun insertAll(comments: List<CommentEntity>)

    @Query("SELECT * FROM comments WHERE taskId = :taskId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByTaskId(taskId: String): List<CommentEntity>

    @Query("SELECT COUNT(*) FROM comments WHERE taskId = :taskId")
    suspend fun countByTaskId(taskId: String): Int
}
