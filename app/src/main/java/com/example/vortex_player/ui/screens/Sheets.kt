package com.example.vortex_player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vortex_player.LrcLib
import com.example.vortex_player.PlayerController
import com.example.vortex_player.formatTime
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.EmptyHint
import com.example.vortex_player.ui.components.FieldBox
import com.example.vortex_player.ui.components.SheetHost
import com.example.vortex_player.ui.components.SheetScaffold
import com.example.vortex_player.ui.components.VortexTextField
import com.example.vortex_player.ui.glass.GlassIconButton
import com.example.vortex_player.ui.glass.RowPosition
import com.example.vortex_player.ui.glass.contentSurface
import com.example.vortex_player.ui.glass.groupDivider
import com.example.vortex_player.ui.glass.groupShape
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.glass.rowPosition
import com.example.vortex_player.ui.theme.TextSecondary

/** Escolher onde o som sai: celular, fone com fio, Bluetooth… */
fun SheetHost.openOutputPicker(player: PlayerController) = open { dismiss ->
    val devices = remember { player.outputDevices() }
    SheetScaffold(title = "Saída de áudio", onClose = dismiss) {
        Column {
            devices.forEachIndexed { index, device ->
                val label = player.deviceLabel(device)
                GroupRow(
                    position = rowPosition(index, devices.size),
                    dividerInset = 50.dp,
                    onClick = {
                        player.selectOutput(device)
                        dismiss()
                    },
                ) {
                    Icon(VortexIcons.Output, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    if (label == player.outputName) {
                        Icon(VortexIcons.Check, contentDescription = "Em uso", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** Procurar outra versão da letra no LRCLIB. */
fun SheetHost.openLyricsSearch(player: PlayerController) = open { dismiss ->
    var query by remember { mutableStateOf(player.lyricsQuery()) }
    var results by remember { mutableStateOf<List<LrcLib.Record>?>(null) }
    var status by remember { mutableStateOf<String?>("Buscando…") }
    var searchId by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current

    LaunchedEffect(searchId) {
        val q = query.trim()
        if (q.isEmpty()) return@LaunchedEffect
        val id = searchId
        status = "Buscando…"
        results = null
        player.searchLyrics(q) { records ->
            if (id != searchId) return@searchLyrics
            when {
                records == null -> status = "Sem conexão com a internet"
                records.isEmpty() -> status = "Nenhum resultado. Tente outro nome."
                else -> {
                    status = null
                    results = records
                }
            }
        }
    }

    SheetScaffold(title = "Buscar letra", onClose = dismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldBox(Modifier.weight(1f)) {
                VortexTextField(query, { query = it }, placeholder = "Artista e música", singleLine = true)
            }
            GlassIconButton(
                onClick = {
                    focus.clearFocus()
                    searchId++
                },
                size = 48.dp,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(VortexIcons.Search, contentDescription = "Buscar", modifier = Modifier.size(22.dp))
            }
        }
        status?.let { EmptyHint(it) }
        results?.let { records ->
            Column(Modifier.padding(top = 8.dp)) {
                records.forEachIndexed { index, record ->
                    val kind = when {
                        !record.synced.isNullOrBlank() -> "Sincronizada"
                        record.instrumental -> "Instrumental"
                        else -> "Sem sincronia"
                    }
                    GroupRow(
                        position = rowPosition(index, records.size),
                        dividerInset = 16.dp,
                        onClick = {
                            player.chooseLyrics(record)
                            dismiss()
                        },
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.trackName ?: "Sem título", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(record.artistName, formatTime((record.duration * 1000).toInt()), kind).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Uma linha de lista agrupada (iOS): só os cantos de fora arredondados, divisória fina entre linhas. */
@Composable
fun GroupRow(
    position: RowPosition,
    dividerInset: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = groupShape(position)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .groupDivider(position, dividerInset)
                .pressable(onClick, pressedScale = 1f, glowShape = shape)
                .contentSurface(shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
