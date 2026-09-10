package com.socialcommentcollector.app.platform

import com.socialcommentcollector.app.model.Platform
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class RedirectResponse(val statusCode: Int, val location: String?)

fun interface RedirectTransport {
    suspend fun execute(url: URI): RedirectResponse
}

enum class RedirectFailure {
    LOOP,
    TOO_MANY_REDIRECTS,
    INSECURE_REDIRECT,
    INVALID_LOCATION,
    UNSUPPORTED_FINAL_URL,
    HTTP_ERROR,
}

class RedirectResolutionException(
    val reason: RedirectFailure,
) : Exception(reason.name)

class HttpRedirectResolver(
    private val transport: RedirectTransport = UrlConnectionRedirectTransport(),
    private val platformDetector: PlatformDetector = PlatformDetector(),
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) : RedirectResolver {
    init {
        require(maxRedirects >= 0) { "maxRedirects must not be negative" }
    }

    override suspend fun resolve(url: URI): URI {
        val expectedPlatform = platformDetector.detect(url)
        if (expectedPlatform != Platform.XIAOHONGSHU) {
            throw RedirectResolutionException(RedirectFailure.UNSUPPORTED_FINAL_URL)
        }

        var current = url
        var followedRedirects = 0
        val visited = mutableSetOf<URI>()

        while (true) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(current.normalize())) {
                throw RedirectResolutionException(RedirectFailure.LOOP)
            }

            val response = transport.execute(current)
            if (response.statusCode !in REDIRECT_CODES) {
                if (response.statusCode !in SUCCESS_CODES) {
                    throw RedirectResolutionException(RedirectFailure.HTTP_ERROR)
                }
                if (
                    platformDetector.detect(current) != expectedPlatform ||
                    platformDetector.isXiaohongshuShortLink(current)
                ) {
                    throw RedirectResolutionException(RedirectFailure.UNSUPPORTED_FINAL_URL)
                }
                return current
            }

            if (followedRedirects >= maxRedirects) {
                throw RedirectResolutionException(RedirectFailure.TOO_MANY_REDIRECTS)
            }

            val next = resolveLocation(current, response.location)
            if (!HTTPS_SCHEME.equals(next.scheme, ignoreCase = true)) {
                throw RedirectResolutionException(RedirectFailure.INSECURE_REDIRECT)
            }
            if (platformDetector.detect(next) != expectedPlatform) {
                throw RedirectResolutionException(RedirectFailure.UNSUPPORTED_FINAL_URL)
            }

            current = next
            followedRedirects += 1
        }
    }

    private fun resolveLocation(current: URI, location: String?): URI {
        if (location.isNullOrBlank()) {
            throw RedirectResolutionException(RedirectFailure.INVALID_LOCATION)
        }
        return try {
            current.resolve(URI(location)).takeIf {
                it.isAbsolute && !it.isOpaque && it.host != null && it.userInfo == null
            } ?: throw RedirectResolutionException(RedirectFailure.INVALID_LOCATION)
        } catch (_: URISyntaxException) {
            throw RedirectResolutionException(RedirectFailure.INVALID_LOCATION)
        }
    }

    private companion object {
        const val DEFAULT_MAX_REDIRECTS = 5
        const val HTTPS_SCHEME = "https"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val SUCCESS_CODES = 200..399
    }
}

private class UrlConnectionRedirectTransport : RedirectTransport {
    override suspend fun execute(url: URI): RedirectResponse = withContext(Dispatchers.IO) {
        val connection = url.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            RedirectResponse(connection.responseCode, connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val USER_AGENT = "SocialCommentCollector/0.1"
    }
}
