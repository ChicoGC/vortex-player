package com.example.vortex_player

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Applies the current theme to views based on space-separated tokens in android:tag,
 * e.g. tag="pill icon" or tag="glass:28". A ":N" suffix is a corner radius in dp.
 *
 * Glass tokens (see LiquidGlass.kt):
 *  - "pill", "glass:N": control glass. With "float=group" it becomes real liquid glass that
 *    blurs and refracts the views registered in [sources] under that group name.
 *  - "sheet": liquid glass for bottom sheets (group "sheet").
 *  - "surface:N": discreet content surface, no border.
 */
class Styler(context: Context) {

    private val density = context.resources.displayMetrics.density
    var theme: AppTheme = Themes.DEFAULT
    var glass: Float = 0.5f

    /** Backdrop sources per group: what each floating glass shows behind itself. */
    val sources = mutableMapOf<String, () -> List<View>>()
    private val liquid = WeakHashMap<Drawable, View>()

    fun dp(value: Float): Float = value * density

    fun applyTree(view: View) {
        (view.tag as? String)?.let { tag ->
            val tokens = tag.split(' ')
            val group = tokens.firstOrNull { it.startsWith("float=") }?.substringAfter('=')
            tokens.forEach { applyToken(view, it, group) }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTree(view.getChildAt(i))
        }
    }

    /** Re-renders every visible liquid glass (call when what is behind it changed). */
    fun invalidateGlass() {
        for ((drawable, host) in liquid) {
            if (host.isShown) drawable.invalidateSelf()
        }
    }

