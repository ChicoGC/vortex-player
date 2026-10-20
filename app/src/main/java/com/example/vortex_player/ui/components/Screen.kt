package com.example.vortex_player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.theme.TextSecondary
import com.example.vortex_player.ui.theme.TextTertiary

/** Espaço reservado no fim das listas para a barra de abas flutuante. */
val TabBarClearance = 110.dp

/** Altura da barra superior (abaixo da barra de status), onde ficam os botões de vidro. */
val TopBarHeight = 56.dp

/** Abre a tela de ajustes; fornecido pela raiz do app. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** Botão do canto superior direito, desenhado pela raiz como um botão de vidro líquido. */
class TopAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/**
 * O que a barra superior (desenhada na raiz, fora do conteúdo, para poder refratá-lo) deve
 * mostrar: título compacto, quanto a tela já rolou e o botão da direita. Cada tela registra
 * o seu; vale o último registrado (a tela que está entrando).
 */
@Stable
class TopBarState {
    class Entry(val title: String, val collapse: State<Float>, val action: TopAction?)

    internal val entries = mutableStateListOf<Entry>()
    val current: Entry? get() = entries.lastOrNull()
}

val LocalTopBar = staticCompositionLocalOf { TopBarState() }

/** Ação padrão das abas: abrir os ajustes. */
@Composable
fun settingsAction(): TopAction {
    val open by rememberUpdatedState(LocalOpenSettings.current)
    return remember { TopAction(VortexIcons.Settings, "Ajustes") { open() } }
}

/**
 * Lista rolável padrão das telas, no layout do iOS 26: barra com botões de vidro em cima,
 * título grande logo abaixo (que rola com o conteúdo) e título pequeno na barra ao rolar.
 */
@Composable
fun ScreenList(
    title: String,
    subtitle: String? = null,
    logo: Painter? = null,
    onLogoTap: (() -> Unit)? = null,
    action: TopAction? = settingsAction(),
    bottomClearance: Dp = TabBarClearance,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    val collapseDistance = with(LocalDensity.current) { 40.dp.toPx() }
    val collapse = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val topBar = LocalTopBar.current
    val entry = remember(title, action) { TopBarState.Entry(title, collapse, action) }
    DisposableEffect(entry) {
        topBar.entries.add(entry)
        onDispose { topBar.entries.remove(entry) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = bars.calculateTopPadding() + TopBarHeight,
            bottom = bars.calculateBottomPadding() + bottomClearance,
        ),
    ) {
        item(key = "header") { LargeTitle(title, subtitle, logo, onLogoTap) }
        content()
    }
}

@Composable
private fun LargeTitle(title: String, subtitle: String?, logo: Painter?, onLogoTap: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Logo nas cores originais, sem mudar com o tema
            if (logo != null) {
                // Toque sem nenhum efeito visual (é usado pelo gesto secreto)
                val tap = if (onLogoTap == null) Modifier else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogoTap,
                )
                Image(logo, contentDescription = null, modifier = Modifier.size(40.dp).then(tap))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, style = MaterialTheme.typography.displaySmall)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

/** Respiro vertical entre blocos da lista. */
fun LazyListScope.gap(key: String, height: Dp = 12.dp) {
    item(key = key) { Spacer(Modifier.height(height)) }
}

/** Cabeçalho de seção, como os das listas agrupadas do iOS. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Mensagem discreta para listas vazias, sem cartão em volta. */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
    )
}
