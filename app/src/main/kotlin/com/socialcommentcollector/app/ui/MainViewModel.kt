package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.domain.StartCollectionError
import com.socialcommentcollector.app.domain.StartCollectionResult
import com.socialcommentcollector.app.domain.StartCollectionUseCase
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
        mutableState.update { it.copy(startInProgress = true, message = null) }
        viewModelScope.launch {
            applyResult(useCase(mutableState.value.url))
        }
    }

    fun onXiaohongshuPageLoaded(url: String) {
        val useCase = startCollection ?: return
        val taskId = mutableState.value.pendingTaskId ?: return
        viewModelScope.launch { applyResult(useCase.pageObserved(taskId, url)) }
    }

    fun onXiaohongshuPageUnavailable() {
        val useCase = startCollection ?: return
        val taskId = mutableState.value.pendingTaskId ?: return
        viewModelScope.launch { applyResult(useCase.sessionUnavailable(taskId)) }
    }

    fun clearXiaohongshuSession() {
        val useCase = startCollection ?: return
        viewModelScope.launch {
            useCase.clearSession()
            mutableState.update {
                it.copy(message = MainMessage.SESSION_CLEARED, pendingTaskId = null)
            }
        }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    private fun applyResult(result: StartCollectionResult) {
        val requestId = System.nanoTime()
        mutableState.update { state ->
            when (result) {
                is StartCollectionResult.Collecting -> state.copy(
                    startInProgress = false,
                    message = MainMessage.SESSION_READY,
                    navigationRequest = WebNavigationRequest(requestId, result.url),
                    pendingTaskId = null,
                )
                is StartCollectionResult.LoginRequired -> state.copy(
                    startInProgress = false,
                    message = MainMessage.LOGIN_REQUIRED,
                    navigationRequest = WebNavigationRequest(requestId, result.url),
                    pendingTaskId = result.taskId,
                )
                is StartCollectionResult.Error -> state.copy(
                    startInProgress = false,
                    message = result.reason.toMessage(),
                    pendingTaskId = null,
                )
            }
        }
    }

    private fun StartCollectionError.toMessage(): MainMessage = when (this) {
        StartCollectionError.INVALID_URL -> MainMessage.INVALID_URL
        StartCollectionError.INSECURE_URL, StartCollectionError.INSECURE_REDIRECT -> MainMessage.INSECURE_URL
        StartCollectionError.UNSUPPORTED_PLATFORM -> MainMessage.UNSUPPORTED_PLATFORM
        StartCollectionError.REDIRECT_FAILED -> MainMessage.REDIRECT_FAILED
        StartCollectionError.PLATFORM_UNAVAILABLE -> MainMessage.PLATFORM_UNAVAILABLE
        StartCollectionError.SESSION_UNAVAILABLE -> MainMessage.SESSION_UNAVAILABLE
    }
}
