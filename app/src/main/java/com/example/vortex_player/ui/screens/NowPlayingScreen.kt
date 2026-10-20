package com.example.vortex_player.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vortex_player.formatTime
import com.example.vortex_player.ui.CoverArt
import com.example.vortex_player.ui.LocalPlayer
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.GlassChip
import com.example.vortex_player.ui.components.GlassSlider
import com.example.vortex_player.ui.components.LocalSheetHost
import com.example.vortex_player.ui.components.LocalTopBar
import com.example.vortex_player.ui.components.TabBarClearance
import com.example.vortex_player.ui.components.TopBarHeight
import com.example.vortex_player.ui.components.TopBarState
import com.example.vortex_player.ui.components.settingsAction
import com.example.vortex_player.ui.glass.GlassIconButton
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import com.example.vortex_player.ui.theme.TextTertiary

@Composable
fun NowPlayingScreen() {
    val player = LocalPlayer.current
    val song = player.current
    val sheets = LocalSheetHost.current
    val bars = WindowInsets.systemBars.asPaddingValues()

    // Esta tela não rola: a barra de cima mostra só o botão de ajustes.
    val topBar = LocalTopBar.current
    val action = settingsAction()
    val entry = remember(action) { TopBarState.Entry("", mutableFloatStateOf(0f), action) }
    DisposableEffect(entry) {
        topBar.entries.add(entry)
        onDispose { topBar.entries.remove(entry) }
    }
    LaunchedEffect(Unit) {
        player.refreshVolume()
        player.updateOutputName()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = bars.calculateTopPadding() + TopBarHeight,
                bottom = bars.calculateBottomPadding() + TabBarClearance - 12.dp,
            ),
    ) {
        // Capa: encolhe um pouco quando pausada, como no Apple Music
        val artScale by animateFloatAsState(
            if (player.isPlaying || song == null) 1f else 0.86f,
            spring(dampingRatio = 0.7f, stiffness = 300f),
            label = "artScale",
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            CoverArt(
                player.art,
                Modifier
                    .size(side)
                    .graphicsLayer { scaleX = artScale; scaleY = artScale }
                    .shadow(24.dp, RoundedCornerShape(26.dp), clip = false),
                RoundedCornerShape(26.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song?.title ?: "Nada tocando",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song?.artist ?: "Escolha uma música na Biblioteca",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val shuffleTint by animateColorAsState(
                if (player.shuffle) MaterialTheme.colorScheme.primary else TextPrimary,
                label = "shuffleTint",
            )
            GlassIconButton(onClick = { player.toggleShuffle() }, size = 42.dp) {
                Icon(VortexIcons.Shuffle, contentDescription = "Ordem aleatória", tint = shuffleTint, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        ProgressBar(
            position = player.positionMs,
            duration = player.durationMs,
            onSeek = { player.seekTo(it) },
        )

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(VortexIcons.SkipPrevious, "Anterior", 40.dp) { player.previous() }
            TransportButton(
                if (player.isPlaying) VortexIcons.Pause else VortexIcons.Play,
                if (player.isPlaying) "Pausar" else "Tocar",
                60.dp,
            ) { player.togglePlay() }
            TransportButton(VortexIcons.SkipNext, "Próxima", 40.dp) { player.next() }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(VortexIcons.VolumeDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
            GlassSlider(
                value = if (player.maxVolume > 0) player.volume / player.maxVolume.toFloat() else 0f,
                onValueChange = { player.changeVolume((it * player.maxVolume).toInt()) },
                description = "Volume",
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Icon(VortexIcons.VolumeUp, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassChip(
                label = player.outputName,
                selected = false,
                onClick = { sheets.openOutputPicker(player) },
                leading = {
                    Icon(VortexIcons.Output, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 24.dp)
            .pressable(onClick, pressedScale = 1.15f, glowShape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(size))
    }
}

/**
 * Barra de progresso no estilo do Apple Music: trilho fino que engrossa enquanto o dedo
 * arrasta; a música só pula quando o dedo solta.
 */
@Composable
private fun ProgressBar(position: Int, duration: Int, onSeek: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDuration by rememberUpdatedState(duration)
    val fraction = if (dragging) dragValue else if (duration > 0) position / duration.toFloat() else 0f
    val trackHeight by animateDpAsState(if (dragging) 12.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "track")

    Column {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        dragging = true
                        dragValue = (down.position.x / width).coerceIn(0f, 1f)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dragValue = (change.position.x / width).coerceIn(0f, 1f)
                            change.consume()
                        }
                        currentOnSeek((dragValue * currentDuration).toInt())
                        dragging = false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = if (dragging) 1f else 0.85f)),
                )
            }
        }
        val shown = (fraction * duration).toInt()
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shown), style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.weight(1f))
            Text("-" + formatTime((duration - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}
