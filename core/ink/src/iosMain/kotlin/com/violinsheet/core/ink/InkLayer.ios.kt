package com.violinsheet.core.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// shortcut: no Ink on iPad yet; the PencilKit or Metal canvas comes with the iPad port (ADR 0002).
@Composable
actual fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    tool: InkTool,
    eraser: Eraser,
    onStroke: (InkStroke) -> Unit,
    erase: EraseInput,
    selection: Selection?,
    lasso: LassoInput,
    selectionColour: Color,
    modifier: Modifier,
) = Unit
