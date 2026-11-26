package com.violinsheet.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.Sheet
import com.violinsheet.core.data.SheetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SheetListViewModel(repository: SheetRepository) : ViewModel() {
    val sheets: StateFlow<List<Sheet>> =
        repository.sheets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Runs a platform import (file picker result) in the ViewModel scope so rotation doesn't cancel it. */
    fun runImport(importSheet: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                importSheet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Couldn't import: ${e.message}"
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
