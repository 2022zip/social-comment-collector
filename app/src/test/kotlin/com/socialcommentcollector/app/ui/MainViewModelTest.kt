package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.socialcommentcollector.app.MainDispatcherRule
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import com.socialcommentcollector.app.domain.StartCollectionUseCase
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.RedirectResolver
import com.socialcommentcollector.app.platform.ShareInputResolver
import com.socialcommentcollector.app.platform.ShareTextUrlExtractor
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionState
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun inputAndClearUpdateStateAndSavedInput() = runTest {
        val handle = SavedStateHandle()
        val model = MainViewModel(CollectionRepository(FakeCollectionTaskDao()), handle)
        val store = ViewModelStore().apply { put("main", model) }
        try {
            assertFalse(model.uiState.value.canUseUrlActions)
            model.updateUrl("  https://example.com/post  ")
            assertEquals("  https://example.com/post  ", model.uiState.value.url)
            assertTrue(model.uiState.value.canUseUrlActions)
            model.clearUrl()
            assertEquals("", model.uiState.value.url)
            assertEquals("", handle.get<String>("url"))
            assertFalse(model.uiState.value.canUseUrlActions)
        } finally { store.clear() }
    }

    @Test
    fun whitespaceDoesNotEnableActionsAndSavedInputIsRestored() = runTest {
        val model = MainViewModel(
            CollectionRepository(FakeCollectionTaskDao()),
            SavedStateHandle(mapOf("url" to "saved input")),
        )
        val store = ViewModelStore().apply { put("main", model) }
        try {
            assertEquals("saved input", model.uiState.value.url)
            model.updateUrl(" \n ")
            assertFalse(model.uiState.value.canUseUrlActions)
        } finally { store.clear() }
    }

    @Test
    fun repositoryTaskUpdatesReachUiWithoutOverwritingInput() = runTest {
        val dao = FakeCollectionTaskDao()
        val repository = CollectionRepository(dao)
        val model = MainViewModel(repository, SavedStateHandle())
        val store = ViewModelStore().apply { put("main", model) }
        try {
            model.updateUrl("keep me")
            val task = repository.createTaskDraft("another input")
            dao.insert(task)
            runCurrent()
            assertEquals(listOf(task), model.uiState.value.tasks)
            assertEquals("keep me", model.uiState.value.url)
        } finally { store.clear() }
    }

    @Test
    fun startWithReadySessionCreatesQueuedTaskAndExposesPreparationState() = runTest {
        val fixture = StartFixture("sessionid=valid")
        fixture.sessions.observePage("https://www.xiaohongshu.com/explore", true)
        fixture.model.updateUrl("https://www.xiaohongshu.com/explore/abc")

        fixture.model.startCollection()
        runCurrent()

        assertEquals(CollectionStatus.QUEUED, fixture.dao.tasks.value.single().status)
        assertEquals(MainMessage.PREPARING_COLLECTION, fixture.model.uiState.value.message)
        assertEquals(Platform.XIAOHONGSHU, fixture.model.uiState.value.resolvedPlatform)
        assertEquals(XiaohongshuSessionState.READY, fixture.model.uiState.value.sessionState)
        assertNotNull(fixture.model.uiState.value.currentTaskId)
        assertEquals("https://www.xiaohongshu.com/explore/abc", fixture.model.uiState.value.navigationRequest?.url)
        fixture.close()
    }

    @Test
    fun startWithNoSessionRequestsLoginWithoutCreatingTask() = runTest {
        val fixture = StartFixture(null)
        fixture.model.updateUrl("https://www.xiaohongshu.com/explore/abc")

        fixture.model.startCollection()
        runCurrent()

        assertTrue(fixture.dao.tasks.value.isEmpty())
        assertEquals(MainMessage.LOGIN_REQUIRED, fixture.model.uiState.value.message)
        assertTrue(fixture.model.uiState.value.loginRequired)
        assertEquals(XiaohongshuSessionState.LOGIN_REQUIRED, fixture.model.uiState.value.sessionState)
        assertEquals(Platform.XIAOHONGSHU, fixture.model.uiState.value.resolvedPlatform)
        fixture.close()
    }

    @Test
    fun openPageResolvesShareTextToFinalWebUrlWithoutCreatingTask() = runTest {
        val final = URI(
            "https://www.xiaohongshu.com/explore/note-id?xsec_token=fixture&xsec_source=pc_feed",
        )
        val fixture = StartFixture(null, redirect = { final })
        fixture.model.updateUrl("分享一下 https://xhslink.cn/o/short 保留口令")

        fixture.model.openWebPage()
        advanceUntilIdle()

        assertEquals(final.toString(), fixture.model.uiState.value.navigationRequest?.url)
        assertEquals(Platform.XIAOHONGSHU, fixture.model.uiState.value.resolvedPlatform)
        assertTrue(fixture.dao.tasks.value.isEmpty())
        fixture.close()
    }

    @Test
    fun invalidAndJikeInputsExposeErrorsWithoutCreatingTask() = runTest {
        val fixture = StartFixture(null)
        fixture.model.updateUrl("invalid")
        fixture.model.startCollection()
        runCurrent()
        assertEquals(MainMessage.INVALID_URL, fixture.model.uiState.value.message)

        fixture.model.updateUrl("https://web.okjike.com/u/abc")
        fixture.model.startCollection()
        runCurrent()
        assertEquals(MainMessage.JIKE_NOT_IMPLEMENTED, fixture.model.uiState.value.message)
        assertTrue(fixture.dao.tasks.value.isEmpty())
        fixture.close()
    }

    @Test
    fun duplicateStartIsIgnoredWhileResolutionIsInProgress() = runTest {
        val redirectGate = CompletableDeferred<URI>()
        val redirectCalls = AtomicInteger()
        val fixture = StartFixture("sessionid=valid") {
            redirectCalls.incrementAndGet()
            redirectGate.await()
        }
        fixture.sessions.observePage("https://www.xiaohongshu.com/explore", true)
        fixture.model.updateUrl("https://xhslink.com/a")

        fixture.model.startCollection()
        fixture.model.startCollection()
        runCurrent()

        assertTrue(fixture.model.uiState.value.startInProgress)
        assertEquals(1, redirectCalls.get())
        assertTrue(fixture.dao.tasks.value.isEmpty())

        redirectGate.complete(URI("https://www.xiaohongshu.com/explore/a"))
        advanceUntilIdle()

        assertFalse(fixture.model.uiState.value.startInProgress)
        assertEquals(1, fixture.dao.tasks.value.size)
        fixture.close()
    }

    private class StartFixture(
        cookie: String?,
        redirect: suspend (URI) -> URI = { it },
    ) {
        val dao = FakeCollectionTaskDao()
        private val repository = CollectionRepository(dao, now = { 10L })
        val sessions = XiaohongshuSessionManager(object : XiaohongshuCookieStore {
            override fun cookiesFor(url: String): String? = cookie
            override suspend fun clear() = Unit
        })
        private val resolver = UrlResolver(RedirectResolver(redirect))
        private val inputResolver = ShareInputResolver(ShareTextUrlExtractor(), resolver)
        private val useCase = StartCollectionUseCase(repository, inputResolver, sessions)
        val model = MainViewModel(repository, SavedStateHandle(), useCase, inputResolver)
        private val store = ViewModelStore().apply { put("main", model) }
        fun close() = store.clear()
    }
}
