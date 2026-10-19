package com.example.vortex_player

import android.animation.ValueAnimator
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * "Liquid glass" in the style of iOS 26, for the classic View system.
 *
 * Three materials (see LIQUID_GLASS.md):
 *  - LiquidGlassDrawable: real glass for what floats (top buttons, mini player, controls, sheets).
 *    It redraws the views behind it (its "sources") blurred, and bends them at the edges like a lens.
 *  - GlassDrawable (GlassDrawable.kt): control glass, no blur, for buttons that sit inside content.
 *  - SurfaceDrawable: discreet content surface, no border, for grouped lists.
 *
 * Rule: a glass view can never be inside one of its own sources (it would draw itself inside itself).
 */

/** Android 12+ blurs; Android 13+ also refracts (needs RuntimeShader). */
val REAL_GLASS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Position of a view relative to the window root, including translations (not scale). */
fun offsetInRoot(view: View, out: FloatArray) {
    var x = 0f
    var y = 0f
    var v: View = view
    while (true) {
        x += v.left + v.translationX
        y += v.top + v.translationY
        val parent = v.parent as? View ?: break
        x -= parent.scrollX
        y -= parent.scrollY
        v = parent
    }
    out[0] = x
    out[1] = y
}

/** The light that appears under the finger while a glass element is pressed. */
class PressGlow(private val owner: Drawable, light: Boolean) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private val peak = if (light) 0.5f else 0.22f
    private var x = -1f
    private var y = -1f
    private var pressed = false
    private var value = 0f
    private var animator: ValueAnimator? = null

    fun setHotspot(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun onStateChange(state: IntArray): Boolean {
        val now = android.R.attr.state_pressed in state
        if (now == pressed) return false
        pressed = now
        animator?.cancel()
        animator = ValueAnimator.ofFloat(value, if (now) 1f else 0f).apply {
            duration = if (now) 90 else 420
            addUpdateListener {
                value = it.animatedValue as Float
                owner.invalidateSelf()
            }
            start()
        }
        return true
    }

    fun draw(canvas: Canvas, bounds: Rect, clip: Path) {
        if (value <= 0f || bounds.isEmpty) return
        val cx = if (x < 0) bounds.exactCenterX() else x
        val cy = if (y < 0) bounds.exactCenterY() else y
        val radius = max(bounds.width(), bounds.height()) * 0.75f
        paint.shader = RadialGradient(cx, cy, radius, withAlpha(Color.WHITE, peak * value), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }
}

/** Records the [sources] (the views behind [host]) into a RenderNode that a RenderEffect can blur. */
@RequiresApi(Build.VERSION_CODES.S)
class BackdropLayer(private val host: View, private val sources: () -> List<View>) {
    private val node = RenderNode("backdrop")
    private val hostPos = FloatArray(2)
    private val srcPos = FloatArray(2)
    private var effectKey: Any? = null

    /** Records the area behind [bounds] plus [pad] on each side (blur needs room at the edges). */
    fun record(bounds: Rect, pad: Int, base: Int): RenderNode {
        val w = bounds.width() + pad * 2
        val h = bounds.height() + pad * 2
        node.setPosition(0, 0, w, h)
        offsetInRoot(host, hostPos)
        val canvas = node.beginRecording(w, h)
        canvas.drawColor(base)
        for (source in sources()) {
            if (!source.isShown || source.width == 0) continue
            offsetInRoot(source, srcPos)
            val save = canvas.save()
            canvas.translate(srcPos[0] - hostPos[0] - bounds.left + pad, srcPos[1] - hostPos[1] - bounds.top + pad)
            source.draw(canvas)
            canvas.restoreToCount(save)
        }
        node.endRecording()
        return node
    }

    fun setEffect(key: Any, build: () -> RenderEffect) {
        if (key == effectKey) return
        node.setRenderEffect(build())
        effectKey = key
    }

    companion object {
        /** Colors behind glass get more saturated, like on iOS ("vibrancy"). */
        val vibrancy: ColorMatrixColorFilter by lazy {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.45f) })
        }

        fun blur(radius: Float): RenderEffect {
            val r = max(radius, 0.5f)
            return RenderEffect.createChainEffect(
                RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP),
                RenderEffect.createColorFilterEffect(vibrancy),
            )
        }

        /** Lens: near the edges the picture is pulled from further inside, bending it like thick glass. */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun lens(origin: Float, width: Float, height: Float, radius: Float, refractionHeight: Float, amount: Float): RenderEffect {
            val shader = lensShader
            shader.setFloatUniform("origin", origin, origin)
            shader.setFloatUniform("rectSize", width, height)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("height", refractionHeight)
            shader.setFloatUniform("amount", amount)
            return RenderEffect.createRuntimeShaderEffect(shader, "content")
        }

        @get:RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private val lensShader: RuntimeShader by lazy { RuntimeShader(LENS_AGSL) }

        private const val LENS_AGSL = """
            uniform shader content;
            uniform float2 origin;
            uniform float2 rectSize;
            uniform float radius;
            uniform float height;
            uniform float amount;

            float sdRound(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            half4 main(float2 coord) {
                float2 halfSize = rectSize * 0.5;
                float2 p = coord - origin - halfSize;
                float inside = -sdRound(p, halfSize, radius);
                if (inside <= 0.0 || inside >= height) return content.eval(coord);
                float2 g = float2(
                    sdRound(p + float2(1.0, 0.0), halfSize, radius) - sdRound(p - float2(1.0, 0.0), halfSize, radius),
                    sdRound(p + float2(0.0, 1.0), halfSize, radius) - sdRound(p - float2(0.0, 1.0), halfSize, radius));
                float len = length(g);
                if (len < 0.0001) return content.eval(coord);
                float2 n = g / len;
                float t = 1.0 - inside / height;
                float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
                float2 offset = -n * amount * bend;
                half4 c = content.eval(coord + offset);
                half r = content.eval(coord + offset * 1.06).r;
                half b = content.eval(coord + offset * 0.94).b;
                return half4(r, c.g, b, c.a);
            }
        """
    }
}

