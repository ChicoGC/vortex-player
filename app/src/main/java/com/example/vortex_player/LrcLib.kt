package com.example.vortex_player

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.abs

/** Client for the free LRCLIB lyrics database (https://lrclib.net). */
object LrcLib {

    class Record(
        val synced: String?,
        val plain: String?,
        val instrumental: Boolean,
        val duration: Double,
        val trackName: String? = null,
        val artistName: String? = null,
    ) {
        val hasLyrics get() = !synced.isNullOrBlank() || !plain.isNullOrBlank()
    }

    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "VortexPlayer/1.0 (Android)"
    // Downloaded video versions often have intros/outros, so allow some drift before giving up on sync.
    private const val SYNC_TOLERANCE_SEC = 12

    /** @throws IOException when the network is unavailable. */
    fun find(song: Song, durationMs: Int): Record? {
        val (artist, title) = SearchTerms.of(song)
        val seconds = durationMs / 1000
        val candidates = mutableListOf<Record>()

        if (artist != null) {
            get("$BASE/get?artist_name=${enc(artist)}&track_name=${enc(title)}&duration=$seconds")
                ?.let { candidates += parse(JSONObject(it)) }
            if (candidates.none { it.hasLyrics }) {
                candidates += fetchList("$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}")
            }
        }
        if (candidates.none { it.hasLyrics || it.instrumental }) {
            candidates += search(listOfNotNull(artist, title).joinToString(" "))
        }
        return pick(candidates, seconds, artist)
    }

    /** Free-text search, used when the user looks for the right lyrics by hand. */
    fun search(query: String): List<Record> = fetchList("$BASE/search?q=${enc(query)}")

    private fun pick(records: List<Record>, seconds: Int, artist: String?): Record? {
        val withLyrics = records.filter { it.hasLyrics || it.instrumental }
        if (withLyrics.isEmpty()) return null
        val sameArtist = artist?.let { wanted ->
            withLyrics.filter { normalize(it.artistName.orEmpty()).contains(normalize(wanted)) }
        }.orEmpty()
        val usable = sameArtist.ifEmpty { withLyrics }
        val close = usable.filter { seconds <= 0 || abs(it.duration - seconds) <= SYNC_TOLERANCE_SEC }
        close.firstOrNull { !it.synced.isNullOrBlank() }?.let { return it }
        close.firstOrNull()?.let { return it }
        // Same song, different length: the text is still right, but the timing would be off.
        val best = usable.first()
        val text = best.plain?.takeIf { it.isNotBlank() } ?: best.synced?.let(LrcParser::stripTimestamps)
        return Record(null, text, best.instrumental, best.duration, best.trackName, best.artistName)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{Mn}"""), "").lowercase().trim()

    private fun fetchList(url: String): List<Record> {
        val body = get(url) ?: return emptyList()
        val array = JSONArray(body)
        return (0 until array.length()).map { parse(array.getJSONObject(it)) }
    }

    private fun parse(json: JSONObject) = Record(
        synced = json.stringOrNull("syncedLyrics"),
        plain = json.stringOrNull("plainLyrics"),
        instrumental = json.optBoolean("instrumental", false),
        duration = json.optDouble("duration", 0.0),
        trackName = json.stringOrNull("trackName"),
        artistName = json.stringOrNull("artistName"),
    )

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            return when (connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/** Turns names like "Artist - Song (Official Video) 🔥" into clean search terms. */
object SearchTerms {

    private val BRACKETS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val SYMBOLS = Regex("""[\p{So}\p{Sk}️‍]""")
    private val FEATURE_TAIL = Regex("""\s+(ft\.?|feat\.?|prod\.?)\s.*$""", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("""\s*(,|&|\s_\s|\sx\s|\sft\.?\s|\sfeat\.?\s)\s*""", RegexOption.IGNORE_CASE)

    fun of(song: Song): Pair<String?, String> {
        var artist: String? = song.artist.takeUnless { it == FolderScanner.UNKNOWN_ARTIST }
        var title = song.title
        if (artist == null && " - " in title) {
            artist = title.substringBefore(" - ")
            title = title.substringAfter(" - ")
        }
        val cleanTitle = clean(title).replace(FEATURE_TAIL, "").trim()
        val cleanArtist = artist?.let { clean(it).split(ARTIST_SPLIT).first().trim() }?.takeIf { it.isNotBlank() }
        return cleanArtist to cleanTitle.ifBlank { title }
    }

    private fun clean(text: String): String =
        text.replace(BRACKETS, "").replace(SYMBOLS, "").replace(Regex("""\s+"""), " ").trim().trimEnd('-').trim()
}
