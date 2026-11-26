package com.violinsheet.feature.annotation

import com.violinsheet.core.ink.EraseMode
import com.violinsheet.core.ink.Eraser

/** The eraser as last chosen. One per app run, so the next Sheet opened starts with it (the eraser has no Setting). */
class EraserMemory {
    var eraser = Eraser(EraseMode.Stroke, ERASER_SIZES[1])
}
