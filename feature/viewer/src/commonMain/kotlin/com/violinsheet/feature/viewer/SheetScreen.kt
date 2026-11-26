package com.violinsheet.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException

/** Neutral dark grey around the Page, not black, to reduce eye strain. The Page itself is never inverted. */
private val Surround = Color(0xFF3A3A3A)

/** Reads one Sheet, one Page at a time. */
@Composable
fun SheetScreen(viewModel: SheetViewModel) {
    val index by viewModel.pageIndex.collectAsState()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Surround)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pageTurnGestures(viewModel::turn),
        contentAlignment = Alignment.Center,
    ) {
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        // A pre-rendered Page shows in the same frame as the turn.
        val ready = viewModel.renderedPage(index, size)
        var lastRendered by remember { mutableStateOf<ImageBitmap?>(null) }
        var failedIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(index, size) {
            try {
                lastRendered = viewModel.renderPage(index, size)
                failedIndex = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedIndex = index
            }
            viewModel.preRender(index + 1, size)
            viewModel.preRender(index - 1, size)
        }
        when {
            failedIndex == index -> Text("Couldn't show Page ${index + 1}", color = Color.White)
            // Not ready yet (fast turns, rotation): keep the last Page on screen rather than flash the surround.
            else -> (ready ?: lastRendered)?.let {
                Image(it, contentDescription = "Page ${index + 1} of ${viewModel.pageCount}")
            }
        }
    }
}
