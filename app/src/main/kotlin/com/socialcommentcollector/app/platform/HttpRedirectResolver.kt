package com.socialcommentcollector.app.platform

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpRedirectResolver : RedirectResolver {
    override suspend fun resolve(url: URI): URI = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "HEAD"
            try {
                if (connection.responseCode !in REDIRECT_CODES) return@withContext current
                val location = connection.getHeaderField("Location")
                    ?: error("Redirect response has no Location")
                current = current.resolve(location)
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 10_000
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
