package com.socialcommentcollector.app

import android.app.Application
import androidx.room.Room
import com.socialcommentcollector.app.data.AppDatabase
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.domain.StartCollectionUseCase
import com.socialcommentcollector.app.platform.HttpRedirectResolver
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.AndroidXiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager

class CollectorApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "collector.db").build()
    }
    val repository by lazy { CollectionRepository(database.collectionTaskDao()) }
    private val sessions by lazy { XiaohongshuSessionManager(AndroidXiaohongshuCookieStore()) }
    val startCollection by lazy {
        StartCollectionUseCase(repository, UrlResolver(HttpRedirectResolver()), sessions)
    }
}
