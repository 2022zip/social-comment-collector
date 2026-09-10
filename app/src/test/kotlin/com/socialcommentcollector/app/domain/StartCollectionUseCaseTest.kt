package com.socialcommentcollector.app.domain

import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.CollectionTaskDao
import com.socialcommentcollector.app.data.CollectionTaskEntity
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import com.socialcommentcollector.app.data.FakeCommentDao
import com.socialcommentcollector.app.data.ImmediateTransactionRunner
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.RedirectResolver
import com.socialcommentcollector.app.platform.ShareInputResolver
import com.socialcommentcollector.app.platform.ShareTextUrlExtractor
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCollectionUseCaseTest {
    @Test
    fun `ready Xiaohongshu session creates one queued task`() = runTest {
        val fixture = Fixture(cookie = "sessionid=valid")
        fixture.sessions.observePage("https://www.xiaohongshu.com/explore", true)

        val result = fixture.useCase("https://www.xiaohongshu.com/explore/abc")

        assertTrue(result is StartCollectionResult.TaskReady)
        val task = fixture.dao.tasks.value.single()
        assertEquals(CollectionStatus.QUEUED, task.status)
        assertEquals(Platform.XIAOHONGSHU, task.platform)
    }

    @Test
    fun `full Xiaohongshu share text resolves before session and task creation`() = runTest {
        val final = URI(
            "https://www.xiaohongshu.com/explore/note-id?xsec_token=fixture&xsec_source=pc_feed",
        )
        val fixture = Fixture(cookie = "sessionid=valid", redirect = { final })
        fixture.sessions.observePage(final.toString(), true)

        val result = fixture.useCase(
            "如果我能像你那么潇洒就好了 ... https://xhslink.cn/o/5teLYHz60xA 保留口令，直达【小红书】围观~",
        )

        assertTrue(result is StartCollectionResult.TaskReady)
        result as StartCollectionResult.TaskReady
        assertEquals(final.toString(), result.url)
        assertEquals("https://xhslink.cn/o/5teLYHz60xA", fixture.dao.tasks.value.single().originalUrl)
    }

    @Test
    fun `login required returns navigation data without creating a task`() = runTest {
        val fixture = Fixture(cookie = null)

        val result = fixture.useCase("https://www.xiaohongshu.com/explore/abc")

        assertTrue(result is StartCollectionResult.LoginRequired)
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun `successful page recheck creates the queued task after login`() = runTest {
        val fixture = Fixture(cookie = "sessionid=valid")

        val result = fixture.useCase.resumeAfterPage(
            originalUrl = "https://www.xiaohongshu.com/explore/abc",
            resolvedUrl = "https://www.xiaohongshu.com/explore/abc",
            observedUrl = "https://www.xiaohongshu.com/explore/abc",
            pageAccessConfirmed = true,
        )

        assertTrue(result is StartCollectionResult.TaskReady)
        assertEquals(CollectionStatus.QUEUED, fixture.dao.tasks.value.single().status)
    }

    @Test
    fun `empty invalid unsupported and Jike inputs return distinct errors`() = runTest {
        val fixture = Fixture(cookie = null)

        assertError(StartCollectionError.EMPTY_INPUT, fixture.useCase(" \n "))
        assertError(StartCollectionError.INVALID_URL, fixture.useCase("not a url"))
        assertError(StartCollectionError.UNSUPPORTED_PLATFORM, fixture.useCase("https://example.com/a"))
        assertError(StartCollectionError.JIKE_NOT_IMPLEMENTED, fixture.useCase("https://web.okjike.com/u/a"))
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun `insecure and redirect failures are mapped without creating tasks`() = runTest {
        val fixture = Fixture(cookie = null, redirect = { error("offline") })

        assertError(StartCollectionError.INSECURE_URL, fixture.useCase("http://xiaohongshu.com/a"))
        assertError(StartCollectionError.REDIRECT_FAILED, fixture.useCase("https://xhslink.com/a"))
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun `session check failure returns typed error without creating a task`() = runTest {
        val fixture = Fixture(cookieLookup = { error("CookieManager unavailable") })

        assertError(
            StartCollectionError.SESSION_ERROR,
            fixture.useCase("https://www.xiaohongshu.com/explore/abc"),
        )
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun `repository failure returns typed task creation error`() = runTest {
        val sessions = sessionManager(cookieLookup = { "sessionid=valid" })
        sessions.observePage("https://www.xiaohongshu.com/explore", true)
        val useCase = StartCollectionUseCase(
            CollectionRepository(ThrowingTaskDao(), FakeCommentDao(), ImmediateTransactionRunner),
            ShareInputResolver(ShareTextUrlExtractor(), UrlResolver(RedirectResolver { it })),
            sessions,
        )

        assertError(
            StartCollectionError.TASK_CREATION_FAILED,
            useCase("https://www.xiaohongshu.com/explore/abc"),
        )
    }

    private fun assertError(expected: StartCollectionError, result: StartCollectionResult) {
        assertTrue(result is StartCollectionResult.Error)
        assertEquals(expected, (result as StartCollectionResult.Error).reason)
    }

    private class Fixture(
        cookie: String? = null,
        cookieLookup: () -> String? = { cookie },
        redirect: suspend (URI) -> URI = { it },
    ) {
        val dao = FakeCollectionTaskDao()
        private val repository = CollectionRepository(dao, FakeCommentDao(), ImmediateTransactionRunner, now = { 10L })
        val sessions = sessionManager(cookieLookup)
        val useCase = StartCollectionUseCase(
            repository,
            ShareInputResolver(ShareTextUrlExtractor(), UrlResolver(RedirectResolver(redirect))),
            sessions,
        )
    }
}

private fun sessionManager(cookieLookup: () -> String?) =
    XiaohongshuSessionManager(object : XiaohongshuCookieStore {
        override fun cookiesFor(url: String): String? = cookieLookup()
        override suspend fun clear() = Unit
    })

private class ThrowingTaskDao : CollectionTaskDao {
    override suspend fun insert(task: CollectionTaskEntity) = error("database unavailable")
    override suspend fun update(task: CollectionTaskEntity) = Unit
    override suspend fun getById(id: String): CollectionTaskEntity? = null
    override suspend fun updateActualCount(id: String, count: Int, updatedAt: Long) = Unit
    override fun observeAll(): Flow<List<CollectionTaskEntity>> = flowOf(emptyList())
    override suspend fun deleteById(id: String) = Unit
}
