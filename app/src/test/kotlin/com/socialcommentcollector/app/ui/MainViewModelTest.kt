package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.socialcommentcollector.app.MainDispatcherRule
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import com.socialcommentcollector.app.domain.StartCollectionUseCase
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.platform.RedirectResolver
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun startWithReadySessionCreatesCollectingTaskAndNavigationRequest() = runTest {
        val fixture = StartFixture("sessionid=valid")
        fixture.sessions.observePage("https://www.xiaohongshu.com/explore", true)
        fixture.model.updateUrl("https://www.xiaohongshu.com/explore/abc")

        fixture.model.startCollection()
        runCurrent()

        assertEquals(CollectionStatus.COLLECTING, fixture.dao.tasks.value.single().status)
        assertEquals(MainMessage.SESSION_READY, fixture.model.uiState.value.message)
        assertEquals("https://www.xiaohongshu.com/explore/abc", fixture.model.uiState.value.navigationRequest?.url)
        fixture.close()
    }

    @Test
    fun startWithNoSessionKeepsQueuedTaskAndRequestsLogin() = runTest {
        val fixture = StartFixture(null)
        fixture.model.updateUrl("https://www.xiaohongshu.com/explore/abc")

        fixture.model.startCollection()
        runCurrent()

        assertEquals(CollectionStatus.QUEUED, fixture.dao.tasks.value.single().status)
        assertEquals(MainMessage.LOGIN_REQUIRED, fixture.model.uiState.value.message)
        assertNotNull(fixture.model.uiState.value.pendingTaskId)
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
        assertEquals(MainMessage.PLATFORM_UNAVAILABLE, fixture.model.uiState.value.message)
        assertTrue(fixture.dao.tasks.value.isEmpty())
        fixture.close()
    }

    private class StartFixture(cookie: String?) {
        val dao = FakeCollectionTaskDao()
        private val repository = CollectionRepository(dao, now = { 10L })
        val sessions = XiaohongshuSessionManager(object : XiaohongshuCookieStore {
            override fun cookiesFor(url: String): String? = cookie
            override suspend fun clear() = Unit
        })
        private val useCase = StartCollectionUseCase(repository, UrlResolver(RedirectResolver { it }), sessions)
        val model = MainViewModel(repository, SavedStateHandle(), useCase)
        private val store = ViewModelStore().apply { put("main", model) }
        fun close() = store.clear()
    }
}
