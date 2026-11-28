package com.violinsheet.core.ink

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEvent

/**
 * Android reports the S Pen side button as the stylus primary button; some devices report a stylus button as the
 * secondary (mouse) button instead, which Compose's isSecondaryPressed also allows for.
 */
actual fun PointerEvent.isEraseHeld(): Boolean {
    val buttons = motionEvent?.buttonState ?: return false
    return buttons and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0
}
