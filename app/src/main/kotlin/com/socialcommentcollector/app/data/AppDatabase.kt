package com.socialcommentcollector.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CollectionTaskEntity::class, CommentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun collectionTaskDao(): CollectionTaskDao
    abstract fun commentDao(): CommentDao
}