    private fun applyToken(view: View, token: String, group: String?) {
        val radius = token.substringAfter(':', "").toFloatOrNull()
        when (token.substringBefore(':')) {
            "text" -> (view as? TextView)?.setTextColor(theme.textPrimary)
            "text2" -> (view as? TextView)?.setTextColor(theme.textSecondary)
            "accent" -> (view as? TextView)?.setTextColor(theme.accent)
            "icon" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.textPrimary)
            "icon2" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.textSecondary)
            "iconAccent" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.accent)
            "glass" -> setGlass(view, dp(radius ?: 26f), group)
            "pill" -> setGlass(view, -1f, group)
            "sheet" -> setLiquid(view, dp(radius ?: 34f), "sheet", minOpacity = 0.74f, lens = 0.7f, pressGlow = false)
            "surface" -> view.background = SurfaceDrawable(theme, glass, dp(radius ?: 26f), density)
            "flat" -> {
                view.background = GlowDrawable(theme.isLight, -1f)
                if (view.isClickable) pressScale(view, 1.1f)
            }
            "grabber" -> view.background = rounded(withAlpha(theme.textPrimary, 0.28f), dp(3f))
            "art" -> view.background = rounded(withAlpha(theme.textPrimary, 0.08f), dp(radius ?: 8f))
            "rim" -> view.foreground = GlassDrawable(theme, glass, dp(radius ?: 8f), GlassDrawable.Mode.RIM, density)
        }
    }

    private fun setGlass(view: View, radius: Float, group: String?) {
        if (group != null) {
            // Glass holding text (mini player, output) needs more cover than a lone icon to stay readable
            setLiquid(view, radius, group, minOpacity = if (view is ViewGroup) 0.42f else 0f)
        } else {
            view.background = GlassDrawable(theme, glass, radius, GlassDrawable.Mode.GLASS, density)
            view.clipToOutline = true
        }
        if (view.isClickable) pressScale(view, growFor(view))
    }

    private fun setLiquid(
        view: View,
        radius: Float,
        group: String,
        minOpacity: Float = 0f,
        lens: Float = 1f,
        pressGlow: Boolean = true,
    ) {
        val sourcesOf = { sources[group]?.invoke().orEmpty() }
        val drawable = LiquidGlassDrawable(view, sourcesOf, theme, glass, radius, density, minOpacity, lens, pressGlow)
        liquid[drawable] = view
        view.background = drawable
        view.clipToOutline = true
        if (REAL_GLASS) view.elevation = dp(if (group == "sheet") 18f else 8f)
    }

    /** Glass buttons grow when pressed; small ones more, wide ones barely (they are almost cards). */
    private fun growFor(view: View): Float {
        val width = view.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
        return when {
            width > 0 && width <= dp(64f) -> 1.14f
            width == ViewGroup.LayoutParams.MATCH_PARENT -> 1.02f
            else -> 1.06f
        }
    }

    /** Springy scale while pressed (the glass "lifts" under the finger). */
    fun pressScale(view: View, scale: Float) {
        val down = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
        ).apply {
            duration = 220
            interpolator = OvershootInterpolator(2.2f)
        }
        val up = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f),
        ).apply {
            duration = 420
            interpolator = OvershootInterpolator(2.6f)
        }
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), down)
            addState(intArrayOf(), up)
        }
    }

    /** A row of a grouped (inset) list: one rounded block per group, thin dividers between rows. */
    fun groupRow(position: RowPosition, dividerInset: Float): SurfaceDrawable =
        SurfaceDrawable(theme, glass, dp(GROUP_RADIUS), density, position, dividerInset)

    fun scrollEdge(host: View, group: String, solidHeight: () -> Float): Drawable {
        val drawable = ScrollEdgeDrawable(host, { sources[group]?.invoke().orEmpty() }, theme, solidHeight, density)
        liquid[drawable] = host
        return drawable
    }

    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    fun styleSeekBar(seekBar: SeekBar) {
        val track = rounded(withAlpha(theme.textPrimary, 0.22f), dp(2f))
        val fill = rounded(withAlpha(theme.textPrimary, 0.92f), dp(2f))
        setTrack(seekBar, track, fill)
        seekBar.thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.textPrimary)
            setSize(dp(14f).toInt(), dp(14f).toInt())
        }
    }

    /** iOS 26 slider: thicker track filled with the theme color and a white capsule knob. */
    fun styleSlider(seekBar: SeekBar) {
        val track = rounded(withAlpha(theme.textPrimary, 0.18f), dp(3f))
        val fill = rounded(theme.accent, dp(3f))
        setTrack(seekBar, track, fill)
        seekBar.thumb = GradientDrawable().apply {
            cornerRadius = dp(12f)
            setColor(Color.WHITE)
            setStroke(dp(0.8f).toInt(), withAlpha(Color.BLACK, 0.08f))
            setSize(dp(38f).toInt(), dp(24f).toInt())
        }
        // Room for the whole capsule at both ends
        val half = dp(19f).toInt()
        seekBar.setPadding(half, seekBar.paddingTop, half, seekBar.paddingBottom)
        seekBar.thumbOffset = half
    }

    private fun setTrack(seekBar: SeekBar, track: Drawable, fill: Drawable) {
        val layers = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        val progress = seekBar.progress
        seekBar.progressDrawable = layers
        seekBar.progress = 0
        seekBar.progress = progress
    }

    fun styleSwitch(switch: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        switch.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFFE0E0E0.toInt()))
        switch.trackTintList = ColorStateList(states, intArrayOf(theme.accent, withAlpha(theme.textPrimary, 0.35f)))
    }

    /** Neutral selection: the chosen segment is clear glass, not the theme color. */
    fun styleSegment(segment: TextView, selected: Boolean) {
        segment.background = if (selected) {
            GradientDrawable().apply {
                cornerRadius = dp(999f)
                setColor(withAlpha(Color.WHITE, if (theme.isLight) 0.92f else 0.16f))
                setStroke(dp(0.8f).toInt(), withAlpha(Color.WHITE, if (theme.isLight) 1f else 0.30f))
            }
        } else {
            null
        }
        segment.setTextColor(if (selected) theme.textPrimary else theme.textSecondary)
        segment.paint.isFakeBoldText = selected
        segment.invalidate()
    }

    private companion object {
        const val GROUP_RADIUS = 26f
    }
}
