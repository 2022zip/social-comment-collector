package com.socialcommentcollector.app.platform.xiaohongshu

import android.webkit.CookieManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidXiaohongshuCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : XiaohongshuCookieStore {
    override fun cookiesFor(url: String): String? = cookieManager.getCookie(url)

    override suspend fun clear() {
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies { continuation.resume(Unit) }
        }
        cookieManager.flush()
    }
}
