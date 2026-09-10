package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.domain.StartCollectionError
import com.socialcommentcollector.app.domain.StartCollectionResult
import com.socialcommentcollector.app.domain.StartCollectionUseCase
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    repository: CollectionRepository,
    private val savedState: SavedStateHandle,
    private val startCollection: StartCollectionUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState(url = savedState["url"] ?: ""))
    private val startGuard = AtomicBoolean(false)
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTasks()
                .catch { mutableState.update { it.copy(storageUnavailable = true) } }
                .collect { tasks -> mutableState.update { it.copy(tasks = tasks) } }
        }
    }

    fun updateUrl(value: String) {
        savedState["url"] = value
        mutableState.update { it.copy(url = value) }
    }

    fun clearUrl() = updateUrl("")

    fun startCollection() {
        val useCase = startCollection ?: return
        if (!startGuard.compareAndSet(false, true)) return
        val input = mutableState.value.url
        mutableState.update { it.copy(startInProgress = true, message = null) }
        viewModelScope.launch {
            try {
                applyResult(useCase(input))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                applyResult(StartCollectionResult.Error(StartCollectionError.SESSION_ERROR))
            } finally {
                startGuard.set(false)
                mutableState.update { it.copy(startInProgress = false) }
            }
        }
    }

    fun onXiaohongshuPageLoaded(url: String) {
        val useCase = startCollection ?: return
        val pending = mutableState.value.pendingRequest ?: return
        if (!startGuard.compareAndSet(false, true)) return
        mutableState.update { it.copy(startInProgress = true) }
        viewModelScope.launch {
            try {
                applyResult(
                    useCase.resumeAfterPage(
                        originalUrl = pending.originalUrl,
                        resolvedUrl = pending.resolvedUrl,
                        observedUrl = url,
                        pageAccessConfirmed = true,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                applyResult(useCase.sessionUnavailable())
            } finally {
                startGuard.set(false)
                mutableState.update { it.copy(startInProgress = false) }
            }
        }
    }

    fun onXiaohongshuPageUnavailable() {
        val useCase = startCollection ?: return
        if (mutableState.value.pendingRequest == null) return
        applyResult(useCase.sessionUnavailable())
    }

    fun clearXiaohongshuSession() {
        val useCase = startCollection ?: return
        viewModelScope.launch {
            try {
                useCase.clearSession()
                mutableState.update {
                    it.copy(
                        message = MainMessage.SESSION_CLEARED,
                        sessionState = XiaohongshuSessionState.LOGIN_REQUIRED,
                        loginRequired = false,
                        pendingRequest = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.update { it.copy(message = MainMessage.SESSION_ERROR) }
            }
        }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    private fun applyResult(result: StartCollectionResult) {
        mutableState.update { state ->
            when (result) {
                is StartCollectionResult.TaskReady -> state.copy(
                    startInProgress = false,
                    resolvedPlatform = result.platform,
                    sessionState = result.sessionState,
                    loginRequired = false,
                    message = MainMessage.PREPARING_COLLECTION,
                    navigationRequest = WebNavigationRequest(System.nanoTime(), result.url),
                    pendingRequest = null,
                    currentTaskId = result.taskId,
                )
                is StartCollectionResult.LoginRequired -> state.copy(
                    startInProgress = false,
                    resolvedPlatform = result.platform,
                    sessionState = result.sessionState,
                    loginRequired = true,
                    message = MainMessage.LOGIN_REQUIRED,
                    navigationRequest = WebNavigationRequest(System.nanoTime(), result.url),
                    pendingRequest = PendingCollectionRequest(result.originalUrl, result.url),
                    currentTaskId = null,
                )
                is StartCollectionResult.Error -> state.copy(
                    startInProgress = false,
                    resolvedPlatform = result.platform,
                    sessionState = result.sessionState,
                    loginRequired = false,
                    message = result.reason.toMessage(),
                    pendingRequest = null,
                    currentTaskId = null,
                )
            }
        }
    }

    private fun StartCollectionError.toMessage(): MainMessage = when (this) {
        StartCollectionError.EMPTY_INPUT -> MainMessage.EMPTY_INPUT
        StartCollectionError.INVALID_URL -> MainMessage.INVALID_URL
        StartCollectionError.INSECURE_URL,
        StartCollectionError.INSECURE_REDIRECT,
        -> MainMessage.INSECURE_URL
        StartCollectionError.UNSUPPORTED_PLATFORM -> MainMessage.UNSUPPORTED_PLATFORM
        StartCollectionError.REDIRECT_FAILED -> MainMessage.REDIRECT_FAILED
        StartCollectionError.JIKE_NOT_IMPLEMENTED -> MainMessage.JIKE_NOT_IMPLEMENTED
        StartCollectionError.SESSION_ERROR -> MainMessage.SESSION_ERROR
        StartCollectionError.TASK_CREATION_FAILED -> MainMessage.TASK_CREATION_FAILED
    }
}
