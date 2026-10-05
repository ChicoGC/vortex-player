package com.example.vortex_player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Full-screen backdrop: drifting theme-colored glows, optionally over a blurred cover. */
class AmbientView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var theme = Themes.DEFAULT
    private var useCover = true
    private var animated = true
    private var cover: Bitmap? = null
    private var previous: Bitmap? = null
    private var fade = 1f
    private var fadeAnimator: ValueAnimator? = null

    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coverPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val veilPaint = Paint()
    private val blobMatrix = Matrix()
    private val dst = RectF()
    private var shaders: List<RadialGradient> = emptyList()
    private val startTime = SystemClock.uptimeMillis()

    fun setStyle(theme: AppTheme, useCover: Boolean, animated: Boolean) {
        this.theme = theme
        this.useCover = useCover
        this.animated = animated
        rebuildShaders()
        invalidate()
    }

    fun setBackdrop(bitmap: Bitmap?) {
        if (bitmap === cover) return
        previous = cover
        cover = bitmap
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            addUpdateListener {
                fade = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuildShaders()

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate()
    }

    private fun rebuildShaders() {
        if (width == 0 || height == 0) return
        val radius = max(width, height) * 0.7f
        val alpha = if (theme.isLight) 0.9f else 0.75f
        shaders = theme.blobs.map {
            RadialGradient(0f, 0f, radius, withAlpha(it, alpha), withAlpha(it, 0f), Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(theme.base)

        val showCover = useCover && (cover != null || (previous != null && fade < 1f))
        if (showCover) {
            previous?.let { if (fade < 1f) drawCover(canvas, it, 1f - fade) }
            cover?.let { drawCover(canvas, it, fade) }
            veilPaint.color = if (theme.isLight) withAlpha(Color.WHITE, 0.35f) else withAlpha(Color.BLACK, 0.30f)
            canvas.drawRect(0f, 0f, w, h, veilPaint)
        }

        val t = if (animated) (SystemClock.uptimeMillis() - startTime) / 1000f else 0f
        blobPaint.alpha = if (showCover) 90 else 255
        shaders.forEachIndexed { i, shader ->
            val (x, y) = blobPosition(i, t, w, h)
            blobMatrix.setTranslate(x, y)
            shader.setLocalMatrix(blobMatrix)
            blobPaint.shader = shader
            canvas.drawRect(0f, 0f, w, h, blobPaint)
        }

        if (animated && isShown) postInvalidateOnAnimation()
    }

    private fun blobPosition(i: Int, t: Float, w: Float, h: Float): Pair<Float, Float> = when (i) {
        0 -> w * (0.15f + 0.20f * sin(t * 0.21f)) to h * (0.15f + 0.10f * cos(t * 0.17f))
        1 -> w * (0.90f + 0.15f * cos(t * 0.13f)) to h * (0.45f + 0.18f * sin(t * 0.19f))
        else -> w * (0.30f + 0.25f * sin(t * 0.11f + 1f)) to h * (0.90f + 0.08f * cos(t * 0.23f))
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, alpha: Float) {
        val scale = max(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        dst.set((width - dw) / 2, (height - dh) / 2, (width + dw) / 2, (height + dh) / 2)
        coverPaint.alpha = (alpha * 255).toInt()
        canvas.drawBitmap(bitmap, null, dst, coverPaint)
    }

    companion object {
        private const val BACKDROP_SIZE = 48
        private const val BLUR_RADIUS = 3

        /** Shrinks and box-blurs a cover so it can be stretched full-screen as a soft backdrop. */
        fun makeBackdrop(source: Bitmap): Bitmap {
            val n = BACKDROP_SIZE
            val small = Bitmap.createScaledBitmap(source, n, n, true)
            val pixels = IntArray(n * n)
            small.getPixels(pixels, 0, n, 0, 0, n, n)
            val tmp = IntArray(pixels.size)
            repeat(3) {
                blurPass(pixels, tmp, n, horizontal = true)
                blurPass(tmp, pixels, n, horizontal = false)
            }
            return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        }

        private fun blurPass(src: IntArray, dst: IntArray, n: Int, horizontal: Boolean) {
            for (y in 0 until n) {
                for (x in 0 until n) {
                    var a = 0
                    var r = 0
                    var g = 0
                    var b = 0
                    for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                        val xx = if (horizontal) (x + k).coerceIn(0, n - 1) else x
                        val yy = if (horizontal) y else (y + k).coerceIn(0, n - 1)
                        val c = src[yy * n + xx]
                        a += c ushr 24
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                    val count = BLUR_RADIUS * 2 + 1
                    dst[y * n + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
    }
}
