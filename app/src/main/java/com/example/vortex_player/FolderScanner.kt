package com.example.vortex_player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract

object FolderScanner {

    const val UNKNOWN_ARTIST = "Artista desconhecido"
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")

    private class AudioFile(val uri: Uri, val name: String, val lrcUri: Uri?)

    fun scan(context: Context, treeUri: Uri): List<Song> {
        val files = mutableListOf<AudioFile>()
        walk(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), files)
        return files
            .map { readSong(context, it) }
            .sortedBy { it.title.lowercase() }
    }

    /** All images (jpg/png/webp) in the tree, including subfolders. */
    fun scanImages(context: Context, treeUri: Uri): List<Uri> {
        val images = mutableListOf<Uri>()
        walkImages(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), images)
        return images
    }

    private fun walkImages(context: Context, treeUri: Uri, docId: String, out: MutableList<Uri>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: ""
                val mime = c.getString(2) ?: ""
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> walkImages(context, treeUri, id, out)
                    mime in IMAGE_MIMES || name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS ->
                        out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
        }
    }

    fun folderName(treeUri: Uri): String {
        val path = DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':', "")
        return if (path.isBlank()) "Armazenamento interno" else path.substringAfterLast('/')
    }

    private fun walk(context: Context, treeUri: Uri, docId: String, out: MutableList<AudioFile>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val audio = mutableListOf<Pair<Uri, String>>()
        val lyrics = HashMap<String, Uri>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: ""
                val mime = c.getString(2) ?: ""
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> walk(context, treeUri, id, out)
                    name.endsWith(".lrc", ignoreCase = true) -> lyrics[baseName(name)] = uri
                    isAudio(name, mime) -> audio.add(uri to name)
                }
            }
        }
        audio.forEach { (uri, name) -> out.add(AudioFile(uri, name, lyrics[baseName(name)])) }
    }

    private fun baseName(fileName: String) = fileName.substringBeforeLast('.').lowercase()

    private fun isAudio(name: String, mime: String): Boolean =
        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    private fun readSong(context: Context, file: AudioFile): Song {
        val fallbackTitle = file.name.substringBeforeLast('.')
        var title = fallbackTitle
        var artist: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
        } finally {
            retriever.release()
        }
        if (artist == null) {
            val (nameArtist, nameTitle) = splitArtistAndTitle(title)
            artist = nameArtist
            title = nameTitle
        }
        return Song(file.uri, title, artist ?: UNKNOWN_ARTIST, file.lrcUri)
    }

    private val VIDEO_NOISE = Regex(
        """\s*[(\[]\s*(?:[^)\]]*\b(?:official|oficial|video|vídeo|videoclipe|clipe|clip|lyrics?|letra|audio|áudio|visualizer|hq|hd|4k|mv)\b[^)\]]*|\d+)\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val PRODUCER_TAIL = Regex("""\s+prod\.?\s+by\s+.*$""", RegexOption.IGNORE_CASE)

    /** "Artist - Song (Official Video)" -> ("Artist", "Song"); names without " - " keep only the cleanup. */
    private fun splitArtistAndTitle(name: String): Pair<String?, String> {
        val cleaned = name.replace(VIDEO_NOISE, "").replace(PRODUCER_TAIL, "").replace(Regex("""\s+"""), " ").trim()
        val separator = cleaned.indexOf(" - ")
        if (separator <= 0) return null to cleaned.ifBlank { name }
        val artist = cleaned.substring(0, separator).replace(" _ ", " & ").trim()
        val title = cleaned.substring(separator + 3).trim()
        return if (artist.isBlank() || title.isBlank()) null to cleaned else artist to title
    }
}
