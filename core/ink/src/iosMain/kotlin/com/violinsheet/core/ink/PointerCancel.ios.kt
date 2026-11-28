package com.violinsheet.core.ink

import androidx.compose.ui.input.pointer.PointerEvent

// shortcut: iPad decides what counts as a system cancel when the iOS port is built.
actual fun PointerEvent.isSystemCancel(): Boolean = false
