package com.example.vortex_player

import android.graphics.Bitmap

/** Shrinks and box-blurs a cover so it can be stretched full-screen as a soft background. */
object CoverBlur {

    private const val SIZE = 48
    private const val RADIUS = 3

    fun make(source: Bitmap): Bitmap {
        val n = SIZE
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
                for (k in -RADIUS..RADIUS) {
                    val xx = if (horizontal) (x + k).coerceIn(0, n - 1) else x
                    val yy = if (horizontal) y else (y + k).coerceIn(0, n - 1)
                    val c = src[yy * n + xx]
                    a += c ushr 24
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                }
                val count = RADIUS * 2 + 1
                dst[y * n + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }
}
