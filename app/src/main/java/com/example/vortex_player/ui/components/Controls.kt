package com.example.vortex_player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.vortex_player.ui.VortexIcons
import com.example.vortex_player.ui.glass.glass
import com.example.vortex_player.ui.glass.contentSurface
import com.example.vortex_player.ui.glass.GroupRadius
import androidx.compose.ui.text.font.FontWeight
import com.example.vortex_player.ui.glass.pressable
import com.example.vortex_player.ui.theme.Danger
import com.example.vortex_player.ui.theme.TextPrimary
import com.example.vortex_player.ui.theme.TextSecondary
import com.example.vortex_player.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** Estica o elemento além do padding horizontal do pai (para listas que rolam de ponta a ponta). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-horizontal.roundToPx(), 0)
    }
}


/** Chip em cápsula de vidro. Selecionado vira uma cápsula clara com texto escuro, como no iOS. */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val fill by animateColorAsState(if (selected) Color.White.copy(alpha = 0.94f) else Color.Transparent, label = "chipFill")
    val text by animateColorAsState(if (selected) Color(0xFF111217) else TextPrimary.copy(alpha = 0.86f), label = "chipText")
    Row(
        modifier = modifier
            .pressable(onClick, pressedScale = 1.06f, glowShape = CircleShape)
            .semantics { this.selected = selected; role = Role.Button }
            .glass(CircleShape)
            .background(fill)
            .height(36.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) CompositionLocalProvider(LocalContentColor provides text) { leading() }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = text, maxLines = 1)
    }
}

/** Campo de texto sem caixa do Material, para combinar com o vidro. */
@Composable
fun VortexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val accent = MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = TextPrimary),
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = TextTertiary)
                inner()
            }
        },
    )
}

/**
 * Linha vermelha de excluir, no fim do formulário (como no iOS). Pede um segundo toque para
 * confirmar em vez de abrir uma caixa de diálogo, e volta ao normal sozinha em 3 segundos.
 */
@Composable
fun DestructiveRow(label: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    val shape = RoundedCornerShape(GroupRadius)
    val fill by animateColorAsState(if (armed) Danger.copy(alpha = 0.18f) else Color.Transparent, label = "deleteFill")
    Row(
        modifier
            .fillMaxWidth()
            .pressable({
                if (armed) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    armed = true
                }
            }, pressedScale = 0.985f, glowShape = shape)
            .contentSurface(shape)
            .background(fill)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(VortexIcons.Delete, contentDescription = null, tint = Danger, modifier = Modifier.size(19.dp))
        Text(
            if (armed) "Toque de novo para excluir" else label,
            style = MaterialTheme.typography.labelLarge,
            color = Danger,
        )
    }
}

/** Círculo de concluir tarefa. Preenche com a cor da matéria quando marcado. */
@Composable
fun CheckCircle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "checkFill")
    val ring by animateColorAsState(if (checked) color else TextTertiary, label = "checkRing")
    val scale by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "checkScale",
    )
    Box(
        modifier
            .size(44.dp) // área de toque confortável, o círculo visível é menor
            .pressable(onToggle, pressedScale = 0.85f)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (checked) "Concluída" else "Pendente"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                VortexIcons.Check,
                contentDescription = null,
                tint = Color(0xFF0B0B10),
                modifier = Modifier.size(16.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

/** Caixa para agrupar campos de um formulário. */
@Composable
fun FieldBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .contentSurface(RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) { content() }
}
