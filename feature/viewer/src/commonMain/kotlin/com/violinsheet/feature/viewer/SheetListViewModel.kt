package com.violinsheet.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ImportResult
import com.violinsheet.core.data.Sheet
import com.violinsheet.core.data.SheetException
import com.violinsheet.core.data.SheetRepository
import com.violinsheet.core.data.UNDO_DELETE_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row of the home screen. */
data class SheetListItem(val sheet: Sheet, val pdfMissing: Boolean)

class SheetListViewModel(private val repository: SheetRepository) : ViewModel() {
    val sheets: StateFlow<List<SheetListItem>> = repository.sheets
        .map { sheets -> sheets.map { SheetListItem(it, repository.isPdfMissing(it)) } }
        .flowOn(Dispatchers.IO) // isPdfMissing checks the file system
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow<Sheet?>(null)

    /** The Sheet just deleted, while its Undo window lasts. A newer delete replaces it (and its Undo bar). */
    val deleted: StateFlow<Sheet?> = _deleted.asStateFlow()

    init {
        // Deletes whose Undo window ran out while the app was closed.
        viewModelScope.launch { repository.purgeDeleted() }
    }

    /** Runs a platform import (file picker result) in the ViewModel scope so rotation doesn't cancel it. */
    fun import(importSheet: suspend () -> ImportResult) = report("Couldn't import") {
        when (val result = importSheet()) {
            is ImportResult.Added -> null
            is ImportResult.Reattached -> result.sheets.singleOrNull()
                ?.let { "PDF reattached to \"${it.name}\"" }
                ?: "PDF reattached to ${result.sheets.size} Sheets"
        }
    }

    /** Re-import from a Sheet's "PDF missing" prompt. */
    fun reattach(reattachPdf: suspend () -> Sheet) = report("Couldn't reattach") {
        "PDF reattached to \"${reattachPdf().name}\""
    }

    fun rename(sheet: Sheet, name: String) {
        viewModelScope.launch { repository.rename(sheet, name) }
    }

    fun delete(sheet: Sheet) {
        viewModelScope.launch {
            _deleted.value = repository.delete(sheet)
            delay(UNDO_DELETE_MILLIS)
            repository.purgeDeleted()
        }
    }

    /** Time left on [sheet]'s Undo bar: it is timed from the delete, so it never outlasts the Undo window. */
    fun undoTimeLeft(sheet: Sheet): Long = repository.undoTimeLeft(sheet)

    fun undoDelete(sheet: Sheet) = report("Couldn't undo") {
        repository.undoDelete(sheet)
        null
    }

    fun deleteBarShown(sheet: Sheet) {
        _deleted.compareAndSet(sheet, null)
    }

    fun messageShown() {
        _message.value = null
    }

    private fun report(failure: String, task: suspend () -> String?) {
        viewModelScope.launch {
            _message.value = try {
                task()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SheetException) {
                e.message
            } catch (e: Exception) {
                "$failure: ${e.message}"
            }
        }
    }
}
