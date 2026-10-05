package com.example.vortex_player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Finds lyrics: a lyrics version the user picked by hand, then a sibling .lrc file,
 * then the on-device cache, then LRCLIB. Also stores a per-song timing offset.
 */
class LyricsRepository(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cacheDir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)

    private inner class CacheFiles(song: Song) {
        private val key = keyFor(song)
        val synced = File(cacheDir, "$key.lrc")
        val plain = File(cacheDir, "$key.txt")
        val instrumental = File(cacheDir, "$key.inst")
        val missing = File(cacheDir, "$key.none")
        val chosenByUser = File(cacheDir, "$key.chosen")

        fun clear() = listOf(synced, plain, instrumental, missing, chosenByUser).forEach { it.delete() }
    }

    fun load(song: Song, durationMs: Int, forceRefresh: Boolean, callback: (Lyrics) -> Unit) {
        executor.execute {
            val result = try {
                resolve(song, durationMs, forceRefresh)
            } catch (_: Exception) {
                Lyrics.NotFound
            }
            main.post { callback(result) }
        }
    }

    /** Calls back with null when there is no connection. */
    fun search(query: String, callback: (List<LrcLib.Record>?) -> Unit) {
        executor.execute {
            val result = try {
                LrcLib.search(query).filter { it.hasLyrics || it.instrumental }
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
                emptyList()
            }
            main.post { callback(result) }
        }
    }

    fun choose(song: Song, record: LrcLib.Record, callback: (Lyrics) -> Unit) {
        executor.execute {
            val files = CacheFiles(song)
            files.clear()
            files.chosenByUser.writeText("")
            val result = store(files, record)
            setOffset(song, 0L)
            main.post { callback(result) }
        }
    }

    fun defaultQuery(song: Song): String =
        SearchTerms.of(song).let { (artist, title) -> listOfNotNull(artist, title).joinToString(" ") }

    fun offset(song: Song): Long = prefs.getLong("offset_${keyFor(song)}", 0L)

    fun setOffset(song: Song, offsetMs: Long) = prefs.edit { putLong("offset_${keyFor(song)}", offsetMs) }

    fun shutdown() = executor.shutdownNow()

    private fun resolve(song: Song, durationMs: Int, forceRefresh: Boolean): Lyrics {
        val files = CacheFiles(song)
        if (forceRefresh) files.clear()

        if (!files.chosenByUser.exists()) {
            song.lrcUri?.let { uri -> readText(uri)?.let { return LrcParser.parse(it) } }
        }
        when {
            files.synced.exists() -> return LrcParser.parse(files.synced.readText())
            files.plain.exists() -> return LrcParser.parse(files.plain.readText())
            files.instrumental.exists() -> return Lyrics.Instrumental
            files.missing.exists() -> return Lyrics.NotFound
        }

        val record = try {
            LrcLib.find(song, durationMs)
        } catch (_: IOException) {
            return Lyrics.Offline
        }
        return if (record == null) files.missing.mark(Lyrics.NotFound) else store(files, record)
    }

    private fun store(files: CacheFiles, record: LrcLib.Record): Lyrics {
        val syncedText = record.synced
        val plainText = record.plain
        return when {
            !syncedText.isNullOrBlank() -> files.synced.save(syncedText)
            !plainText.isNullOrBlank() -> files.plain.save(plainText)
            record.instrumental -> files.instrumental.mark(Lyrics.Instrumental)
            else -> files.missing.mark(Lyrics.NotFound)
        }
    }

    private fun File.save(text: String): Lyrics {
        writeText(text)
        return LrcParser.parse(text)
    }

    private fun File.mark(result: Lyrics): Lyrics {
        writeText("")
        return result
    }

    private fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun keyFor(song: Song): String =
        MessageDigest.getInstance("SHA-1").digest(song.uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
}
