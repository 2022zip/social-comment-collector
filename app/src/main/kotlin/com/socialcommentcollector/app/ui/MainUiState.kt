package com.socialcommentcollector.app.ui

import com.socialcommentcollector.app.data.CollectionTaskEntity

data class MainUiState(
    val url: String = "",
    val tasks: List<CollectionTaskEntity> = emptyList(),
    val storageUnavailable: Boolean = false,
) {
    val canUseUrlActions: Boolean get() = url.isNotBlank()
}
