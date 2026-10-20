package com.example.vortex_player.ui.screens

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.vortex_player.DeviceBattery
import com.example.vortex_player.DeviceSpecs
import com.example.vortex_player.R
import com.example.vortex_player.ui.components.SectionLabel
import com.example.vortex_player.ui.components.SheetHost
import com.example.vortex_player.ui.components.SheetScaffold
import com.example.vortex_player.ui.glass.rowPosition
import com.example.vortex_player.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** Tamanho dos vídeos dos aparelhos (856 × 606). */
private const val VIDEO_ASPECT = 856f / 606f

/** Nome mostrado para os AirPods na tela de detalhes. */
private const val AIRPODS_NAME = "AirPods do Pietro"

/** Este aparelho tem tela de detalhes (com vídeo)? Só existe no modo desenvolvedor. */
fun hasDetails(device: DeviceBattery): Boolean = when (device.kind) {
    DeviceBattery.Kind.Phone -> true
    else -> isAirPods(device)
}

private fun isAirPods(device: DeviceBattery) = "airpods" in device.name.lowercase()

/** Abre a tela do aparelho. Quem chama já conferiu o modo desenvolvedor. */
fun SheetHost.openDeviceDetails(device: DeviceBattery) {
    if (device.kind == DeviceBattery.Kind.Phone) {
        openDetails(device.name, R.raw.a23_float, live = { DeviceSpecs.live(it) }, sheet = DeviceSpecs.sheet())
    } else if (isAirPods(device)) {
        openDetails(
            AIRPODS_NAME,
            R.raw.airpods_float,
            live = { context -> DeviceSpecs.airPodsLive(context, device.id) },
            sheet = DeviceSpecs.airPodsSheet(),
        )
    }
}

/**
 * Tela do modo desenvolvedor: o aparelho flutuando (vídeo em loop, sobe e desce) e a ficha
 * dele embaixo. Os dados ao vivo se atualizam a cada 2 segundos enquanto a folha está aberta.
 */
private fun SheetHost.openDetails(
    title: String,
    videoRes: Int,
    live: (Context) -> List<DeviceSpecs.Spec>,
    sheet: List<DeviceSpecs.Spec>?,
) = open { dismiss ->
    val context = LocalContext.current
    var now by remember { mutableStateOf(live(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            now = live(context)
        }
    }

    SheetScaffold(title = title, onClose = dismiss) {
        LoopingVideo(
            videoRes,
            Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT)
                .clip(RoundedCornerShape(26.dp)),
        )
        SpecGroup("Agora", now)
        if (sheet != null) SpecGroup("Ficha técnica", sheet)
    }
}

@Composable
private fun SpecGroup(title: String, specs: List<DeviceSpecs.Spec>) {
    SectionLabel(title, Modifier.padding(top = 4.dp))
    Column {
        specs.forEachIndexed { index, spec ->
            GroupRow(position = rowPosition(index, specs.size), dividerInset = 16.dp, onClick = null) {
                Text(spec.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    spec.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 12.dp).widthIn(max = 200.dp),
                )
            }
        }
    }
}

/**
 * Vídeo sem som tocando em loop. Usa TextureView (e não SurfaceView) para acompanhar a
 * animação e os cantos arredondados da folha. Aparece com um fade quando o primeiro quadro
 * fica pronto, para não piscar preto.
 */
@Composable
private fun LoopingVideo(resId: Int, modifier: Modifier) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(300), label = "videoAlpha")
    val player = remember { MediaPlayer() }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            context.resources.openRawResourceFd(resId).use { fd ->
                                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                            }
                            player.setSurface(Surface(texture))
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { it.start() }
                            player.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) ready = true
                                false
                            }
                            player.prepareAsync()
                        } catch (_: Exception) {
                            // Sem vídeo: a ficha continua aparecendo normalmente
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        try { player.setSurface(null) } catch (_: Exception) { }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
