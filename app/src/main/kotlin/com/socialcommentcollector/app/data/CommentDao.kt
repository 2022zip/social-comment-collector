package com.socialcommentcollector.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(comment: CommentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(comments: List<CommentEntity>): List<Long>

    @Query("SELECT * FROM comments WHERE taskId = :taskId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByTaskId(taskId: String): List<CommentEntity>

    @Query("SELECT COUNT(*) FROM comments WHERE taskId = :taskId")
    suspend fun countByTaskId(taskId: String): Int
}
