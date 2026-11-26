package com.violinsheet.feature.annotation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinsheet.core.ink.Pen

/** What the toolbar's Undo and Redo buttons do, and whether each has anything to do. */
class UndoControls(val canUndo: Boolean, val canRedo: Boolean, val onUndo: () -> Unit, val onRedo: () -> Unit)

/**
 * The slim palette at the bottom edge: Undo and Redo, then pen colour and size presets. No dialogs (CLAUDE.md:
 * UI/UX). [saveFailed] shows a warning that the last change wasn't saved.
 */
@Composable
fun InkToolbar(pen: Pen, onPen: (Pen) -> Unit, undo: UndoControls, saveFailed: Boolean = false) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = undo.onUndo, enabled = undo.canUndo, modifier = Modifier.size(40.dp)) {
                Icon(UndoArrow, contentDescription = "Undo")
            }
            IconButton(onClick = undo.onRedo, enabled = undo.canRedo, modifier = Modifier.size(40.dp)) {
                // Redo's arrow is Undo's, mirrored.
                Icon(UndoArrow, contentDescription = "Redo", modifier = Modifier.graphicsLayer(scaleX = -1f))
            }
            ToolbarDivider()
            if (saveFailed) Text("Last change not saved", color = MaterialTheme.colorScheme.error)
            PEN_COLOURS.forEach { (name, colour) ->
                Choice(selected = pen.colour == colour, label = "$name pen", onClick = { onPen(pen.copy(colour = colour)) }) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(colour)))
                }
            }
            ToolbarDivider()
            PEN_SIZES.forEach { size ->
                Choice(selected = pen.size == size, label = "Pen size $size pt", onClick = { onPen(pen.copy(size = size)) }) {
                    // Dot grows with the size, so the presets read at a glance.
                    Box(Modifier.size((4 + size * 4).dp).clip(CircleShape).background(Color(pen.colour)))
                }
            }
        }
    }
}

@Composable
private fun ToolbarDivider() = VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 28.dp))

// The Material "undo" arrow. Built here because material-icons-core has none, and material-icons-extended is large.
private val UndoArrow = ImageVector.Builder("Undo", 24.dp, 24.dp, 24f, 24f).addPath(
    addPathNodes(
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    ),
    fill = SolidColor(Color.Black),
).build()

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
