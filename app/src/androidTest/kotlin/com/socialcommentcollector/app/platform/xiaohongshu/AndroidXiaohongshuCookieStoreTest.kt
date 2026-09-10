package com.socialcommentcollector.app.platform.xiaohongshu

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidXiaohongshuCookieStoreTest {
    @Test
    fun clearRemovesWebViewCookiesForCurrentV01Policy() {
        val cookieManager = CookieManager.getInstance()
        val cookieWritten = CountDownLatch(1)
        cookieManager.setCookie(TEST_URL, "sessionid=instrumented-test") {
            cookieWritten.countDown()
        }
        assertTrue(cookieWritten.await(5, TimeUnit.SECONDS))

        runBlocking { AndroidXiaohongshuCookieStore(cookieManager).clear() }

        assertNull(cookieManager.getCookie(TEST_URL))
    }

    private companion object {
        const val TEST_URL = "https://www.xiaohongshu.com/"
    }
}
