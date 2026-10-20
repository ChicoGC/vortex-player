package com.example.vortex_player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

// O app é sempre escuro: o vidro fica muito mais bonito sobre fundo escuro.
@Composable
fun PlayerTheme(look: VortexLook, content: @Composable () -> Unit) {
    val accent by animateColorAsState(look.theme.accent, tween(400), label = "accent")
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0B0B10),
        background = look.base,
        onBackground = TextPrimary,
        surface = Color(0xFF14151C),
        onSurface = TextPrimary,
        surfaceVariant = Color(0xFF1E2029),
        onSurfaceVariant = TextSecondary,
        surfaceContainerHigh = Color(0xFF1A1B23),
        outline = Color(0x33FFFFFF),
        outlineVariant = Color(0x1FFFFFFF),
        error = Danger,
    )
    MaterialTheme(colorScheme = colors, typography = Typography) {
        // Sem um Surface por fora, o texto herdaria preto como cor padrão.
        CompositionLocalProvider(
            LocalContentColor provides TextPrimary,
            LocalLook provides look,
            content = content,
        )
    }
}
