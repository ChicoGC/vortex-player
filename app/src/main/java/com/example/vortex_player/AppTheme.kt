package com.example.vortex_player

import android.graphics.Color

class AppTheme(
    val id: String,
    val name: String,
    val isLight: Boolean,
    val base: Int,
    val blobs: IntArray,
    val accent: Int,
    val onAccent: Int = Color.WHITE,
    val logoHue: Float = 0f,
    val logoSaturation: Float = 1f,
) {
    val textPrimary: Int = if (isLight) 0xFF111116.toInt() else Color.WHITE
    val textSecondary: Int = if (isLight) 0x99111116.toInt() else 0xA6FFFFFF.toInt()
}

object Themes {
    val ALL = listOf(
        // Escuros
        AppTheme("aurora", "Aurora", false, 0xFF0B0820.toInt(),
            intArrayOf(0xFF7C4DFF.toInt(), 0xFF2D7CFF.toInt(), 0xFFFF4FD8.toInt()), 0xFFA78BFA.toInt(), logoHue = -40f),
        AppTheme("oceano", "Oceano", false, 0xFF03131C.toInt(),
            intArrayOf(0xFF00C2FF.toInt(), 0xFF0057FF.toInt(), 0xFF00E0B8.toInt()), 0xFF38D6FF.toInt(), 0xFF00202C.toInt(), logoHue = -150f, logoSaturation = 0.95f),
        AppTheme("sunset", "Pôr do sol", false, 0xFF1A0A10.toInt(),
            intArrayOf(0xFFFF7A45.toInt(), 0xFFFF3D7F.toInt(), 0xFF8F3DFF.toInt()), 0xFFFF8A5B.toInt(), logoHue = 0f),
        AppTheme("floresta", "Floresta", false, 0xFF06140F.toInt(),
            intArrayOf(0xFF22C55E.toInt(), 0xFF0EA5A4.toInt(), 0xFFA3E635.toInt()), 0xFF4ADE80.toInt(), 0xFF052E16.toInt(), logoHue = 170f),
        AppTheme("cereja", "Cereja", false, 0xFF16050A.toInt(),
            intArrayOf(0xFFFA2D48.toInt(), 0xFFFF6B9A.toInt(), 0xFF7A1030.toInt()), 0xFFFF4D64.toInt(), logoHue = -15f, logoSaturation = 1.15f),
        AppTheme("neon", "Neon", false, 0xFF07031A.toInt(),
            intArrayOf(0xFFFF2BD6.toInt(), 0xFF00E5FF.toInt(), 0xFF6A2BFF.toInt()), 0xFFFF4FE0.toInt(), logoHue = -25f, logoSaturation = 1.2f),
        AppTheme("ouro", "Ouro", false, 0xFF140E04.toInt(),
            intArrayOf(0xFFFFC233.toInt(), 0xFFFF8A00.toInt(), 0xFF8A5A00.toInt()), 0xFFFFC94D.toInt(), 0xFF2A1A00.toInt(), logoHue = 30f, logoSaturation = 0.95f),
        AppTheme("vulcao", "Vulcão", false, 0xFF140504.toInt(),
            intArrayOf(0xFFFF3D00.toInt(), 0xFFFF9100.toInt(), 0xFF8B0A0A.toInt()), 0xFFFF6A2B.toInt(), logoHue = 10f, logoSaturation = 1.1f),
        AppTheme("meianoite", "Meia-noite", false, 0xFF02040D.toInt(),
            intArrayOf(0xFF1E3A8A.toInt(), 0xFF3730A3.toInt(), 0xFF0F766E.toInt()), 0xFF60A5FA.toInt(), 0xFF0B1530.toInt(), logoHue = -100f, logoSaturation = 0.9f),
        AppTheme("matrix", "Matrix", false, 0xFF010A04.toInt(),
            intArrayOf(0xFF00FF66.toInt(), 0xFF00A83E.toInt(), 0xFF004D1C.toInt()), 0xFF39FF88.toInt(), 0xFF002910.toInt(), logoHue = 160f, logoSaturation = 1.1f),
        AppTheme("grafite", "Grafite", false, 0xFF0A0A0C.toInt(),
            intArrayOf(0xFF6B7280.toInt(), 0xFF9CA3AF.toInt(), 0xFF374151.toInt()), 0xFFE5E7EB.toInt(), 0xFF111116.toInt(), logoSaturation = 0f),
        // Claros
        AppTheme("cristal", "Cristal", true, 0xFFEEF2FA.toInt(),
            intArrayOf(0xFF9EC5FF.toInt(), 0xFFFFC6E8.toInt(), 0xFFB8F0FF.toInt()), 0xFF0A84FF.toInt(), logoHue = -90f, logoSaturation = 0.9f),
        AppTheme("areia", "Areia", true, 0xFFF6F0E6.toInt(),
            intArrayOf(0xFFFFD6A5.toInt(), 0xFFFFADAD.toInt(), 0xFFCAFFBF.toInt()), 0xFFE8590C.toInt(), logoHue = 10f, logoSaturation = 0.85f),
        AppTheme("lavanda", "Lavanda", true, 0xFFF3EEFB.toInt(),
            intArrayOf(0xFFD8C4FF.toInt(), 0xFFFFD1F0.toInt(), 0xFFC4D7FF.toInt()), 0xFF7C3AED.toInt(), logoHue = -45f, logoSaturation = 0.9f),
        AppTheme("menta", "Menta", true, 0xFFEAF7F2.toInt(),
            intArrayOf(0xFFA7F3D0.toInt(), 0xFFBAE6FD.toInt(), 0xFFD9F99D.toInt()), 0xFF059669.toInt(), logoHue = 165f, logoSaturation = 0.85f),
        AppTheme("algodao", "Algodão-doce", true, 0xFFFDF0F6.toInt(),
            intArrayOf(0xFFFFB3D9.toInt(), 0xFFB3E5FF.toInt(), 0xFFE0C3FC.toInt()), 0xFFDB2777.toInt(), logoHue = -10f, logoSaturation = 0.9f),
        AppTheme("pessego", "Pêssego", true, 0xFFFFF3EC.toInt(),
            intArrayOf(0xFFFFC2A8.toInt(), 0xFFFFE0A3.toInt(), 0xFFFFB5C2.toInt()), 0xFFEA580C.toInt(), logoHue = 15f, logoSaturation = 0.85f),
    )

    val DEFAULT = ALL.first()

    fun byId(id: String?): AppTheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
