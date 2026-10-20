package com.example.vortex_player.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.vortex_player.ui.LocalActions
import com.example.vortex_player.ui.LocalPlayer
import com.example.vortex_player.ui.Tab
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.components.GlassChip
import com.example.vortex_player.ui.components.GlassSlider
import com.example.vortex_player.ui.components.ScreenList
import com.example.vortex_player.ui.components.SectionLabel
import com.example.vortex_player.ui.components.SegmentedControl
import com.example.vortex_player.ui.components.TopAction
import com.example.vortex_player.ui.glass.GlassCard
import com.example.vortex_player.ui.glass.LocalGlassBackdrop
import com.example.vortex_player.ui.glass.liquidGlass
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.theme.BackgroundStyle
import com.example.vortex_player.ui.theme.LocalLook
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import com.example.vortex_player.ui.theme.TextTertiary
import com.example.vortex_player.ui.theme.Themes
import com.example.vortex_player.ui.theme.VortexTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val player = LocalPlayer.current
    val actions = LocalActions.current
    val appearance = player.appearance
    val look = LocalLook.current
    val haptics = LocalHapticFeedback.current

    val close by rememberUpdatedState(onClose)
    val closeAction = remember { TopAction(VortexIcons.Close, "Fechar") { close() } }

    ScreenList(title = "Ajustes", action = closeAction, bottomClearance = 24.dp) {
        item(key = "themeLabel") { SectionLabel("Tema") }
        item(key = "themes") {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 20.dp, horizontal = 8.dp)) {
                Themes.chunked(3).forEachIndexed { row, themes ->
                    if (row > 0) Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        themes.forEach { theme ->
                            ThemeSwatch(
                                theme = theme,
                                selected = theme.id == look.theme.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    appearance.setTheme(theme.id)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Completa a última linha para as bolinhas ficarem alinhadas
                        repeat(3 - themes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item(key = "bgLabel") { SectionLabel("Fundo") }
        item(key = "bg") {
            SegmentedControl(
                options = BackgroundStyle.entries.map { it.label },
                selectedIndex = look.background.ordinal,
                onSelect = { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    appearance.setBackground(BackgroundStyle.entries[index])
                },
            )
        }

        item(key = "musicLabel") { SectionLabel("Músicas") }
        item(key = "music") {
            SettingRow(
                icon = VortexIcons.Folder,
                title = "Pasta de músicas",
                subtitle = player.folderName ?: "Nenhuma pasta escolhida",
                onClick = actions.pickFolder,
            )
        }

        item(key = "coversLabel") { SectionLabel("Fotos de capa") }
        item(key = "covers") {
            val folder = player.coverFolderName
            SettingRow(
                icon = VortexIcons.Photo,
                title = folder?.let { if (player.coverFolderEmpty) "$it · sem fotos" else it } ?: "Capa original da música",
                subtitle = if (folder == null) "Toque para usar fotos de uma pasta no lugar das capas."
                else "As capas vêm das fotos dessa pasta, em ordem aleatória.",
                onClick = actions.pickCoverFolder,
            )
        }
        if (player.coverFolderName != null) {
            item(key = "coversClear") {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    GlassChip("Voltar para a capa original", selected = false, onClick = { player.clearCoverFolder() })
                }
            }
        }

        item(key = "notificationsLabel") { SectionLabel("Notificação") }
        item(key = "notifications") { NotificationsRow() }

        item(key = "glassLabel") { SectionLabel("Vidro") }
        item(key = "glass") {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                GlassPreview()
                GlassSlider(
                    value = look.glass,
                    onValueChange = { appearance.setGlass(it) },
                    description = "Intensidade do vidro",
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text("Transparente", style = MaterialTheme.typography.labelMedium, color = TextTertiary, modifier = Modifier.weight(1f))
                    Text("Fosco", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                }
            }
        }

        // Só aparece depois do gesto secreto
        if (player.devMode.enabled) {
            item(key = "devLabel") { SectionLabel("Desenvolvedor") }
            item(key = "dev") {
                SettingRow(
                    icon = VortexIcons.Code,
                    title = "Modo desenvolvedor ativo",
                    subtitle = "Toque para desativar",
                    onClick = { player.devMode.set(false) },
                )
            }
        }
    }
}

/** Linha de ajuste dentro de um cartão: ícone, título, explicação e seta. */
@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, iconTint: Color? = null) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            }
            Icon(VortexIcons.ChevronRight, contentDescription = null, tint = TextTertiary)
        }
    }
}

/**
 * Prévia do vidro: listras coloridas atrás de uma barra de vidro líquido, para ver a
 * refração e o desfoque mudarem enquanto o controle é arrastado.
 */
@Composable
private fun GlassPreview() {
    val look = LocalLook.current
    val layer = rememberLayerBackdrop()
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().layerBackdrop(layer)) {
            drawRect(Brush.linearGradient(look.theme.glows + look.theme.accent))
            val step = 16.dp.toPx()
            val stroke = 5.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    Color.White.copy(alpha = 0.4f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = stroke,
                )
                x += step
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides layer) {
            Row(
                Modifier
                    .liquidGlass(CircleShape)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Tab.entries.forEach {
                    Icon(it.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: VortexTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent,
        label = "ring",
    )
    Column(
        modifier = modifier
            .pressable(onClick, pressedScale = 1.1f)
            .semantics { this.selected = selected; role = Role.RadioButton },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .border(2.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(theme.glows)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(VortexIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Mostra se a notificação de reprodução está liberada. Desligada, um toque pede a permissão;
 * se o Android não deixar pedir de novo, abre os ajustes do app.
 */
@Composable
private fun NotificationsRow() {
    val context = LocalContext.current
    val player = LocalPlayer.current
    // Rechecado a cada segundo para refletir a mudança quando a pessoa volta dos ajustes.
    val enabled by produceState(canNotify(context)) {
        while (true) {
            value = canNotify(context)
            delay(1000)
        }
    }
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) player.updateNotification() else openSettings()
    }

    SettingRow(
        icon = VortexIcons.Bell,
        iconTint = if (enabled) null else TextTertiary,
        title = if (enabled) "Ativada" else "Desativada",
        subtitle = if (enabled) "Capa, nome da música e botões na notificação e na tela de bloqueio."
        else "Toque para mostrar a música tocando na notificação.",
        onClick = {
            when {
                enabled -> openSettings()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED -> request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> openSettings()
            }
        },
    )
}

private fun canNotify(context: android.content.Context): Boolean =
    context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
