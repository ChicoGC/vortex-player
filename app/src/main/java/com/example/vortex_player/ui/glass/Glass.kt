package com.example.vortex_player.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.example.vortex_player.ui.theme.BackgroundStyle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.vortex_player.ui.theme.Background
import com.example.vortex_player.ui.theme.LocalLook
import com.example.vortex_player.ui.theme.VortexLook
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs

/**
 * O que fica atrás dos elementos flutuantes (fundo + conteúdo da tela). Os elementos de
 * vidro líquido desenham essa imagem desfocada e refratada.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { error("LocalGlassBackdrop não definido") }

/**
 * Só o fundo do app (sem o conteúdo das telas). Usado por vidro que fica *dentro* do
 * conteúdo, como os widgets: ele não pode refratar a camada da qual faz parte.
 */
val LocalBackgroundBackdrop = staticCompositionLocalOf<Backdrop> { error("LocalBackgroundBackdrop não definido") }

// ---------------------------------------------------------------------------------------
// Vidro líquido (elementos flutuantes: barra de abas, botão +, folhas)
// ---------------------------------------------------------------------------------------

/**
 * Vidro líquido no estilo da Apple: desfoca e **refrata** o que está atrás (efeito de lente
 * nas bordas), com brilho na borda e sombra suave. A intensidade vem do ajuste "Vidro".
 *
 * @param tint cor misturada ao vidro (ex.: o botão principal na cor do tema).
 * @param minOpacity opacidade mínima do vidro, para blocos com muito texto (folhas).
 * @param lens quanto da refração aplicar (1 = padrão).
 * @param backdrop o que o vidro refrata (padrão: fundo + conteúdo da tela).
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    tint: Color? = null,
    minOpacity: Float = 0f,
    lens: Float = 1f,
    backdrop: Backdrop = LocalGlassBackdrop.current,
): Modifier {
    val look = LocalLook.current
    val g = look.glass
    val base = look.base
    val surfaceAlpha = maxOf(lerp(0.08f, 0.66f, g), minOpacity)
    val blurRadius = lerp(1.5.dp, 14.dp, g)
    val refractionAmount = lerp(30.dp, 16.dp, g)
    val highlightAlpha = lerp(0.9f, 0.55f, g)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (lens > 0f) {
                lens(
                    refractionHeight = (18.dp * lens).toPx().coerceAtMost(size.minDimension / 2f),
                    refractionAmount = (refractionAmount * lens).toPx(),
                    depthEffect = true,
                )
            }
        },
        highlight = { Highlight(width = 1.dp, alpha = highlightAlpha, style = HighlightStyle.Default) },
        shadow = { Shadow(radius = 22.dp, color = Color.Black.copy(alpha = 0.22f)) },
        onDrawSurface = {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, base.copy(alpha = surfaceAlpha))
            if (tint != null) drawOutline(outline, tint.copy(alpha = 0.34f))
        },
    )
}

// ---------------------------------------------------------------------------------------
// Vidro de controles (chips, botões redondos, seletores) e superfície do conteúdo
// ---------------------------------------------------------------------------------------

/**
 * Brilho especular da borda: forte no canto de cima à esquerda e de novo, mais fraco, no
 * de baixo à direita, como luz atravessando uma peça de vidro.
 */
private fun specularBrush(strength: Float) = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.42f * strength),
    0.22f to Color.White.copy(alpha = 0.08f * strength),
    0.6f to Color.White.copy(alpha = 0.02f * strength),
    1f to Color.White.copy(alpha = 0.20f * strength),
)

private val SheenBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.07f),
    0.5f to Color.Transparent,
)

/**
 * Vidro para controles dentro do conteúdo (chips, botões, seletores). Não desfoca: atrás
 * deles só existe o fundo, um degradê suave, então o desfoque não mudaria o visual e
 * custaria desempenho. A opacidade acompanha o ajuste "Vidro".
 */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val look = LocalLook.current
    val g = look.glass
    val fill = Color.White.copy(alpha = lerp(0.06f, 0.10f, g))
    val frost = look.base.copy(alpha = lerp(0.1f, 0.55f, g))
    return this
        .clip(shape)
        .background(frost)
        .background(fill)
        .background(SheenBrush)
        .border(0.8.dp, specularBrush(lerp(1.1f, 0.8f, g)), shape)
}

/**
 * Superfície das listas e cartões. Segue a regra da Apple: o vidro fica na camada de
 * navegação (o que flutua); o conteúdo usa superfícies discretas, agrupadas como no iOS.
 */
@Composable
fun Modifier.contentSurface(shape: Shape): Modifier {
    val look = LocalLook.current
    val g = look.glass
    // As duas camadas (fundo do tema + véu branco) misturadas numa cor só: mesmo visual,
    // metade do trabalho de pintura para a GPU em listas longas.
    val color = remember(look.base, g) {
        Color.White.copy(alpha = lerp(0.055f, 0.075f, g))
            .compositeOver(look.base.copy(alpha = lerp(0.18f, 0.6f, g)))
    }
    return this.background(color, shape)
}

/** Posição de uma linha dentro de um grupo (define quais cantos são arredondados). */
enum class RowPosition { Single, First, Middle, Last }

fun rowPosition(index: Int, count: Int): RowPosition = when {
    count == 1 -> RowPosition.Single
    index == 0 -> RowPosition.First
    index == count - 1 -> RowPosition.Last
    else -> RowPosition.Middle
}

val GroupRadius = 26.dp

fun groupShape(position: RowPosition, radius: Dp = GroupRadius): Shape = when (position) {
    RowPosition.Single -> RoundedCornerShape(radius)
    RowPosition.First -> RoundedCornerShape(topStart = radius, topEnd = radius)
    RowPosition.Last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    RowPosition.Middle -> RectangleShape
}

