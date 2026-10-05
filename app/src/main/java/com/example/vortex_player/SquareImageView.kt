package com.example.vortex_player

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import kotlin.math.min

/** Square view that fits the smaller of the available width and height. */
class SquareImageView(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) width else min(width, height)
        setMeasuredDimension(size, size)
    }
}
