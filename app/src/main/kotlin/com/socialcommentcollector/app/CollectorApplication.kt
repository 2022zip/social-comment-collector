package com.socialcommentcollector.app

import android.app.Application
import androidx.room.Room
import com.socialcommentcollector.app.data.AppDatabase
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.RoomTransactionRunner
import com.socialcommentcollector.app.domain.StartCollectionUseCase
import com.socialcommentcollector.app.platform.HttpRedirectResolver
import com.socialcommentcollector.app.platform.ShareInputResolver
import com.socialcommentcollector.app.platform.ShareTextUrlExtractor
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.AndroidXiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager

class CollectorApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "collector.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
    val repository by lazy {
        CollectionRepository(
            database.collectionTaskDao(),
            database.commentDao(),
            RoomTransactionRunner(database),
        )
    }
    private val sessions by lazy { XiaohongshuSessionManager(AndroidXiaohongshuCookieStore()) }
    private val urlResolver by lazy { UrlResolver(HttpRedirectResolver()) }
    val inputResolver by lazy { ShareInputResolver(ShareTextUrlExtractor(), urlResolver) }
    val startCollection by lazy { StartCollectionUseCase(repository, inputResolver, sessions) }
}
