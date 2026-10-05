package com.example.vortex_player

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Translucent panel with a top gloss and a specular rim; radius < 0 draws a capsule. */
class GlassDrawable(
    private val theme: AppTheme,
    private val intensity: Float,
    private val radius: Float,
    private val mode: Mode,
    density: Float,
) : Drawable() {

    enum class Mode { GLASS, SOLID, RIM }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val rect = RectF()
    private val rimRect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val half = rimPaint.strokeWidth / 2
        rimRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)

        val light = theme.isLight
        fillPaint.color = when (mode) {
            Mode.SOLID -> withAlpha(theme.base, lerp(0.78f, 0.94f, intensity))
            else -> withAlpha(Color.WHITE, if (light) lerp(0.28f, 0.72f, intensity) else lerp(0.04f, 0.24f, intensity))
        }
        glossPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.55f,
            withAlpha(Color.WHITE, if (light) 0.45f else 0.10f + 0.10f * intensity),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                withAlpha(Color.WHITE, if (light) 0.95f else 0.60f),
                withAlpha(Color.WHITE, if (light) 0.35f else 0.06f),
                if (light) withAlpha(Color.BLACK, 0.10f) else withAlpha(Color.WHITE, 0.28f),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun cornerRadius(): Float =
        if (radius < 0) min(rect.width(), rect.height()) / 2 else radius

    override fun draw(canvas: Canvas) {
        val r = cornerRadius()
        if (mode != Mode.RIM) {
            canvas.drawRoundRect(rect, r, r, fillPaint)
            canvas.drawRoundRect(rect, r, r, glossPaint)
        }
        canvas.drawRoundRect(rimRect, r, r, rimPaint)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius())
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
