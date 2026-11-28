package com.violinsheet.core.ink

import androidx.compose.ui.input.pointer.PointerEvent

// shortcut: the Apple Pencil's erase gesture (double tap or squeeze) is decided when the iOS port is built.
actual fun PointerEvent.isEraseHeld(): Boolean = false
