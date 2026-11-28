package com.violinsheet.core.ink

import androidx.compose.ui.input.pointer.PointerEvent

/** Whether the pen's erase button (the S Pen side button) is held during this event. */
expect fun PointerEvent.isEraseHeld(): Boolean
