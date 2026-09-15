package com.you.visionaid.util

import android.util.TypedValue
import android.widget.TextView

/** Applies reading text size without the app-specific UI font multiplier. */
fun TextView.setReadingTextSizeSp(textSizeSp: Int) {
    val systemMetrics = context.applicationContext.resources.displayMetrics
    val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        textSizeSp.toFloat(),
        systemMetrics,
    )
    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
}
