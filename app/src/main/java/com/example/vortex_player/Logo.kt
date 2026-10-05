package com.example.vortex_player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import kotlin.math.cos
import kotlin.math.sin

/** The app logo (transparent PNG), recolored per theme with a hue rotation. */
object Logo {

    private const val TARGET_SIZE = 256
    private var cached: Bitmap? = null

    fun bitmap(context: Context): Bitmap = cached ?: decode(context).also { cached = it }

    fun colorFilter(theme: AppTheme): ColorMatrixColorFilter {
        val matrix = hueRotation(theme.logoHue)
        matrix.postConcat(ColorMatrix().apply { setSaturation(theme.logoSaturation) })
        return ColorMatrixColorFilter(matrix)
    }

    private fun decode(context: Context): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.vortex_logo, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeResource(context.resources, R.drawable.vortex_logo, options)
    }

    /** Luminance-preserving hue rotation; positive degrees move red toward green. */
    private fun hueRotation(degrees: Float): ColorMatrix {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lr = 0.213f
        val lg = 0.715f
        val lb = 0.072f
        return ColorMatrix(
            floatArrayOf(
                lr + c * (1 - lr) - s * lr, lg - c * lg - s * lg, lb - c * lb + s * (1 - lb), 0f, 0f,
                lr - c * lr + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb - c * lb - s * 0.283f, 0f, 0f,
                lr - c * lr - s * (1 - lr), lg - c * lg + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}