/** Linha fininha separando itens do mesmo grupo (só a partir do segundo), recuada à esquerda. */
fun Modifier.groupDivider(position: RowPosition, startInset: Dp): Modifier =
    if (position == RowPosition.Single || position == RowPosition.First) this
    else drawWithContent {
        drawContent()
        val x = startInset.toPx()
        drawLine(
            Color.White.copy(alpha = 0.09f),
            start = Offset(x, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 0.5.dp.toPx(),
        )
    }

// ---------------------------------------------------------------------------------------
// Toque
// ---------------------------------------------------------------------------------------

/**
 * Resposta ao toque no estilo do iOS: o elemento muda de escala com uma mola e acende uma
 * luz no ponto onde o dedo encostou.
 *
 * @param pressedScale escala enquanto pressionado. Botões de vidro crescem (> 1), cartões
 * afundam levemente (< 1).
 * @param glowShape forma usada para recortar o brilho; null desliga o brilho.
 */
@Composable
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    pressedScale: Float = 0.98f,
    glowShape: Shape? = null,
): Modifier {
    if (onClick == null) return this
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var pressPosition by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> { pressPosition = it.pressPosition; pressed = true }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "pressScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(90) else tween(420),
        label = "pressGlow",
    )
    val path = remember { Path() }
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (glowShape == null) Modifier else Modifier.drawWithContent {
                drawContent()
                if (glow > 0f && pressPosition != Offset.Unspecified) {
                    path.rewind()
                    path.addOutline(glowShape.createOutline(size, layoutDirection, this))
                    clipPath(path) {
                        val radius = size.maxDimension * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.22f * glow), Color.Transparent),
                                center = pressPosition,
                                radius = radius,
                            ),
                            radius = radius,
                            center = pressPosition,
                            blendMode = BlendMode.Plus,
                        )
                    }
                }
            },
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ---------------------------------------------------------------------------------------
// Indicador "líquido" (pílula da barra de abas e do seletor)
// ---------------------------------------------------------------------------------------

/**
 * Posição animada com mola. Começa já no alvo (sem animar na primeira exibição) e
 * persegue o alvo sempre que ele muda, a não ser que [hold] esteja ativo (durante arrasto).
 */
@Composable
fun rememberLiquidOffset(target: Float, hold: Boolean = false): Animatable<Float, AnimationVector1D> {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, hold) {
        if (!hold) anim.animateTo(target, LiquidSpring)
    }
    return anim
}

val LiquidSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 380f)

/**
 * Estica o elemento na direção do movimento e o achata um pouco, como uma gota sendo
 * arrastada. Usa a velocidade atual da animação.
 */
fun Modifier.liquidStretch(anim: Animatable<Float, AnimationVector1D>, lift: Float = 1f): Modifier =
    graphicsLayer {
        // Só estica enquanto anima: a velocidade não avisa quando volta a zero, e sem esse
        // teste a gota podia ficar "congelada" esticada depois que a animação parava.
        val stretch = if (anim.isRunning) (abs(anim.velocity) / 5000f).coerceAtMost(0.45f) else 0f
        scaleX = (1f + stretch) * lift
        scaleY = (1f - stretch * 0.35f) * lift
    }

// ---------------------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GroupRadius),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    pressedScale: Float = 0.985f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .pressable(onClick, pressedScale = pressedScale, glowShape = shape)
            .contentSurface(shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick, pressedScale = 1.12f, glowShape = CircleShape)
            .glass(CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Fundo da tela. No modo degradê desenha manchas de luz com as cores do tema; no modo
 * cor única, pinta tudo com a cor sólida do tema; no modo capa, mostra a capa da música
 * desfocada e escurecida por cima das manchas. As trocas são animadas.
 */
@Composable
fun GlassBackdrop(look: VortexLook, cover: ImageBitmap?, modifier: Modifier = Modifier) {
    val spec = tween<Color>(500)
    val glow0 by animateColorAsState(look.theme.glows[0], spec, label = "glow0")
    val glow1 by animateColorAsState(look.theme.glows[1], spec, label = "glow1")
    val glow2 by animateColorAsState(look.theme.glows[2], spec, label = "glow2")
    val solid by animateColorAsState(look.theme.solid, spec, label = "solid")
    val gradient by animateFloatAsState(
        targetValue = if (look.background == BackgroundStyle.Solid) 0f else 1f,
        animationSpec = tween(500),
        label = "gradient",
    )
    val showCover = look.background == BackgroundStyle.Cover

    // Camada própria (textura): o fundo só é redesenhado quando muda, não a cada quadro
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Background)
            fun glow(color: Color, center: Offset, radius: Float, alpha: Float) = drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            val w = size.width
            val h = size.height
            if (gradient > 0f) {
                glow(glow0, Offset(w * 0.1f, h * 0.1f), w * 0.95f, 0.55f * gradient)
                glow(glow1, Offset(w * 1.0f, h * 0.48f), w * 0.85f, 0.42f * gradient)
                glow(glow2, Offset(w * 0.15f, h * 0.92f), w * 0.8f, 0.32f * gradient)
            }
            if (gradient < 1f) drawRect(solid, alpha = 1f - gradient)
        }
        Crossfade(
            targetState = if (showCover) cover else null,
            animationSpec = tween(700),
            modifier = Modifier.matchParentSize(),
            label = "cover",
        ) { bitmap ->
            if (bitmap != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        alpha = 0.62f,
                        modifier = Modifier.matchParentSize(),
                    )
                    // Escurece mais embaixo, onde ficam os controles, para o texto continuar legível
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Background.copy(alpha = 0.25f),
                                1f to Background.copy(alpha = 0.7f),
                            ),
                        ),
                    )
                }
            }
        }
    }
}
