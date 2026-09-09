package com.socialcommentcollector.app

import android.app.Application
import androidx.room.Room
import com.socialcommentcollector.app.data.AppDatabase
import com.socialcommentcollector.app.data.CollectionRepository

class CollectorApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "collector.db").build()
    }
    val repository by lazy { CollectionRepository(database.collectionTaskDao()) }
}
