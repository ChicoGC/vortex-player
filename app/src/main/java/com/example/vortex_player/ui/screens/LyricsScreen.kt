package com.example.vortex_player.ui.screens

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vortex_player.Lyrics
import com.example.vortex_player.formatOffset
import com.example.vortex_player.ui.LocalPlayer
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.EmptyHint
import com.example.vortex_player.ui.components.GlassChip
import com.example.vortex_player.ui.components.LocalSheetHost
import com.example.vortex_player.ui.components.ScreenList
import com.example.vortex_player.ui.components.TabBarClearance
import com.example.vortex_player.ui.components.TopAction
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import com.example.vortex_player.ui.theme.TextTertiary

private const val OFFSET_STEP_MS = 500L
private const val USER_SCROLL_PAUSE_MS = 3000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsScreen() {
    val player = LocalPlayer.current
    val song = player.current
    val sheets = LocalSheetHost.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    LaunchedEffect(song?.uri) { player.ensureLyrics() }

    val search = remember(player) { TopAction(VortexIcons.Search, "Buscar outra letra") { sheets.openLyricsSearch(player) } }
    val lyrics = player.lyrics
    val synced = lyrics as? Lyrics.Synced

    // Linha cantada agora (com o ajuste de sincronia aplicado)
    val activeLine by remember(synced) {
        derivedStateOf {
            synced?.lines?.indexOfLast { it.timeMs + player.lyricsOffset <= player.positionMs } ?: -1
        }
    }

    // Segue a linha atual, a menos que a pessoa tenha rolado a letra há pouco
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) lastUserScroll = SystemClock.uptimeMillis()
        }
    }
    LaunchedEffect(activeLine) {
        if (activeLine < 0) return@LaunchedEffect
        if (SystemClock.uptimeMillis() - lastUserScroll < USER_SCROLL_PAUSE_MS) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        // +2: o título grande e a linha de sincronia vêm antes das linhas
        listState.animateScrollToItem(activeLine + 2, -(viewport / 3))
    }

    ScreenList(
        title = "Letra",
        subtitle = song?.let { "${it.title} · ${it.artist}" },
        action = if (song != null) search else null,
        bottomClearance = TabBarClearance + MiniPlayerClearance + 120.dp,
        listState = listState,
    ) {
        when {
            song == null -> item(key = "none") { EmptyHint("Toque numa música para ver a letra") }
            lyrics == null || player.lyricsLoading -> item(key = "loading") { EmptyHint("Procurando letra…") }
            synced != null -> {
                item(key = "sync") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassChip("−0,5s", selected = false, onClick = { player.changeLyricsOffset(player.lyricsOffset - OFFSET_STEP_MS) })
                        Text(formatOffset(player.lyricsOffset), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        GlassChip("+0,5s", selected = false, onClick = { player.changeLyricsOffset(player.lyricsOffset + OFFSET_STEP_MS) })
                    }
                }
                itemsIndexed(synced.lines) { index, line ->
                    val color by animateColorAsState(
                        if (index == activeLine) TextPrimary else TextTertiary,
                        tween(300),
                        label = "lyricColor",
                    )
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val target = (line.timeMs + player.lyricsOffset).coerceAtLeast(0L)
                                    player.seekTo(target.toInt())
                                    lastUserScroll = 0L
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    player.syncLineToNow(line)
                                },
                            )
                            .padding(horizontal = 4.dp, vertical = 9.dp),
                    )
                }
            }
            lyrics is Lyrics.Plain -> itemsIndexed(lyrics.lines) { _, line ->
                Text(line, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
            }
            else -> item(key = "status") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyHint(
                        when (lyrics) {
                            Lyrics.Instrumental -> "Música instrumental"
                            Lyrics.Offline -> "Sem conexão com a internet"
                            else -> "Letra não encontrada"
                        },
                    )
                    if (lyrics != Lyrics.Instrumental) {
                        GlassChip("Tentar de novo", selected = true, onClick = { player.ensureLyrics(forceRefresh = true) })
                    }
                }
            }
        }
    }
}
