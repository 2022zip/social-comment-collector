package com.socialcommentcollector.app.ui

import com.socialcommentcollector.app.data.CollectionTaskEntity
import com.socialcommentcollector.app.model.Platform
import com.socialcommentcollector.app.platform.xiaohongshu.XiaohongshuSessionState

data class MainUiState(
    val url: String = "",
    val tasks: List<CollectionTaskEntity> = emptyList(),
    val storageUnavailable: Boolean = false,
    val startInProgress: Boolean = false,
    val resolvedPlatform: Platform = Platform.UNKNOWN,
    val sessionState: XiaohongshuSessionState = XiaohongshuSessionState.UNKNOWN,
    val loginRequired: Boolean = false,
    val message: MainMessage? = null,
    val navigationRequest: WebNavigationRequest? = null,
    val pendingRequest: PendingCollectionRequest? = null,
    val currentTaskId: String? = null,
) {
    val canUseUrlActions: Boolean get() = url.isNotBlank() && !startInProgress
}

data class WebNavigationRequest(val id: Long, val url: String)

data class PendingCollectionRequest(
    val originalUrl: String,
    val resolvedUrl: String,
)

enum class MainMessage {
    EMPTY_INPUT,
    INVALID_URL,
    INSECURE_URL,
    UNSUPPORTED_PLATFORM,
    REDIRECT_FAILED,
    JIKE_NOT_IMPLEMENTED,
    LOGIN_REQUIRED,
    PREPARING_COLLECTION,
    SESSION_CLEARED,
    SESSION_ERROR,
    TASK_CREATION_FAILED,
}
