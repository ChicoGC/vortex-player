package com.example.vortex_player.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.vortex_player.DeviceBatteries
import com.example.vortex_player.DeviceBattery
import com.example.vortex_player.ui.LocalPlayer
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.EmptyHint
import com.example.vortex_player.ui.components.GlassChip
import com.example.vortex_player.ui.components.LocalSheetHost
import com.example.vortex_player.ui.components.SheetHost
import com.example.vortex_player.ui.components.SheetScaffold
import com.example.vortex_player.ui.components.ScreenList
import com.example.vortex_player.ui.components.TabBarClearance
import com.example.vortex_player.ui.glass.LocalBackgroundBackdrop
import com.example.vortex_player.ui.glass.liquidGlass
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.glass.rowPosition
import com.example.vortex_player.ui.theme.Danger
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun WidgetsScreen() {
    ScreenList(
        title = "Widgets",
        bottomClearance = TabBarClearance + MiniPlayerClearance,
    ) {
        item(key = "batteries") { BatteriesWidget() }
    }
}

private val WidgetShape = RoundedCornerShape(34.dp)
private val WidgetGap = 14.dp

/**
 * Widget de baterias no estilo do iOS: até quatro aparelhos (o celular e os conectados por
 * Bluetooth), cada um com um anel que mostra a carga e o ícone do aparelho no meio.
 */
@Composable
private fun BatteriesWidget() {
    val context = LocalContext.current
    val devicesState = remember { mutableStateOf(DeviceBatteries.read(context)) }
    var devices by devicesState
    val sheets = LocalSheetHost.current
    var allowed by remember { mutableStateOf(DeviceBatteries.hasBluetoothPermission(context)) }
    val askBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        devices = DeviceBatteries.read(context)
    }

    // Atualiza na hora quando o sistema avisa, e a cada 30 s (alguns fones não avisam)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                DeviceBatteries.onBroadcast(intent)
                devices = DeviceBatteries.read(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, DeviceBatteries.updates, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(context) {
        while (true) {
            delay(30_000)
            devices = DeviceBatteries.read(context)
        }
    }

    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Tamanho de um widget pequeno do iOS: metade da largura, quadrado
            val side = (maxWidth - WidgetGap) / 2
            BatteryGrid(
                devices.take(4),
                onClick = { sheets.openDevices(devicesState) },
                modifier = Modifier.width(side).aspectRatio(1f),
            )
        }
        if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(12.dp))
            GlassChip(
                label = "Mostrar fones e relógios",
                selected = false,
                onClick = { askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                leading = {
                    Icon(VortexIcons.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun BatteryGrid(devices: List<DeviceBattery>, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            // Tocar abre a lista de aparelhos; o widget afunda um pouco, como no iOS
            .pressable(onClick, pressedScale = 0.96f, glowShape = WidgetShape)
            // Mesmo vidro líquido da barra de abas: segue o ajuste "Vidro" (transparente ↔ fosco)
            .liquidGlass(WidgetShape, backdrop = LocalBackgroundBackdrop.current)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0 until 2) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0 until 2) {
                        // Vagas sem aparelho ficam só com o anel apagado, como no iOS
                        BatteryRing(devices.getOrNull(row * 2 + col), accent, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryRing(device: DeviceBattery?, accent: Color, modifier: Modifier) {
    val level = device?.level
    val progress by animateFloatAsState((level ?: 0) / 100f, tween(700), label = "battery")
    val low = level != null && level <= 20 && device.charging.not()
    val color = if (low) Danger else accent
    // Trilho: a cor do tema bem apagada, para combinar com qualquer tema
    val track = accent.copy(alpha = 0.16f).compositeOver(Color.White.copy(alpha = 0.06f))
    val charging = device?.charging == true

    BoxWithConstraints(
        modifier.semantics {
            contentDescription = when {
                device == null -> "Vaga vazia"
                level == null -> "${device.name}: bateria desconhecida"
                else -> "${device.name}: $level%" + if (charging) ", carregando" else ""
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val ring = minOf(maxWidth, maxHeight)
        val strokeDp = ring * 0.095f
        val boltSize = strokeDp * 2f
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = strokeDp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                // Carregando: abre um espaço no topo para o raio, como no iOS
                val gap = if (charging) 36f else 0f
                drawArc(track, -90f + gap / 2, 360f - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (progress > 0f) {
                    val sweep = (360f - gap) * progress
                    drawArc(color, -90f + gap / 2, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (charging) {
                // Raio centralizado sobre a linha do anel, no espaço aberto
                Icon(
                    VortexIcons.Bolt,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = strokeDp / 2 - boltSize / 2)
                        .size(boltSize),
                )
            }
            if (device != null) {
                // Os AirPods ocupam menos espaço no desenho, então ficam um pouco maiores
                val scale = if (device.kind == DeviceBattery.Kind.Headphones) 0.38f else 0.32f
                Icon(iconFor(device.kind), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(ring * scale))
            }
        }
    }
}

private fun iconFor(kind: DeviceBattery.Kind): ImageVector = when (kind) {
    DeviceBattery.Kind.Phone -> VortexIcons.Smartphone
    DeviceBattery.Kind.Headphones -> VortexIcons.Earbuds
    DeviceBattery.Kind.Watch -> VortexIcons.Watch
    DeviceBattery.Kind.Other -> VortexIcons.Bluetooth
}

/** Lista dos aparelhos do widget, com nome, tipo e carga. Acompanha as atualizações ao vivo. */
private fun SheetHost.openDevices(devices: State<List<DeviceBattery>>) = open { dismiss ->
    val devMode = LocalPlayer.current.devMode
    val sheets = LocalSheetHost.current
    SheetScaffold(title = "Aparelhos conectados", onClose = dismiss) {
        val list = devices.value
        Column {
            list.forEachIndexed { index, device ->
                // Só no modo desenvolvedor: celular e AirPods abrem a ficha com o vídeo deles
                val details = if (devMode.enabled && hasDetails(device)) {
                    { sheets.openDeviceDetails(device) }
                } else null
                GroupRow(position = rowPosition(index, list.size), dividerInset = 58.dp, onClick = details) {
                    Icon(
                        iconFor(device.kind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(kindLabel(device.kind), "Carregando".takeIf { device.charging }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                    }
                    val level = device.level
                    Text(
                        if (level != null) "$level%" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (level != null && level <= 20 && !device.charging) Danger else TextPrimary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    if (details != null) {
                        Icon(VortexIcons.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
                }
            }
        }
        if (list.size == 1) {
            EmptyHint("Nenhum aparelho Bluetooth conectado")
        }
    }
}

private fun kindLabel(kind: DeviceBattery.Kind): String = when (kind) {
    DeviceBattery.Kind.Phone -> "Este celular"
    DeviceBattery.Kind.Headphones -> "Fone de ouvido"
    DeviceBattery.Kind.Watch -> "Relógio"
    DeviceBattery.Kind.Other -> "Bluetooth"
}
