package com.example.vortex_player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.vortex_player.PlayerController
import com.example.vortex_player.ui.components.LocalOpenSettings
import com.example.vortex_player.ui.components.LocalSheetHost
import com.example.vortex_player.ui.components.LocalTopBar
import com.example.vortex_player.ui.components.SheetHost
import com.example.vortex_player.ui.components.SheetLayer
import com.example.vortex_player.ui.components.TopBarState
import com.example.vortex_player.ui.glass.GlassBackdrop
import com.example.vortex_player.ui.glass.LiquidSpring
import com.example.vortex_player.ui.glass.LocalBackgroundBackdrop
import com.example.vortex_player.ui.glass.LocalGlassBackdrop
import com.example.vortex_player.ui.glass.liquidGlass
import com.example.vortex_player.ui.glass.liquidStretch
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.glass.rememberLiquidOffset
import com.example.vortex_player.ui.screens.LibraryScreen
import com.example.vortex_player.ui.screens.LyricsScreen
import com.example.vortex_player.ui.screens.NowPlayingScreen
import com.example.vortex_player.ui.screens.SettingsScreen
import com.example.vortex_player.ui.screens.WidgetsScreen
import com.example.vortex_player.ui.theme.LocalLook
import com.example.vortex_player.ui.theme.PlayerTheme
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Library("Biblioteca", VortexIcons.Library, VortexIcons.LibraryFilled),
    Playing("Tocando", VortexIcons.NowPlaying, VortexIcons.NowPlayingFilled),
    Lyrics("Letra", VortexIcons.Lyrics, VortexIcons.Lyrics),
    Widgets("Widgets", VortexIcons.Widgets, VortexIcons.WidgetsFilled),
}

/** What the screens need from the Activity: the system folder pickers. */
class PlayerActions(val pickFolder: () -> Unit, val pickCoverFolder: () -> Unit)

val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("LocalPlayer não definido") }
val LocalActions = staticCompositionLocalOf<PlayerActions> { error("LocalActions não definido") }

@Composable
fun PlayerRoot(controller: PlayerController, actions: PlayerActions) {
    PlayerTheme(controller.appearance.look) {
        CompositionLocalProvider(LocalPlayer provides controller, LocalActions provides actions) {
            RootContent()
        }
    }
}

private val BottomOrigin = TransformOrigin(0.5f, 1f)

@Composable
private fun RootContent() {
    val player = LocalPlayer.current
    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val sheets = remember { SheetHost() }
    val topBar = remember { TopBarState() }
    val openSettings = remember { { settingsOpen = true } }

    // O que os elementos de vidro líquido refratam: o fundo e o conteúdo da tela.
    val backgroundLayer = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val glassBackdrop = rememberCombinedBackdrop(backgroundLayer, contentLayer)

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != Tab.Library) { tab = Tab.Library }

    CompositionLocalProvider(
        LocalGlassBackdrop provides glassBackdrop,
        LocalBackgroundBackdrop provides backgroundLayer,
        LocalOpenSettings provides openSettings,
        LocalTopBar provides topBar,
        LocalSheetHost provides sheets,
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackdrop(LocalLook.current, player.backdrop, Modifier.fillMaxSize().layerBackdrop(backgroundLayer))

            // null representa a tela de ajustes.
            val page: Tab? = if (settingsOpen) null else tab
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize().layerBackdrop(contentLayer),
                transitionSpec = {
                    when {
                        targetState == null ->
                            (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 10 } +
                                scaleIn(spring(dampingRatio = 0.85f, stiffness = 420f), initialScale = 0.96f) +
                                fadeIn(tween(200))) togetherWith fadeOut(tween(140))
                        initialState == null ->
                            (fadeIn(tween(220)) + scaleIn(spring(stiffness = 420f), initialScale = 0.98f)) togetherWith
                                (slideOutVertically(tween(220)) { it / 10 } + fadeOut(tween(160)))
                        else ->
                            (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 500f), initialScale = 0.985f)) togetherWith
                                fadeOut(tween(120))
                    }
                },
                label = "page",
            ) { current ->
                when (current) {
                    null -> SettingsScreen(onClose = { settingsOpen = false })
                    Tab.Library -> LibraryScreen(onOpenPlaying = { tab = Tab.Playing })
                    Tab.Playing -> NowPlayingScreen()
                    Tab.Lyrics -> LyricsScreen()
                    Tab.Widgets -> WidgetsScreen()
                }
            }

            TopBar(topBar, Modifier.align(Alignment.TopCenter))

            // A barra se recolhe para baixo ao abrir os ajustes, como no iOS.
            AnimatedVisibility(
                visible = !settingsOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.8f, transformOrigin = BottomOrigin),
                exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.85f, transformOrigin = BottomOrigin),
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Mini player acima da barra (como no Apple Music), fora da tela "Tocando"
                    AnimatedVisibility(
                        visible = player.current != null && tab != Tab.Playing,
                        enter = fadeIn(tween(180)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 420f)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
                    ) {
                        MiniPlayer(onOpen = { tab = Tab.Playing }, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassTabBar(selected = tab, onSelect = { tab = it })
                        PlayButton(modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }

            SheetLayer(sheets)
        }
    }
}

