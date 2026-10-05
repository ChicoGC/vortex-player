package com.example.vortex_player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Applies the current theme to views based on space-separated tokens in android:tag,
 * e.g. tag="pill icon" or tag="glass:28". A ":N" suffix is a corner radius in dp.
 */
class Styler(context: Context) {

    private val density = context.resources.displayMetrics.density
    var theme: AppTheme = Themes.DEFAULT
    var glass: Float = 0.5f

    fun dp(value: Float): Float = value * density

    fun applyTree(view: View) {
        (view.tag as? String)?.split(' ')?.forEach { applyToken(view, it) }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTree(view.getChildAt(i))
        }
    }

    private fun applyToken(view: View, token: String) {
        val radius = token.substringAfter(':', "").toFloatOrNull()
        when (token.substringBefore(':')) {
            "text" -> (view as? TextView)?.setTextColor(theme.textPrimary)
            "text2" -> (view as? TextView)?.setTextColor(theme.textSecondary)
            "accent" -> (view as? TextView)?.setTextColor(theme.accent)
            "icon" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.textPrimary)
            "icon2" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.textSecondary)
            "iconAccent" -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(theme.accent)
            "glass" -> setGlass(view, dp(radius ?: 26f), GlassDrawable.Mode.GLASS)
            "pill" -> setGlass(view, -1f, GlassDrawable.Mode.GLASS)
            "sheet" -> setGlass(view, dp(radius ?: 30f), GlassDrawable.Mode.SOLID)
            "flat" -> view.background = RippleDrawable(ColorStateList.valueOf(rippleColor()), null, null)
            "grabber" -> view.background = rounded(theme.textSecondary, dp(3f))
            "art" -> view.background = rounded(withAlpha(theme.textPrimary, 0.08f), dp(radius ?: 8f))
            "rim" -> view.foreground = GlassDrawable(theme, glass, dp(radius ?: 8f), GlassDrawable.Mode.RIM, density)
        }
    }

    private fun setGlass(view: View, radius: Float, mode: GlassDrawable.Mode) {
        val glassDrawable = GlassDrawable(theme, glass, radius, mode, density)
        view.background = if (mode == GlassDrawable.Mode.GLASS && view.isClickable) {
            RippleDrawable(ColorStateList.valueOf(rippleColor()), glassDrawable, rounded(Color.WHITE, if (radius < 0) dp(999f) else radius))
        } else {
            glassDrawable
        }
        view.clipToOutline = true
    }

    fun rowBackground(): Drawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor()), null, ColorDrawable(Color.WHITE))

    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    fun styleSeekBar(seekBar: SeekBar) {
        val track = rounded(withAlpha(theme.textPrimary, 0.22f), dp(2f))
        val fill = rounded(withAlpha(theme.textPrimary, 0.92f), dp(2f))
        val layers = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        val progress = seekBar.progress
        seekBar.progressDrawable = layers
        seekBar.thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(theme.textPrimary)
            setSize(dp(14f).toInt(), dp(14f).toInt())
        }
        seekBar.progress = 0
        seekBar.progress = progress
    }

    fun styleSwitch(switch: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        switch.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, 0xFFE0E0E0.toInt()))
        switch.trackTintList = ColorStateList(states, intArrayOf(theme.accent, withAlpha(theme.textPrimary, 0.35f)))
    }

    fun styleSegment(segment: TextView, selected: Boolean) {
        segment.background = if (selected) rounded(theme.accent, dp(999f)) else null
        segment.setTextColor(if (selected) theme.onAccent else theme.textPrimary)
    }

    private fun rippleColor() = withAlpha(theme.textPrimary, 0.16f)
}
