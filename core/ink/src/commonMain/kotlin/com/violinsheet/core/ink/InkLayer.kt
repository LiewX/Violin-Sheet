package com.violinsheet.core.ink

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Draws [strokes] on the Pages at [placements] and lets the pen draw new ones with [pen]. Each finished stroke goes
 * to [onStroke] in Page coordinates. Only the pen draws; finger touches pass through to navigation (CLAUDE.md: input
 * split). Platform code: native low-latency ink per platform (ADR 0002).
 */
@Composable
expect fun InkLayer(
    placements: List<PagePlacement>,
    strokes: List<InkStroke>,
    pen: Pen,
    onStroke: (InkStroke) -> Unit,
    modifier: Modifier = Modifier,
)
