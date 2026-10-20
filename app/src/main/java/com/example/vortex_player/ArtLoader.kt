package com.example.vortex_player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/** Decodes covers off the main thread (the song's embedded art or a photo), with a memory cache. */
class ArtLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** The picture embedded in the audio file, if any. */
    fun embedded(uri: Uri, maxSize: Int, onResult: (Bitmap?) -> Unit) =
        load("$uri@$maxSize", onResult) { decodeEmbedded(uri, maxSize) }

    /** A photo file (cover folder). */
    fun image(uri: Uri, maxSize: Int, onResult: (Bitmap?) -> Unit) =
        load("image:$uri@$maxSize", onResult) {
            context.contentResolver.openInputStream(uri)?.use { decodeSampled(it.readBytes(), maxSize) }
        }

    fun cachedEmbedded(uri: Uri, maxSize: Int): Bitmap? = cache.get("$uri@$maxSize")

    fun shutdown() = executor.shutdownNow()

    private fun load(key: String, onResult: (Bitmap?) -> Unit, decoder: () -> Bitmap?) {
        cache.get(key)?.let {
            onResult(it)
            return
        }
        if (key in missing) {
            onResult(null)
            return
        }
        executor.execute {
            val bitmap = try { decoder() } catch (_: Exception) { null }
            main.post {
                if (bitmap != null) cache.put(key, bitmap) else missing.add(key)
                onResult(bitmap)
            }
        }
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
