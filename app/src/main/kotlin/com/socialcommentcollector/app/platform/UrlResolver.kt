package com.socialcommentcollector.app.platform

import com.socialcommentcollector.app.model.Platform
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CancellationException

fun interface RedirectResolver {
    suspend fun resolve(url: URI): URI
}

sealed interface UrlResolutionResult {
    data class Success(
        val originalUrl: URI,
        val finalUrl: URI,
        val platform: Platform,
    ) : UrlResolutionResult

    data class Failure(val reason: UrlResolutionFailure) : UrlResolutionResult
}

enum class UrlResolutionFailure {
    INVALID_URL,
    INSECURE_URL,
    UNSUPPORTED_PLATFORM,
    REDIRECT_FAILED,
    INSECURE_REDIRECT,
}

class UrlResolver(
    private val redirectResolver: RedirectResolver,
    private val platformDetector: PlatformDetector = PlatformDetector(),
) {
    suspend fun resolve(input: String): UrlResolutionResult {
        val originalUrl = input.trim().toUriOrNull()
            ?: return UrlResolutionResult.Failure(UrlResolutionFailure.INVALID_URL)

        if (!originalUrl.isHttps()) {
            return UrlResolutionResult.Failure(UrlResolutionFailure.INSECURE_URL)
        }

        val originalPlatform = platformDetector.detect(originalUrl)
        if (originalPlatform == Platform.UNKNOWN) {
            return UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM)
        }

        if (!platformDetector.isXiaohongshuShortLink(originalUrl)) {
            return UrlResolutionResult.Success(originalUrl, originalUrl, originalPlatform)
        }

        val finalUrl = try {
            redirectResolver.resolve(originalUrl)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RedirectResolutionException) {
            return UrlResolutionResult.Failure(failure.reason.toUrlResolutionFailure())
        } catch (_: Exception) {
            return UrlResolutionResult.Failure(UrlResolutionFailure.REDIRECT_FAILED)
        }

        if (!finalUrl.isHttps()) {
            return UrlResolutionResult.Failure(UrlResolutionFailure.INSECURE_REDIRECT)
        }

        val finalPlatform = platformDetector.detect(finalUrl)
        if (
            finalPlatform == Platform.UNKNOWN ||
            finalPlatform != originalPlatform ||
            platformDetector.isXiaohongshuShortLink(finalUrl)
        ) {
            return UrlResolutionResult.Failure(UrlResolutionFailure.UNSUPPORTED_PLATFORM)
        }

        return UrlResolutionResult.Success(originalUrl, finalUrl, finalPlatform)
    }

    private fun String.toUriOrNull(): URI? = try {
        URI(this).takeIf { it.isAbsolute && !it.isOpaque && it.host != null && it.userInfo == null }
    } catch (_: URISyntaxException) {
        null
    }

    private fun URI.isHttps(): Boolean =
        HTTPS_SCHEME.equals(scheme, ignoreCase = true) &&
            isAbsolute &&
            !isOpaque &&
            host != null &&
            userInfo == null

    private fun RedirectFailure.toUrlResolutionFailure(): UrlResolutionFailure = when (this) {
        RedirectFailure.INSECURE_REDIRECT -> UrlResolutionFailure.INSECURE_REDIRECT
        RedirectFailure.UNSUPPORTED_FINAL_URL -> UrlResolutionFailure.UNSUPPORTED_PLATFORM
        RedirectFailure.LOOP,
        RedirectFailure.TOO_MANY_REDIRECTS,
        RedirectFailure.INVALID_LOCATION,
        RedirectFailure.HTTP_ERROR,
        -> UrlResolutionFailure.REDIRECT_FAILED
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
    }
}
