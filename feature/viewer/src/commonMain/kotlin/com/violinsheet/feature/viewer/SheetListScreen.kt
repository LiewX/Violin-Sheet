package com.violinsheet.feature.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.violinsheet.core.data.Sheet
import kotlinx.coroutines.withTimeoutOrNull

/** Home screen: the flat list of Sheets. */
@Composable
fun SheetListScreen(
    viewModel: SheetListViewModel,
    onImport: () -> Unit,
    onOpen: (Sheet) -> Unit,
    onReattach: (Sheet) -> Unit,
) {
    val items by viewModel.sheets.collectAsState()
    val message by viewModel.message.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    var renaming by remember { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    LaunchedEffect(deleted) {
        val sheet = deleted ?: return@LaunchedEffect
        // Timed from the delete rather than by SnackbarDuration, so coming back to the list (or rotating) shows
        // only what is left of the Undo window.
        val result = withTimeoutOrNull(viewModel.undoTimeLeft(sheet)) {
            snackbar.showSnackbar("Deleted \"${sheet.name}\"", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(sheet)
        viewModel.deleteBarShown(sheet)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onImport) { Text("Import PDF") }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No Sheets yet. Import a PDF to start.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(items, key = { it.sheet.id }) { item ->
                    SheetRow(
                        item,
                        onClick = { if (item.pdfMissing) onReattach(item.sheet) else onOpen(item.sheet) },
                        onRename = { renaming = item.sheet },
                        onDelete = { viewModel.delete(item.sheet) },
                    )
                }
            }
        }
    }
    renaming?.let { sheet ->
        RenameDialog(
            sheet,
            onRename = { viewModel.rename(sheet, it) },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SheetRow(item: SheetListItem, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.sheet.name) },
        supportingContent = if (item.pdfMissing) {
            { Text("PDF missing, re-import", color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "Options for ${item.sheet.name}" },
                ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun RenameDialog(sheet: Sheet, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(sheet.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Sheet") },
        text = { OutlinedTextField(name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onRename(name); onDismiss() }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
