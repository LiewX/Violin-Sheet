package com.violinsheet.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.data.settings.PaletteSetting
import com.violinsheet.core.data.settings.SettingsStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** "#RRGGBB" for a colour the user made, or wherever a colour has no factory name. */
fun hexName(argb: Int) = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** What the colour panel under the swatches is doing: adding a colour, or editing [Edit.colour]. */
private sealed interface ColourPanel {
    data object Add : ColourPanel
    data class Edit(val colour: Int) : ColourPanel
}

/**
 * A Palette's control: its swatches in a wrapping row, then + to add one. Tapping a swatch opens the picker to edit it
 * (Save replaces it everywhere) or delete it (Undo in [snackbar]; the last colour has no Delete). Hold and drag
 * reorders at any time. A Sheet starts on the first colour.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
internal fun PaletteEditor(setting: PaletteSetting, store: SettingsStore, snackbar: SnackbarHostState) {
    val loaded by remember(setting) { store.observe(setting).map(::PaletteEdit) }.collectAsState(null)
    val edit = loaded ?: return
    val latest by rememberUpdatedState(edit)
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<ColourPanel?>(null) }
    val drag = remember { SwatchDrag() }

    fun save(new: PaletteEdit) {
        if (new != latest) store.set(setting, new.colours)
    }

    fun delete(colour: Int) {
        val index = latest.colours.indexOf(colour)
        save(latest.deleted(colour))
        panel = null
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(ColourDeleted(colour, "Colour deleted from the ${setting.title.lowercase()}"))
            // Only the deletion is undone: what was added, moved or edited while the bar showed stays.
            if (result == SnackbarResult.ActionPerformed) save(latest.undeleted(colour, index))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(setting.title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            edit.colours.forEachIndexed { i, colour ->
                PaletteSwatch(
                    setting.title, edit.colours, i, drag,
                    onTap = { panel = if (panel == ColourPanel.Edit(colour)) null else ColourPanel.Edit(colour) },
                    onMove = { from, to -> save(latest.moved(from, to)) },
                )
            }
            Box(
                Modifier.size(SLOT).padding(4.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { panel = if (panel == ColourPanel.Add) null else ColourPanel.Add }
                    .semantics { contentDescription = "Add a colour to the ${setting.title.lowercase()}" },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            "Tap a colour to edit or delete it; hold and drag to reorder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        panel?.let { open ->
            val editedColour = (open as? ColourPanel.Edit)?.colour
            ColourEditPanel(
                label = editedColour?.let { "Editing: ${hexName(it)}" } ?: "Add a colour",
                start = editedColour ?: edit.colours.first(),
                factory = setting.default,
                onCancel = { panel = null },
                onSave = { new ->
                    save(if (editedColour != null) latest.replaced(editedColour, new) else latest.added(new))
                    panel = null
                },
                onDelete = if (editedColour != null && edit.canDelete) {
                    { delete(editedColour) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * The picker with the tool's [factory] colours under it, Cancel and Save, and Delete when [onDelete] is given
 * (SPECS: Settings → Ink). Starts on [start].
 */
@Composable
private fun ColourEditPanel(
    label: String,
    start: Int,
    factory: List<Int>,
    onCancel: () -> Unit,
    onSave: (Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var draft by remember(label, start) { mutableStateOf(start) }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
        Column(Modifier.padding(16.dp).width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ColourPicker(draft, { draft = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Factory", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(54.dp))
                factory.forEach { argb ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color(argb))
                            .selectable(selected = draft == argb, role = Role.RadioButton, onClick = { draft = argb })
                            .clearAndSetSemantics { contentDescription = "Factory ${hexName(argb)}" },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Box(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { onSave(draft) }) { Text("Save") }
            }
        }
    }
}
