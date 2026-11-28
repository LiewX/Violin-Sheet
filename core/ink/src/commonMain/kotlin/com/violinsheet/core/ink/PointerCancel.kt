package com.violinsheet.core.ink

import androidx.compose.ui.input.pointer.PointerEvent

/**
 * Compose turns a cancelled gesture into an ordinary lift. This tells them apart where the platform can: a cancel, a
 * pointer the system flagged as cancelled (palm rejection), or no event at all (Compose's own synthetic cancel).
 */
expect fun PointerEvent.isSystemCancel(): Boolean
