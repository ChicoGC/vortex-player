package com.example.vortex_player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Fundo base do modo degradê. */
val Background = Color(0xFF07080C)

val TextPrimary = Color(0xFFF5F6FA)
val TextSecondary = Color(0xA6EBEDF5)
val TextTertiary = Color(0x66EBEDF5)

val Danger = Color(0xFFFF6B6B)

/**
 * Um tema do app.
 *
 * @param accent cor de destaque (seleções, botão principal).
 * @param glows as três manchas de luz do fundo em degradê.
 * @param solid a cor do fundo no modo "cor única".
 */
@Immutable
data class VortexTheme(
    val id: String,
    val name: String,
    val accent: Color,
    val glows: List<Color>,
    val solid: Color,
)

private fun theme(id: String, name: String, accent: Long, glows: List<Long>, base: Long) = VortexTheme(
    id = id,
    name = name,
    accent = Color(accent),
    glows = glows.map { Color(it) },
    // A cor única é o fundo do tema com um toque da primeira mancha de luz
    solid = lerp(Color(base), Color(glows.first()), 0.12f),
)

val Themes = listOf(
    theme("aurora", "Aurora", 0xFFA78BFA, listOf(0xFF7C4DFF, 0xFF2D7CFF, 0xFFFF4FD8), 0xFF0B0820),
    theme("oceano", "Oceano", 0xFF38D6FF, listOf(0xFF0057FF, 0xFF00C2FF, 0xFF00E0B8), 0xFF03131C),
    theme("sunset", "Pôr do sol", 0xFFFF8A5B, listOf(0xFFFF7A45, 0xFFFF3D7F, 0xFF8F3DFF), 0xFF1A0A10),
    theme("floresta", "Floresta", 0xFF4ADE80, listOf(0xFF22C55E, 0xFF0EA5A4, 0xFFA3E635), 0xFF06140F),
    theme("cereja", "Cereja", 0xFFFF4D64, listOf(0xFFFA2D48, 0xFFFF6B9A, 0xFF7A1030), 0xFF16050A),
    theme("neon", "Neon", 0xFFFF4FE0, listOf(0xFFFF2BD6, 0xFF00E5FF, 0xFF6A2BFF), 0xFF07031A),
    theme("ouro", "Ouro", 0xFFFFC94D, listOf(0xFFFF8A00, 0xFFFFC233, 0xFF8A5A00), 0xFF140E04),
    theme("vulcao", "Vulcão", 0xFFFF6A2B, listOf(0xFFFF3D00, 0xFFFF9100, 0xFF8B0A0A), 0xFF140504),
    theme("meianoite", "Meia-noite", 0xFF60A5FA, listOf(0xFF3730A3, 0xFF1E3A8A, 0xFF0F766E), 0xFF02040D),
    theme("matrix", "Matrix", 0xFF39FF88, listOf(0xFF00A83E, 0xFF00FF66, 0xFF004D1C), 0xFF010A04),
    theme("grafite", "Grafite", 0xFFE5E7EB, listOf(0xFF6B7280, 0xFF475569, 0xFF9CA3AF), 0xFF0A0A0C),
)

fun themeById(id: String?): VortexTheme = Themes.firstOrNull { it.id == id } ?: Themes.first()

/** O que fica no fundo da tela. */
enum class BackgroundStyle(val label: String) {
    Cover("Capa"),
    Gradient("Degradê"),
    Solid("Cor única"),
}

/** Tema, estilo de fundo e intensidade do vidro em uso. */
@Immutable
data class VortexLook(
    val theme: VortexTheme,
    val background: BackgroundStyle,
    /** Intensidade do vidro: 0 = transparente, 1 = fosco. */
    val glass: Float = 0.4f,
) {
    /** Cor de base da tela, usada atrás do vidro desfocado. */
    val base: Color get() = if (background == BackgroundStyle.Solid) theme.solid else Background
}

val LocalLook = compositionLocalOf { VortexLook(Themes.first(), BackgroundStyle.Gradient) }
