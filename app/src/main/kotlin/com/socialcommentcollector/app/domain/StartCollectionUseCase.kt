package com.socialcommentcollector.app.domain

import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.UrlResolutionFailure
import com.socialcommentcollector.app.platform.UrlResolutionResult
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionState
import kotlinx.coroutines.CancellationException

enum class StartCollectionError {
    EMPTY_INPUT,
    INVALID_URL,
    INSECURE_URL,
    UNSUPPORTED_PLATFORM,
    REDIRECT_FAILED,
    INSECURE_REDIRECT,
    JIKE_NOT_IMPLEMENTED,
    SESSION_ERROR,
    TASK_CREATION_FAILED,
}

sealed interface StartCollectionResult {
    data class TaskReady(
        val taskId: String,
        val url: String,
        val platform: Platform = Platform.XIAOHONGSHU,
        val sessionState: XiaohongshuSessionState = XiaohongshuSessionState.READY,
    ) : StartCollectionResult

    data class LoginRequired(
        val originalUrl: String,
        val url: String,
        val platform: Platform = Platform.XIAOHONGSHU,
        val sessionState: XiaohongshuSessionState,
    ) : StartCollectionResult

    data class Error(
        val reason: StartCollectionError,
        val platform: Platform = Platform.UNKNOWN,
        val sessionState: XiaohongshuSessionState = XiaohongshuSessionState.UNKNOWN,
    ) : StartCollectionResult
}

class StartCollectionUseCase(
    private val repository: CollectionRepository,
    private val urlResolver: UrlResolver,
    private val sessions: XiaohongshuSessionManager,
) {
    suspend operator fun invoke(input: String): StartCollectionResult {
        if (input.isBlank()) {
            return StartCollectionResult.Error(StartCollectionError.EMPTY_INPUT)
        }

        val resolved = urlResolver.resolve(input)
        if (resolved is UrlResolutionResult.Failure) {
            return StartCollectionResult.Error(resolved.reason.toStartError())
        }
        resolved as UrlResolutionResult.Success

        if (resolved.platform == Platform.JIKE) {
            return StartCollectionResult.Error(
                StartCollectionError.JIKE_NOT_IMPLEMENTED,
                platform = Platform.JIKE,
            )
        }
        if (resolved.platform != Platform.XIAOHONGSHU) {
            return StartCollectionResult.Error(StartCollectionError.UNSUPPORTED_PLATFORM)
        }

        val sessionState = checkSession()
            ?: return StartCollectionResult.Error(
                StartCollectionError.SESSION_ERROR,
                platform = Platform.XIAOHONGSHU,
            )

        return if (sessionState == XiaohongshuSessionState.READY) {
            createQueuedTask(resolved.originalUrl.toString(), resolved.finalUrl.toString())
        } else {
            StartCollectionResult.LoginRequired(
                originalUrl = resolved.originalUrl.toString(),
                url = resolved.finalUrl.toString(),
                sessionState = sessionState,
            )
        }
    }

    suspend fun resumeAfterPage(
        originalUrl: String,
        resolvedUrl: String,
        observedUrl: String,
        pageAccessConfirmed: Boolean,
    ): StartCollectionResult {
        val sessionState = try {
            sessions.observePage(observedUrl, pageAccessConfirmed)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return StartCollectionResult.Error(
                StartCollectionError.SESSION_ERROR,
                platform = Platform.XIAOHONGSHU,
            )
        }

        return if (sessionState == XiaohongshuSessionState.READY) {
            createQueuedTask(originalUrl, resolvedUrl)
        } else {
            StartCollectionResult.LoginRequired(
                originalUrl = originalUrl,
                url = resolvedUrl,
                sessionState = sessionState,
            )
        }
    }

    suspend fun clearSession() = sessions.clearSession()

    fun sessionUnavailable(): StartCollectionResult = StartCollectionResult.Error(
        StartCollectionError.SESSION_ERROR,
        platform = Platform.XIAOHONGSHU,
        sessionState = XiaohongshuSessionState.EXPIRED,
    )

    private fun checkSession(): XiaohongshuSessionState? = try {
        sessions.checkSession()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun createQueuedTask(originalUrl: String, resolvedUrl: String): StartCollectionResult = try {
        val task = repository.createTask(originalUrl, Platform.XIAOHONGSHU)
        StartCollectionResult.TaskReady(task.id, resolvedUrl)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        StartCollectionResult.Error(
            StartCollectionError.TASK_CREATION_FAILED,
            platform = Platform.XIAOHONGSHU,
            sessionState = XiaohongshuSessionState.READY,
        )
    }

    private fun UrlResolutionFailure.toStartError(): StartCollectionError = when (this) {
        UrlResolutionFailure.INVALID_URL -> StartCollectionError.INVALID_URL
        UrlResolutionFailure.INSECURE_URL -> StartCollectionError.INSECURE_URL
        UrlResolutionFailure.UNSUPPORTED_PLATFORM -> StartCollectionError.UNSUPPORTED_PLATFORM
        UrlResolutionFailure.REDIRECT_FAILED -> StartCollectionError.REDIRECT_FAILED
        UrlResolutionFailure.INSECURE_REDIRECT -> StartCollectionError.INSECURE_REDIRECT
    }
}
