package com.example.vortex_player.ui.screens

import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import com.example.vortex_player.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vortex_player.Song
import com.example.vortex_player.ui.CoverArt
import com.example.vortex_player.ui.LocalActions
import com.example.vortex_player.ui.LocalPlayer
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.EmptyHint
import com.example.vortex_player.ui.components.GlassChip
import com.example.vortex_player.ui.components.ScreenList
import com.example.vortex_player.ui.components.TabBarClearance
import com.example.vortex_player.ui.glass.RowPosition
import com.example.vortex_player.ui.glass.contentSurface
import com.example.vortex_player.ui.glass.groupDivider
import com.example.vortex_player.ui.glass.groupShape
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.glass.rowPosition
import com.example.vortex_player.ui.rememberSongArt
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary

/** Espaço extra no fim da lista para o mini player, que fica acima da barra de abas. */
val MiniPlayerClearance = 72.dp

@Composable
fun LibraryScreen(onOpenPlaying: () -> Unit) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val message = player.libraryMessage
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    ScreenList(
        title = "Biblioteca",
        subtitle = player.folderName ?: "Nenhuma pasta escolhida",
        logo = painterResource(R.drawable.vortex_logo),
        onLogoTap = {
            if (player.devMode.onSecretTap()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "Modo desenvolvedor ativado", Toast.LENGTH_SHORT).show()
            }
        },
        bottomClearance = TabBarClearance + if (player.current != null) MiniPlayerClearance else 0.dp,
    ) {
        if (message != null) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(VortexIcons.Music, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                    EmptyHint(message)
                    if (message != "Carregando músicas…") {
                        GlassChip("Escolher pasta", selected = true, onClick = actions.pickFolder)
                    }
                }
            }
        } else {
            val songs = player.songs
            itemsIndexed(songs, key = { _, song -> song.uri.toString() }) { index, song ->
                SongRow(
                    song = song,
                    playing = index == player.index,
                    position = rowPosition(index, songs.size),
                    onClick = {
                        if (index == player.index) onOpenPlaying() else player.playAt(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, playing: Boolean, position: RowPosition, onClick: () -> Unit) {
    val shape = groupShape(position)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .groupDivider(position, startInset = 74.dp)
            .pressable(onClick, pressedScale = 1f, glowShape = shape)
            .contentSurface(shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(rememberSongArt(song.uri), Modifier.size(48.dp), RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (playing) {
            Box(Modifier.padding(start = 8.dp)) {
                Icon(VortexIcons.NowPlayingFilled, contentDescription = "Tocando", tint = accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

