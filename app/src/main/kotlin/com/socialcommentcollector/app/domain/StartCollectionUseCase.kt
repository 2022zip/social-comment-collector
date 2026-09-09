package com.socialcommentcollector.app.domain

import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.model.CollectionStatus
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.UrlResolutionFailure
import com.socialcommentcollector.app.platform.UrlResolutionResult
import com.socialcommentcollector.app.platform.UrlResolver
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionManager
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionState
import kotlinx.coroutines.CancellationException

enum class StartCollectionError {
    INVALID_URL, INSECURE_URL, UNSUPPORTED_PLATFORM, REDIRECT_FAILED,
    INSECURE_REDIRECT, PLATFORM_UNAVAILABLE, SESSION_UNAVAILABLE,
}

sealed interface StartCollectionResult {
    data class Collecting(val taskId: String, val url: String) : StartCollectionResult
    data class LoginRequired(val taskId: String, val url: String) : StartCollectionResult
    data class Error(val reason: StartCollectionError) : StartCollectionResult
}

class StartCollectionUseCase(
    private val repository: CollectionRepository,
    private val urlResolver: UrlResolver,
    private val sessions: XiaohongshuSessionManager,
) {
    suspend operator fun invoke(input: String): StartCollectionResult {
        val resolved = urlResolver.resolve(input)
        if (resolved is UrlResolutionResult.Failure) {
            return StartCollectionResult.Error(resolved.reason.toStartError())
        }
        resolved as UrlResolutionResult.Success
        if (resolved.platform == Platform.JIKE) {
            return StartCollectionResult.Error(StartCollectionError.PLATFORM_UNAVAILABLE)
        }
        if (resolved.platform != Platform.XIAOHONGSHU) {
            return StartCollectionResult.Error(StartCollectionError.UNSUPPORTED_PLATFORM)
        }

        val task = repository.createTask(resolved.originalUrl.toString(), resolved.platform)
        val sessionState = try {
            sessions.checkSession()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            repository.updateStatus(task.id, CollectionStatus.FAILED, "Session unavailable")
            return StartCollectionResult.Error(StartCollectionError.SESSION_UNAVAILABLE)
        }
        return when (sessionState) {
            XiaohongshuSessionState.READY -> {
                repository.updateStatus(task.id, CollectionStatus.COLLECTING)
                StartCollectionResult.Collecting(task.id, resolved.finalUrl.toString())
            }
            XiaohongshuSessionState.UNKNOWN,
            XiaohongshuSessionState.LOGIN_REQUIRED,
            XiaohongshuSessionState.EXPIRED,
            -> StartCollectionResult.LoginRequired(task.id, resolved.finalUrl.toString())
        }
    }

    suspend fun pageObserved(taskId: String, url: String): StartCollectionResult {
        return when (sessions.observePage(url, pageAccessConfirmed = true)) {
            XiaohongshuSessionState.READY -> {
                repository.updateStatus(taskId, CollectionStatus.COLLECTING)
                StartCollectionResult.Collecting(taskId, url)
            }
            XiaohongshuSessionState.UNKNOWN,
            XiaohongshuSessionState.LOGIN_REQUIRED,
            XiaohongshuSessionState.EXPIRED,
            -> StartCollectionResult.LoginRequired(taskId, url)
        }
    }

    suspend fun clearSession() = sessions.clearSession()

    suspend fun sessionUnavailable(taskId: String): StartCollectionResult {
        repository.updateStatus(taskId, CollectionStatus.FAILED, "Session unavailable")
        return StartCollectionResult.Error(StartCollectionError.SESSION_UNAVAILABLE)
    }

    private fun UrlResolutionFailure.toStartError(): StartCollectionError = when (this) {
        UrlResolutionFailure.INVALID_URL -> StartCollectionError.INVALID_URL
        UrlResolutionFailure.INSECURE_URL -> StartCollectionError.INSECURE_URL
        UrlResolutionFailure.UNSUPPORTED_PLATFORM -> StartCollectionError.UNSUPPORTED_PLATFORM
        UrlResolutionFailure.REDIRECT_FAILED -> StartCollectionError.REDIRECT_FAILED
        UrlResolutionFailure.INSECURE_REDIRECT -> StartCollectionError.INSECURE_REDIRECT
    }
}
