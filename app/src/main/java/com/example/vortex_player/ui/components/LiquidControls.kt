package com.example.vortex_player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.vortex_player.ui.glass.glass
import com.example.vortex_player.ui.glass.liquidStretch
import com.example.vortex_player.ui.glass.rememberLiquidOffset
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextTertiary
import kotlin.math.roundToInt

/** Seletor em cápsula. A pílula escorrega até a opção escolhida, esticando como uma gota. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .glass(CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val segmentPx = with(LocalDensity.current) { segment.toPx() }
        val offset = rememberLiquidOffset(segmentPx * selectedIndex)
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .width(segment)
                .fillMaxHeight()
                .liquidStretch(offset)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selectedIndex) TextPrimary else TextTertiary,
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(index, selectedIndex) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val up = waitForUpOrCancellation()
                                if (up != null && index != selectedIndex) onSelect(index)
                            }
                        }
                        .semantics {
                            role = Role.Tab
                            selected = index == selectedIndex
                            onClick { onSelect(index); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

private val ThumbWidth = 38.dp
private val ThumbHeight = 24.dp

/**
 * Controle deslizante no estilo do iOS 26/27: trilho fino e um "botão" em cápsula que,
 * enquanto é arrastado, cresce e vira vidro transparente.
 *
 * @param onValueChange chamado a cada movimento (para prévia ao vivo).
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    // Valor local para o botão responder na hora, sem esperar o dado salvo voltar.
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) local = value }
    val currentOnChange by rememberUpdatedState(onValueChange)

    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f),
        label = "thumbLift",
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                description?.let { contentDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(local, 0f..1f)
                setProgress { target ->
                    local = target.coerceIn(0f, 1f)
                    currentOnChange(local)
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val thumbPx = with(density) { ThumbWidth.toPx() }
        val trackPx = with(density) { maxWidth.toPx() } - thumbPx

        fun valueAt(x: Float) = ((x - thumbPx / 2f) / trackPx).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(trackPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        fun set(x: Float) {
                            val next = valueAt(x)
                            // Um toquinho ao bater nas pontas, como no iOS.
                            if ((next == 0f || next == 1f) && next != local) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            local = next
                            currentOnChange(next)
                        }
                        set(down.position.x)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            set(change.position.x)
                            change.consume()
                        }
                        dragging = false
                    }
                },
        )

        // Trilho
        Box(
            Modifier
                .padding(horizontal = ThumbWidth / 2)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(local)
                    .fillMaxHeight()
                    .background(accent),
            )
        }

        // Botão: branco parado; ao arrastar cresce e fica translúcido, como uma lente.
        Box(
            Modifier
                .offset { IntOffset((local * trackPx).roundToInt(), 0) }
                .size(ThumbWidth, ThumbHeight)
                .graphicsLayer {
                    val s = 1f + 0.45f * lift
                    scaleX = s
                    scaleY = s
                }
                .shadow(lerp(4f, 10f, lift).dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = lerp(1f, 0.22f, lift)))
                .border(1.dp, Color.White.copy(alpha = lerp(0f, 0.7f, lift)), CircleShape),
        )
    }
}
