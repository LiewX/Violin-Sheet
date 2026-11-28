package com.violinsheet.core.ink

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEvent

actual fun PointerEvent.isSystemCancel(): Boolean {
    val event = motionEvent ?: return true
    return event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0
}
