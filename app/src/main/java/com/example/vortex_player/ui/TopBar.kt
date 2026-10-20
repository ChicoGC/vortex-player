package com.example.vortex_player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vortex_player.ui.components.TopAction
import com.example.vortex_player.ui.components.TopBarHeight
import com.example.vortex_player.ui.components.TopBarState
import com.example.vortex_player.ui.glass.liquidGlass
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.theme.LocalLook

/**
 * Barra superior no estilo do iOS 26: botão de vidro líquido no canto, título pequeno que
 * aparece quando a tela rola e o "scroll edge effect" (o conteúdo escurece ao
 * passar por baixo da borda de cima).
 */
@Composable
fun TopBar(state: TopBarState, modifier: Modifier = Modifier) {
    val entry = state.current ?: return
    val collapse = entry.collapse.value
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxWidth().height(top + TopBarHeight)) {
        if (collapse > 0f) {
            ScrollEdge(
                strength = collapse,
                solidHeight = top + TopBarHeight,
                modifier = Modifier.fillMaxWidth().height(top + TopBarHeight + 28.dp),
            )
        }

        Text(
            entry.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 17.dp)
                .graphicsLayer {
                    alpha = collapse
                    translationY = (1f - collapse) * 6.dp.toPx()
                },
        )

        AnimatedContent(
            targetState = entry.action,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 6.dp),
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
            },
            contentKey = { it?.description },
            label = "topAction",
        ) { action ->
            if (action != null) TopGlassButton(action)
        }
    }
}

/** Botão redondo de vidro líquido da barra superior (44 dp, como no iOS). */
@Composable
private fun TopGlassButton(action: TopAction) {
    Box(
        Modifier
            .size(44.dp)
            .pressable(action.onClick, pressedScale = 1.14f, glowShape = CircleShape)
            .liquidGlass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(action.icon, contentDescription = action.description, modifier = Modifier.size(21.dp))
    }
}

/**
 * Faixa no topo que escurece o conteúdo que passa por baixo, sumindo suavemente para baixo.
 *
 * Antes desfocava o conteúdo, mas o desfoque numa faixa da largura da tela custava ~19 ms
 * por quadro na GPU de celulares mais simples (rolagem a ~30 fps). O degradê custa quase nada.
 */
@Composable
private fun ScrollEdge(strength: Float, solidHeight: Dp, modifier: Modifier) {
    val base = LocalLook.current.base
    Box(
        modifier.drawBehind {
            val solid = solidHeight.toPx() - 6.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    0f to base.copy(alpha = 0.92f * strength),
                    (solid / size.height).coerceIn(0f, 1f) to base.copy(alpha = 0.8f * strength),
                    1f to Color.Transparent,
                ),
            )
        },
    )
}
