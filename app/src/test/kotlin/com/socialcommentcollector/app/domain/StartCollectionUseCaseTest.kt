package com.socialcommentcollector.app.domain

import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.platform.RedirectResolver
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuCookieStore
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCollectionUseCaseTest {
    @Test
    fun readyXiaohongshuSessionCreatesTaskAndStartsPreparation() = runTest {
        val fixture = Fixture(cookie = "sessionid=valid")
        fixture.sessions.observePage("https://www.xiaohongshu.com/explore", true)

        val result = fixture.useCase("https://www.xiaohongshu.com/explore/abc")

        assertTrue(result is StartCollectionResult.Collecting)
        assertEquals(CollectionStatus.COLLECTING, fixture.dao.tasks.value.single().status)
    }

    @Test
    fun loginRequiredCreatesQueuedTaskAndRequestsWebView() = runTest {
        val fixture = Fixture(cookie = null)

        val result = fixture.useCase("https://www.xiaohongshu.com/explore/abc")

        assertTrue(result is StartCollectionResult.LoginRequired)
        assertEquals(CollectionStatus.QUEUED, fixture.dao.tasks.value.single().status)
    }

    @Test
    fun invalidUnsupportedAndJikeInputsDoNotCreateTasks() = runTest {
        val fixture = Fixture(cookie = null)

        assertEquals(StartCollectionError.INVALID_URL, (fixture.useCase("not a url") as StartCollectionResult.Error).reason)
        assertEquals(StartCollectionError.UNSUPPORTED_PLATFORM, (fixture.useCase("https://example.com/a") as StartCollectionResult.Error).reason)
        assertEquals(StartCollectionError.PLATFORM_UNAVAILABLE, (fixture.useCase("https://web.okjike.com/u/a") as StartCollectionResult.Error).reason)
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun insecureAndRedirectFailureAreMappedWithoutCrashing() = runTest {
        val fixture = Fixture(cookie = null, redirect = { error("offline") })

        assertEquals(StartCollectionError.INSECURE_URL, (fixture.useCase("http://xiaohongshu.com/a") as StartCollectionResult.Error).reason)
        assertEquals(StartCollectionError.REDIRECT_FAILED, (fixture.useCase("https://xhslink.com/a") as StartCollectionResult.Error).reason)
        assertTrue(fixture.dao.tasks.value.isEmpty())
    }

    @Test
    fun unavailableSessionMarksPreviouslyQueuedTaskFailed() = runTest {
        val fixture = Fixture(cookie = null)
        val pending = fixture.useCase("https://www.xiaohongshu.com/explore/abc") as StartCollectionResult.LoginRequired

        val result = fixture.useCase.sessionUnavailable(pending.taskId)

        assertEquals(StartCollectionError.SESSION_UNAVAILABLE, (result as StartCollectionResult.Error).reason)
        assertEquals(CollectionStatus.FAILED, fixture.dao.tasks.value.single().status)
    }

    private class Fixture(
        cookie: String?,
        redirect: suspend (URI) -> URI = { it },
    ) {
        val dao = FakeCollectionTaskDao()
        private val repository = CollectionRepository(dao, now = { 10L })
        val sessions = XiaohongshuSessionManager(object : XiaohongshuCookieStore {
            override fun cookiesFor(url: String): String? = cookie
            override suspend fun clear() = Unit
        })
        val useCase = StartCollectionUseCase(repository, UrlResolver(RedirectResolver(redirect)), sessions)
    }
}