/** Shared tint, top sheen and specular rim of every glass shape. */
class GlassFinish(theme: AppTheme, density: Float, private val rimStrength: Float) {
    private val light = theme.isLight
    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    fun layout(rect: RectF, glossAlpha: Float) {
        glossPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.55f,
            withAlpha(Color.WHITE, glossAlpha), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        val s = rimStrength
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                withAlpha(Color.WHITE, (if (light) 0.95f else 0.62f) * s),
                withAlpha(Color.WHITE, (if (light) 0.30f else 0.06f) * s),
                if (light) withAlpha(Color.BLACK, 0.08f) else withAlpha(Color.WHITE, 0.30f * s),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
}

/**
 * Real glass. [intensity] 0 = transparent (little blur, strong refraction), 1 = frosted.
 * [radius] < 0 draws a capsule. [minOpacity] keeps text readable on big panels (sheets).
 */
class LiquidGlassDrawable(
    host: View,
    sources: () -> List<View>,
    private val theme: AppTheme,
    private val intensity: Float,
    private val radius: Float,
    private val density: Float,
    private val minOpacity: Float = 0f,
    private val lens: Float = 1f,
    private val pressGlow: Boolean = true,
) : Drawable() {

    private val layer = if (REAL_GLASS) BackdropLayer(host, sources) else null
    private val rect = RectF()
    private val rimRect = RectF()
    private val path = Path()
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val finish = GlassFinish(theme, density, lerp(1.25f, 0.8f, intensity))
    private val glow = PressGlow(this, theme.isLight)
    private val blurRadius = lerp(if (minOpacity > 0f) 6f else 1.5f, 16f, intensity) * density

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val half = finish.rimPaint.strokeWidth / 2
        rimRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        path.reset()
        val r = cornerRadius()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        val surfaceAlpha = if (layer != null) {
            max(lerp(0.08f, 0.62f, intensity), minOpacity)
        } else {
            max(lerp(0.55f, 0.85f, intensity), minOpacity) // no blur: needs more cover
        }
        surfacePaint.color = withAlpha(theme.base, surfaceAlpha)
        finish.layout(rect, if (theme.isLight) 0.4f else lerp(0.12f, 0.07f, intensity))
    }

