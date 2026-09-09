package com.socialcommentcollector.app.platform.xiaohongshu

import java.net.URI
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.PlatformDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class XiaohongshuSessionState { UNKNOWN, LOGIN_REQUIRED, READY, EXPIRED }

interface XiaohongshuCookieStore {
    fun cookiesFor(url: String): String?
    suspend fun clear()
}

class XiaohongshuSessionManager(
    private val cookieStore: XiaohongshuCookieStore,
    private val platformDetector: PlatformDetector = PlatformDetector(),
) {
    private val mutableState = MutableStateFlow(XiaohongshuSessionState.UNKNOWN)
    val state = mutableState.asStateFlow()

    fun checkSession(): XiaohongshuSessionState {
        val hasEvidence = !cookieStore.cookiesFor(BASE_URL).isNullOrBlank()
        val next = if (!hasEvidence) {
            XiaohongshuSessionState.LOGIN_REQUIRED
        } else if (mutableState.value == XiaohongshuSessionState.READY) {
            XiaohongshuSessionState.READY
        } else {
            XiaohongshuSessionState.UNKNOWN
        }
        mutableState.value = next
        return next
    }

    fun observePage(url: String, pageAccessConfirmed: Boolean): XiaohongshuSessionState {
        val uri = runCatching { URI(url) }.getOrNull()
        val path = uri?.path.orEmpty().lowercase()
        val isLoginPage = path == "/login" || path.startsWith("/login/")
        val hasCookieEvidence = !cookieStore.cookiesFor(BASE_URL).isNullOrBlank()
        val next = when {
            uri == null || platformDetector.detect(uri) != Platform.XIAOHONGSHU -> XiaohongshuSessionState.EXPIRED
            isLoginPage -> XiaohongshuSessionState.LOGIN_REQUIRED
            pageAccessConfirmed && hasCookieEvidence -> XiaohongshuSessionState.READY
            !pageAccessConfirmed && hasCookieEvidence -> XiaohongshuSessionState.EXPIRED
            else -> XiaohongshuSessionState.LOGIN_REQUIRED
        }
        mutableState.value = next
        return next
    }

    suspend fun clearSession() {
        cookieStore.clear()
        mutableState.value = XiaohongshuSessionState.LOGIN_REQUIRED
    }

    private companion object { const val BASE_URL = "https://www.xiaohongshu.com/" }
}
