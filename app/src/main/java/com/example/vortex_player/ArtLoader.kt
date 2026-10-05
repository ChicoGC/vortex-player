package com.example.vortex_player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

class ArtLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(uri: Uri, maxSize: Int, target: ImageView, onResult: ((Bitmap?) -> Unit)? = null) =
        load("$uri@$maxSize", target, onResult) { decodeEmbedded(uri, maxSize) }

    fun loadImage(uri: Uri, maxSize: Int, target: ImageView, onResult: ((Bitmap?) -> Unit)? = null) =
        load("image:$uri@$maxSize", target, onResult) {
            context.contentResolver.openInputStream(uri)?.use { decodeSampled(it.readBytes(), maxSize) }
        }

    fun shutdown() = executor.shutdownNow()

    private fun load(key: String, target: ImageView, onResult: ((Bitmap?) -> Unit)?, decoder: () -> Bitmap?) {
        target.setTag(R.id.art_key, key)
        val cached = cache.get(key)
        if (cached != null) {
            show(target, cached)
            onResult?.invoke(cached)
            return
        }
        showPlaceholder(target)
        if (key in missing) {
            onResult?.invoke(null)
            return
        }
        executor.execute {
            val bitmap = try { decoder() } catch (_: Exception) { null }
            main.post {
                if (bitmap != null) cache.put(key, bitmap) else missing.add(key)
                if (target.getTag(R.id.art_key) == key) {
                    if (bitmap != null) show(target, bitmap)
                    onResult?.invoke(bitmap)
                }
            }
        }
    }

    private fun show(target: ImageView, bitmap: Bitmap) {
        target.scaleType = ImageView.ScaleType.CENTER_CROP
        target.setImageBitmap(bitmap)
    }

    private fun showPlaceholder(target: ImageView) {
        target.scaleType = ImageView.ScaleType.CENTER_INSIDE
        target.setImageResource(R.drawable.ic_music)
    }

    private fun decodeEmbedded(uri: Uri, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { decodeSampled(it, maxSize) }
        } finally {
            retriever.release()
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
