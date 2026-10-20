package com.example.vortex_player.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.vortex_player.ui.theme.TextTertiary

/** A cover, or a music note on a discreet square while there is none. */
@Composable
fun CoverArt(bitmap: ImageBitmap?, modifier: Modifier, shape: Shape) {
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(VortexIcons.Music, contentDescription = null, tint = TextTertiary, modifier = Modifier.fillMaxWidth(0.45f))
        }
    }
}

/** The picture embedded in a song file, loaded in the background (for list rows). */
@Composable
fun rememberSongArt(uri: Uri, size: Int = 160): ImageBitmap? {
    val loader = LocalPlayer.current.artLoader
    var bitmap by remember(uri) { mutableStateOf(loader.cachedEmbedded(uri, size)?.asImageBitmap()) }
    DisposableEffect(uri) {
        var active = true
        if (bitmap == null) loader.embedded(uri, size) { if (active) bitmap = it?.asImageBitmap() }
        onDispose { active = false }
    }
    return bitmap
}
