package com.violinsheet.core.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// shortcut: no Ink on iPad yet; the PencilKit or Metal canvas comes with the iPad port (ADR 0002).
@Composable
actual fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    tool: InkTool,
    eraser: Eraser,
    onStroke: (InkStroke) -> Unit,
    erase: EraseInput,
    modifier: Modifier,
) = Unit