    private fun cornerRadius(): Float =
        if (radius < 0) min(rect.width(), rect.height()) / 2 else min(radius, min(rect.width(), rect.height()) / 2)

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val r = cornerRadius()
        if (layer != null && canvas.isHardwareAccelerated) drawBackdrop(canvas, layer, r)
        canvas.drawPath(path, surfacePaint)
        canvas.drawPath(path, finish.glossPaint)
        glow.draw(canvas, bounds, path)
        canvas.drawRoundRect(rimRect, r, r, finish.rimPaint)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBackdrop(canvas: Canvas, layer: BackdropLayer, r: Float) {
        val pad = ceil(blurRadius).toInt()
        val node = layer.record(bounds, pad, theme.base)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val refractionHeight = min(18f * density * lens, min(w, h) / 2)
        // Bending further than the lens is tall would mirror the picture at the edge
        val amount = min(lerp(30f, 16f, intensity) * density * lens, refractionHeight * 0.7f)
        layer.setEffect(listOf(w, h, r, pad, intensity, lens)) {
            val blur = BackdropLayer.blur(blurRadius)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lens > 0f) {
                RenderEffect.createChainEffect(BackdropLayer.lens(pad.toFloat(), w, h, r, refractionHeight, amount), blur)
            } else {
                blur
            }
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.translate(bounds.left - pad.toFloat(), bounds.top - pad.toFloat())
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    override fun isStateful() = pressGlow
    override fun onStateChange(state: IntArray) = pressGlow && glow.onStateChange(state)
    override fun setHotspot(x: Float, y: Float) = glow.setHotspot(x, y)

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius())
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * The iOS 26 "scroll edge": under the top bar, the content that scrolls past is blurred and
 * darkened, fully up to [solidHeight] and fading out below it.
 */
class ScrollEdgeDrawable(
    host: View,
    sources: () -> List<View>,
    private val theme: AppTheme,
    private val solidHeight: () -> Float,
    private val density: Float,
) : Drawable() {

    private val layer = if (REAL_GLASS) BackdropLayer(host, sources) else null
    private val basePaint = Paint().apply { color = withAlpha(theme.base, if (REAL_GLASS) 0.6f else 0.92f) }
    private val maskPaint = Paint().apply { blendMode = BlendMode.DST_IN }
    private val blurRadius = 14f * density
    private val area = RectF()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        area.set(bounds)
        val save = canvas.saveLayer(area, null)
        if (layer != null && canvas.isHardwareAccelerated) {
            val pad = ceil(blurRadius).toInt()
            val node = layer.record(bounds, pad, theme.base)
            layer.setEffect(blurRadius) { BackdropLayer.blur(blurRadius) }
            canvas.save()
            canvas.translate(bounds.left - pad.toFloat(), bounds.top - pad.toFloat())
            canvas.drawRenderNode(node)
            canvas.restore()
        }
        canvas.drawRect(area, basePaint)
        maskPaint.shader = LinearGradient(
            0f, solidHeight() - 6 * density, 0f, bounds.bottom - 4 * density,
            Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(area, maskPaint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

enum class RowPosition { SINGLE, FIRST, MIDDLE, LAST }

fun rowPosition(index: Int, count: Int) = when {
    count == 1 -> RowPosition.SINGLE
    index == 0 -> RowPosition.FIRST
    index == count - 1 -> RowPosition.LAST
    else -> RowPosition.MIDDLE
}

/**
 * Discreet content surface without a border. As a grouped-list row only the outer corners are
 * rounded, and rows after the first get a thin divider that starts at [dividerInset].
 */
class SurfaceDrawable(
    private val theme: AppTheme,
    intensity: Float,
    private val radius: Float,
    private val density: Float,
    var position: RowPosition = RowPosition.SINGLE,
    private val dividerInset: Float = 0f,
) : Drawable() {

    private val rect = RectF()
    private val path = Path()
    private val radii = FloatArray(8)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.base, lerp(0.18f, 0.6f, intensity)) }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(Color.WHITE, if (theme.isLight) lerp(0.35f, 0.5f, intensity) else lerp(0.055f, 0.075f, intensity))
    }
    private val dividerPaint = Paint().apply {
        color = if (theme.isLight) withAlpha(Color.BLACK, 0.08f) else withAlpha(Color.WHITE, 0.09f)
        strokeWidth = 0.5f * density
    }
    private val glow = PressGlow(this, theme.isLight)

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        rect.set(bounds)
        val top = if (position == RowPosition.SINGLE || position == RowPosition.FIRST) radius else 0f
        val bottom = if (position == RowPosition.SINGLE || position == RowPosition.LAST) radius else 0f
        radii.fill(top, 0, 4)
        radii.fill(bottom, 4, 8)
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(path, basePaint)
        canvas.drawPath(path, lightPaint)
        glow.draw(canvas, bounds, path)
        if (position == RowPosition.MIDDLE || position == RowPosition.LAST) {
            canvas.drawLine(rect.left + dividerInset, rect.top, rect.right, rect.top, dividerPaint)
        }
    }

    override fun isStateful() = true
    override fun onStateChange(state: IntArray) = glow.onStateChange(state)
    override fun setHotspot(x: Float, y: Float) = glow.setHotspot(x, y)

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Only the press light, for borderless buttons inside a glass shape. [radius] < 0 = circle/capsule. */
class GlowDrawable(light: Boolean, private val radius: Float) : Drawable() {
    private val glow = PressGlow(this, light)
    private val path = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val r = if (radius < 0) min(rect.width(), rect.height()) / 2 else radius
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) = glow.draw(canvas, bounds, path)
    override fun isStateful() = true
    override fun onStateChange(state: IntArray) = glow.onStateChange(state)
    override fun setHotspot(x: Float, y: Float) = glow.setHotspot(x, y)

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
