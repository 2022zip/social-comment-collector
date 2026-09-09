package com.socialcommentcollector.app.ui

import com.socialcommentcollector.app.data.CollectionTaskEntity

data class MainUiState(
    val url: String = "",
    val tasks: List<CollectionTaskEntity> = emptyList(),
    val storageUnavailable: Boolean = false,
    val startInProgress: Boolean = false,
    val message: MainMessage? = null,
    val navigationRequest: WebNavigationRequest? = null,
    val pendingTaskId: String? = null,
) {
    val canUseUrlActions: Boolean get() = url.isNotBlank() && !startInProgress
}

data class WebNavigationRequest(val id: Long, val url: String)

enum class MainMessage {
    INVALID_URL, INSECURE_URL, UNSUPPORTED_PLATFORM, REDIRECT_FAILED,
    PLATFORM_UNAVAILABLE, LOGIN_REQUIRED, SESSION_READY, SESSION_CLEARED, SESSION_UNAVAILABLE,
}
