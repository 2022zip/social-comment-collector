package com.socialcommentcollector.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.socialcommentcollector.app.MainDispatcherRule
import com.socialcommentcollector.app.data.CollectionRepository
import com.socialcommentcollector.app.data.FakeCollectionTaskDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun inputAndClearUpdateStateAndSavedInput() = runTest {
        val handle = SavedStateHandle()
        val model = MainViewModel(CollectionRepository(FakeCollectionTaskDao()), handle)
        val store = ViewModelStore().apply { put("main", model) }
        try {
            assertFalse(model.uiState.value.canUseUrlActions)
            model.updateUrl("  https://example.com/post  ")
            assertEquals("  https://example.com/post  ", model.uiState.value.url)
            assertTrue(model.uiState.value.canUseUrlActions)
            model.clearUrl()
            assertEquals("", model.uiState.value.url)
            assertEquals("", handle.get<String>("url"))
            assertFalse(model.uiState.value.canUseUrlActions)
        } finally { store.clear() }
    }

    @Test
    fun whitespaceDoesNotEnableActionsAndSavedInputIsRestored() = runTest {
        val model = MainViewModel(
            CollectionRepository(FakeCollectionTaskDao()),
            SavedStateHandle(mapOf("url" to "saved input")),
        )
        val store = ViewModelStore().apply { put("main", model) }
        try {
            assertEquals("saved input", model.uiState.value.url)
            model.updateUrl(" \n ")
            assertFalse(model.uiState.value.canUseUrlActions)
        } finally { store.clear() }
    }

    @Test
    fun repositoryTaskUpdatesReachUiWithoutOverwritingInput() = runTest {
        val dao = FakeCollectionTaskDao()
        val repository = CollectionRepository(dao)
        val model = MainViewModel(repository, SavedStateHandle())
        val store = ViewModelStore().apply { put("main", model) }
        try {
            model.updateUrl("keep me")
            val task = repository.createTaskDraft("another input")
            dao.insert(task)
            runCurrent()
            assertEquals(listOf(task), model.uiState.value.tasks)
            assertEquals("keep me", model.uiState.value.url)
        } finally { store.clear() }
    }
}
