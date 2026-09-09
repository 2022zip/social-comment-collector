package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialcommentcollector.app.data.CollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    repository: CollectionRepository,
    private val savedState: SavedStateHandle,
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
}
