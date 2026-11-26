package com.violinsheet.feature.annotation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinsheet.core.data.ink.InkRepository
import com.violinsheet.core.data.settings.SettingsStore
import com.violinsheet.core.ink.InkStroke
import com.violinsheet.core.ink.Pen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One open Sheet's Annotations and the toolbar's current choices. Strokes are held in memory while the Sheet is open
 * and saved as each one is drawn (autosave), in [saveScope], which outlives the screen.
 */
class AnnotationViewModel(
    private val sheetId: Long,
    private val ink: InkRepository,
    settings: SettingsStore,
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private val _strokes = MutableStateFlow(emptyList<InkStroke>())
    val strokes: StateFlow<List<InkStroke>> = _strokes.asStateFlow()

    private val _pen = MutableStateFlow(Pen(InkSettings.penColour.default, InkSettings.penSize.default))

    /** Starts from the Ink Settings; a toolbar change lasts only while this Sheet is open (SPECS: Settings). */
    val pen: StateFlow<Pen> = _pen.asStateFlow()
    private var penChosen = false

    private val _saveFailed = MutableStateFlow(false)

    /** A stroke couldn't be saved: it shows now but would be gone on reopening. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        viewModelScope.launch {
            // Strokes drawn before the saved ones load are kept, after them; one already saved isn't added twice.
            val saved = ink.strokes(sheetId)
            val savedIds = saved.mapTo(HashSet()) { it.id }
            _strokes.update { drawn -> saved + drawn.filter { it.id !in savedIds } }
        }
        viewModelScope.launch {
            val colour = settings.observe(InkSettings.penColour).first()
            val size = settings.observe(InkSettings.penSize).first()
            if (!penChosen) _pen.value = Pen(colour, size)
        }
    }

    fun setPen(pen: Pen) {
        penChosen = true
        _pen.value = pen
    }

    fun addStroke(stroke: InkStroke) {
        _strokes.update { it + stroke }
        saveScope.launch {
            try {
                ink.add(sheetId, stroke)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveFailed.value = true
            }
        }
    }
}
