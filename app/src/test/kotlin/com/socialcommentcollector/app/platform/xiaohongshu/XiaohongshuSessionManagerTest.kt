package com.socialcommentcollector.app.platform.xiaohongshu

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XiaohongshuSessionManagerTest {
    @Test
    fun noCookieEvidenceRequiresLogin() = runTest {
        val cookies = FakeCookieStore(null)
        val manager = XiaohongshuSessionManager(cookies)

        assertEquals(XiaohongshuSessionState.LOGIN_REQUIRED, manager.checkSession())
    }

    @Test
    fun cookiesAloneDoNotClaimReady() = runTest {
        val manager = XiaohongshuSessionManager(FakeCookieStore("sessionid=possible"))

        assertEquals(XiaohongshuSessionState.UNKNOWN, manager.checkSession())
    }

    @Test
    fun cookieEvidenceAndSuccessfulPlatformPageAreRequiredForReady() = runTest {
        val manager = XiaohongshuSessionManager(FakeCookieStore("sessionid=possible"))

        assertEquals(
            XiaohongshuSessionState.READY,
            manager.observePage("https://www.xiaohongshu.com/explore", pageAccessConfirmed = true),
        )
    }

    @Test
    fun loginAndExpiredSignalsOverrideCookieEvidence() = runTest {
        val manager = XiaohongshuSessionManager(FakeCookieStore("sessionid=possible"))

        assertEquals(
            XiaohongshuSessionState.LOGIN_REQUIRED,
            manager.observePage("https://www.xiaohongshu.com/login", pageAccessConfirmed = false),
        )
        assertEquals(
            XiaohongshuSessionState.EXPIRED,
            manager.observePage("https://www.xiaohongshu.com/explore", pageAccessConfirmed = false),
        )
    }

    @Test
    fun clearRemovesWebViewSessionEvidence() = runTest {
        val cookies = FakeCookieStore("sessionid=possible")
        val manager = XiaohongshuSessionManager(cookies)

        manager.clearSession()

        assertFalse(cookies.hasCookies())
        assertEquals(XiaohongshuSessionState.LOGIN_REQUIRED, manager.state.value)
    }
}

private class FakeCookieStore(private var cookies: String?) : XiaohongshuCookieStore {
    override fun cookiesFor(url: String): String? = cookies
    override suspend fun clear() { cookies = null }
    fun hasCookies(): Boolean = !cookies.isNullOrBlank()
}
