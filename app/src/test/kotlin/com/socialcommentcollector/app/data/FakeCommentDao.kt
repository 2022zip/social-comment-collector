package com.socialcommentcollector.app.data

class FakeCommentDao : CommentDao {
    private val comments = linkedMapOf<Pair<String, String>, CommentEntity>()

    override suspend fun insert(comment: CommentEntity): Long = insertAll(listOf(comment)).single()

    override suspend fun insertAll(comments: List<CommentEntity>): List<Long> = comments.map { comment ->
        val key = comment.taskId to comment.dedupKey
        if (this.comments.containsKey(key)) -1L else {
            this.comments[key] = comment
            this.comments.size.toLong()
        }
    }

    override suspend fun getByTaskId(taskId: String): List<CommentEntity> = commentsFor(taskId)
    override suspend fun countByTaskId(taskId: String): Int = commentsFor(taskId).size

    fun commentsFor(taskId: String): List<CommentEntity> = comments.values
        .filter { it.taskId == taskId }
        .sortedWith(compareBy(CommentEntity::sortOrder, CommentEntity::id))
}
