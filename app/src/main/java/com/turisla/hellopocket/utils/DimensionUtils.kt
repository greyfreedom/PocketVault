package com.turisla.hellopocket.utils

import android.content.Context
import android.util.TypedValue

fun Float.dp2px(context: Context): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics)
}