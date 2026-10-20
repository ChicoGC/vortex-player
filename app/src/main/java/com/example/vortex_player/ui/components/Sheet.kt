package com.example.vortex_player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.glass.GlassIconButton
import com.example.vortex_player.ui.glass.liquidGlass
import com.example.vortex_player.ui.glass.pressable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

/**
 * Pilha de folhas (bottom sheets) abertas por cima do app. Fica na raiz para cobrir
 * também a barra de abas. Uma folha pode abrir outra por cima (ex.: criar matéria
 * de dentro do editor de tarefa).
 */
@Stable
class SheetHost {
    internal class Entry(val id: Long, val content: @Composable (dismiss: () -> Unit) -> Unit) {
        val visible = MutableTransitionState(false).apply { targetState = true }
        fun dismiss() { visible.targetState = false }
    }

    internal val entries = mutableStateListOf<Entry>()
    private var nextId = 0L

    /** Abre uma folha. O conteúdo recebe `dismiss` para fechá-la. */
    fun open(content: @Composable (dismiss: () -> Unit) -> Unit) {
        entries.add(Entry(nextId++, content))
    }

    internal fun dismissTop() {
        entries.lastOrNull { it.visible.targetState }?.dismiss()
    }

    internal val anyOpen: Boolean get() = entries.any { it.visible.targetState }
}

val LocalSheetHost = staticCompositionLocalOf<SheetHost> { error("LocalSheetHost não definido") }

/** Gesto de arrastar a folha para baixo; aplicado na alça e no cabeçalho. */
private val LocalSheetDrag = staticCompositionLocalOf<Modifier> { Modifier }

private val SheetShape = RoundedCornerShape(34.dp)
private val SheetSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
private val BottomCenterOrigin = TransformOrigin(0.5f, 1f)

@Composable
fun SheetLayer(host: SheetHost) {
    BackHandler(enabled = host.anyOpen) { host.dismissTop() }

    host.entries.forEach { entry ->
        key(entry.id) {
            LaunchedEffect(entry.visible.isIdle, entry.visible.currentState) {
                if (entry.visible.isIdle && !entry.visible.currentState) host.entries.remove(entry)
            }
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(entry.visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = entry::dismiss,
                            ),
                    )
                }
                // A folha sobe com uma mola e cresce a partir de baixo, como no iOS.
                AnimatedVisibility(
                    visibleState = entry.visible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } +
                        scaleIn(SheetSpring, initialScale = 0.9f, transformOrigin = BottomCenterOrigin) +
                        fadeIn(tween(140)),
                    exit = slideOutVertically(tween(240)) { it / 2 } +
                        scaleOut(tween(240), targetScale = 0.92f, transformOrigin = BottomCenterOrigin) +
                        fadeOut(tween(200)),
                ) {
                    SheetSurface(onDismiss = entry::dismiss) { entry.content(entry::dismiss) }
                }
            }
        }
    }
}

@Composable
private fun SheetSurface(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp - top - 16.dp
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissDistance = with(density) { 120.dp.toPx() }

    val drag = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            // Para baixo segue o dedo; para cima resiste (efeito elástico).
            val next = dragOffset.value + if (dragOffset.value + delta < 0f) delta * 0.15f else delta
            scope.launch { dragOffset.snapTo(next) }
        },
        onDragStopped = { velocity ->
            if (dragOffset.value > dismissDistance || velocity > 1800f) onDismiss()
            else dragOffset.animateTo(0f, SheetSpring)
        },
    )

    Box(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .graphicsLayer { translationY = dragOffset.value }
            .heightIn(max = maxHeight)
            // Impede que toques na folha cheguem ao fundo escurecido (que fecharia a folha).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .liquidGlass(SheetShape, minOpacity = 0.74f, lens = 0.7f)
            .clip(SheetShape),
    ) {
        CompositionLocalProvider(LocalSheetDrag provides drag) {
            Column {
                // Alça, como nas folhas do iOS.
                Box(drag.fillMaxWidth().height(20.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.28f), CircleShape),
                    )
                }
                content()
            }
        }
    }
}

/** Botão de confirmar no canto direito do cabeçalho da folha (o "✓" do iOS). */
class SheetAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Estrutura padrão de uma folha, como no iOS 26: ✕ de vidro à esquerda, título no centro,
 * botão de confirmar na cor do tema à direita, e o conteúdo rolável embaixo.
 */
@Composable
fun SheetScaffold(
    title: String,
    onClose: () -> Unit,
    action: SheetAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            LocalSheetDrag.current.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(onClick = onClose, size = 42.dp) {
                Icon(VortexIcons.Close, contentDescription = "Fechar", modifier = Modifier.size(19.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (action != null) ProminentButton(action) else Spacer(Modifier.size(42.dp))
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Botão redondo cheio na cor do tema, para a ação principal. */
@Composable
private fun ProminentButton(action: SheetAction) {
    val accent = MaterialTheme.colorScheme.primary
    val alpha by animateFloatAsState(if (action.enabled) 1f else 0.3f, label = "actionAlpha")
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer { this.alpha = alpha }
            .pressable(if (action.enabled) action.onClick else null, pressedScale = 1.14f, glowShape = CircleShape)
            .clip(CircleShape)
            .background(accent)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)))
            .border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .semantics { if (!action.enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(action.icon, contentDescription = action.description, tint = Color(0xFF0B0B10), modifier = Modifier.size(22.dp))
    }
}
