package com.socialcommentcollector.app.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.CollectionStatusPolicy
import com.socialcommentcollector.app.model.Platform
import kotlinx.coroutines.flow.Flow

data class CollectionContent(
    val title: String?,
    val author: String?,
    val body: String?,
    val publishedAt: Long?,
    val collectedAt: Long,
    val displayedCommentCount: Int?,
) {
    val isMeaningful: Boolean
        get() = !title.isNullOrBlank() || !author.isNullOrBlank() || !body.isNullOrBlank()
}

interface TransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun run(block: suspend () -> Unit) = block()
}

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun run(block: suspend () -> Unit) = database.withTransaction { block() }
}

class CollectionRepository(
    private val taskDao: CollectionTaskDao,
    private val commentDao: CommentDao = UnsupportedCommentDao,
    private val transactionRunner: TransactionRunner = ImmediateTransactionRunner,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeTasks(): Flow<List<CollectionTaskEntity>> = taskDao.observeAll()

    fun createTaskDraft(originalUrl: String): CollectionTaskEntity {
        val timestamp = now()
        return CollectionTaskEntity(
            originalUrl = originalUrl,
            displayedCommentCount = null,
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

    suspend fun saveContent(taskId: String, content: CollectionContent) {
        val task = requireTask(taskId)
        taskDao.update(
            task.copy(
                title = content.title,
                author = content.author,
                body = content.body,
                publishedAt = content.publishedAt,
                collectedAt = content.collectedAt,
                displayedCommentCount = content.displayedCommentCount,
                updatedAt = now(),
            ),
        )
    }

    suspend fun persistCommentBatch(taskId: String, comments: List<CommentEntity>): Int {
        require(comments.all { it.taskId == taskId }) { "Every comment must belong to task $taskId" }
        var actualCount = 0
        transactionRunner.run {
            if (comments.isNotEmpty()) commentDao.insertAll(comments)
            actualCount = commentDao.countByTaskId(taskId)
            taskDao.updateActualCount(taskId, actualCount, now())
        }
        return actualCount
    }

    suspend fun updateStatus(id: String, status: CollectionStatus, failureReason: String? = null) {
        val task = requireTask(id)
        check(CollectionStatusPolicy.canTransition(task.status, status)) {
            "Invalid collection status transition: ${task.status} -> $status"
        }
        taskDao.update(task.copy(status = status, failureReason = failureReason, updatedAt = now()))
    }

    suspend fun markCollecting(id: String) = updateStatus(id, CollectionStatus.COLLECTING)
    suspend fun markCompleted(id: String) = updateStatus(id, CollectionStatus.COMPLETED)

    suspend fun markIncomplete(id: String, reason: String) {
        require(reason.isNotBlank()) { "Incomplete collection requires a reason" }
        updateStatus(id, CollectionStatus.INCOMPLETE, reason)
    }

    suspend fun markFailed(id: String, reason: String) {
        require(reason.isNotBlank()) { "Failed collection requires a reason" }
        updateStatus(id, CollectionStatus.FAILED, reason)
    }

    suspend fun getActualCommentCount(taskId: String): Int = commentDao.countByTaskId(taskId)
    suspend fun getComments(taskId: String): List<CommentEntity> = commentDao.getByTaskId(taskId)
    suspend fun getTask(id: String): CollectionTaskEntity? = taskDao.getById(id)

    suspend fun hasUsefulData(taskId: String): Boolean {
        val task = requireTask(taskId)
        return !task.title.isNullOrBlank() || !task.author.isNullOrBlank() ||
            !task.body.isNullOrBlank() || commentDao.countByTaskId(taskId) > 0
    }

    private suspend fun requireTask(id: String): CollectionTaskEntity =
        checkNotNull(taskDao.getById(id)) { "Collection task not found: $id" }
}

private object UnsupportedCommentDao : CommentDao {
    private fun unavailable(): Nothing = error("Comment persistence is not configured")
    override suspend fun insert(comment: CommentEntity): Long = unavailable()
    override suspend fun insertAll(comments: List<CommentEntity>): List<Long> = unavailable()
    override suspend fun getByTaskId(taskId: String): List<CommentEntity> = unavailable()
    override suspend fun countByTaskId(taskId: String): Int = unavailable()
}