private val TabItemWidth = 64.dp
private val TabItemHeight = 52.dp

/** Botão redondo de vidro na cor do tema, ao lado da barra: tocar / pausar. */
@Composable
private fun PlayButton(modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    Box(
        modifier
            .size(TabItemHeight + 10.dp)
            .pressable({ player.togglePlay() }, pressedScale = 1.12f, glowShape = CircleShape)
            .liquidGlass(CircleShape, tint = MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (player.isPlaying) VortexIcons.Pause else VortexIcons.Play,
            contentDescription = if (player.isPlaying) "Pausar" else "Tocar",
            tint = TextPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Música tocando agora, numa cápsula de vidro líquido. Tocar abre a tela "Tocando". */
@Composable
private fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val song = player.current ?: return
    val shape = CircleShape
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(62.dp)
            .pressable(onOpen, pressedScale = 1.03f, glowShape = shape)
            .liquidGlass(shape, minOpacity = 0.3f)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(player.art, Modifier.size(46.dp), RoundedCornerShape(23.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(44.dp)
                .pressable({ player.next() }, pressedScale = 1.15f, glowShape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VortexIcons.SkipNext, contentDescription = "Próxima", modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Barra de abas de vidro líquido. A pílula de seleção estica como uma gota ao trocar de
 * aba, cresce enquanto o dedo está na barra e pode ser arrastada entre as abas.
 */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { TabItemWidth.toPx() }
    val tabs = Tab.entries
    val maxOffset = itemPx * (tabs.size - 1)

    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var hoverIndex by remember { mutableIntStateOf(selected.ordinal) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val offset = rememberLiquidOffset(selected.ordinal * itemPx, hold = dragging)
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "tabLift",
    )
    val accent = MaterialTheme.colorScheme.primary
    val activeIndex = if (dragging) hoverIndex else selected.ordinal

    Box(modifier.liquidGlass(CircleShape).padding(5.dp)) {
        // Pílula de seleção neutra (como no iOS): ao ser tocada "levanta" e vira uma lente mais clara.
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(TabItemWidth, TabItemHeight)
                .liquidStretch(offset, lift = 1f + 0.12f * lift)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = lerp(0.13f, 0.08f, lift)))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)))
                .border(0.8.dp, Color.White.copy(alpha = lerp(0.14f, 0.4f, lift)), CircleShape),
        )
        Row(
            Modifier.pointerInput(itemPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var isDrag = false
                    var x = down.position.x
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!isDrag && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            isDrag = true
                            dragging = true
                        }
                        if (isDrag) {
                            x = change.position.x
                            val follow = (x - itemPx / 2f).coerceIn(0f, maxOffset)
                            scope.launch { offset.animateTo(follow, spring(dampingRatio = 0.8f, stiffness = 1400f)) }
                            val index = (x / itemPx).toInt().coerceIn(0, tabs.lastIndex)
                            if (index != hoverIndex) {
                                hoverIndex = index
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            change.consume()
                        }
                    }
                    val index = (x / itemPx).toInt().coerceIn(0, tabs.lastIndex)
                    pressed = false
                    dragging = false
                    hoverIndex = index
                    if (tabs[index] != currentSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        currentOnSelect(tabs[index])
                    } else {
                        scope.launch { offset.animateTo(index * itemPx, LiquidSpring) }
                    }
                }
            },
            horizontalArrangement = Arrangement.Start,
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == activeIndex
                val color by animateColorAsState(if (active) accent else TextPrimary.copy(alpha = 0.72f), label = "tabColor")
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                    label = "tabIconScale",
                )
                Box(
                    modifier = Modifier
                        .size(TabItemWidth, TabItemHeight)
                        .semantics {
                            role = Role.Tab
                            this.selected = tab == selected
                            contentDescription = tab.label
                            onClick { onSelect(tab); true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (active) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                }
            }
        }
    }
}
