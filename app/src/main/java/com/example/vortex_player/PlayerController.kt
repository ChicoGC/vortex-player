package com.example.vortex_player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.concurrent.Executors

/**
 * Everything the player does (music folder, playback, play order, covers, lyrics, audio output,
 * headset buttons and the notification), exposed as Compose state for the screens to show.
 * Lives as long as the app process, so the music keeps going if the screen is recreated.
 */
class PlayerController private constructor(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences("vortex", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val coverPicker = CoverPicker()
    private val lyricsRepository = LyricsRepository(context)
    val appearance = Appearance(prefs)
    val devMode = DevMode(prefs)
    val artLoader = ArtLoader(context)

    private val headset = HeadsetControls(context, object : HeadsetControls.Listener {
        override fun onPlay() = play()
        override fun onPause() = pause()
        override fun onNext() = next()
        override fun onPrevious() = previous()
        override fun onSeek(positionMs: Long) = seekTo(positionMs.toInt())
    })

    private var player: MediaPlayer? = null
    private var preferredDevice: AudioDeviceInfo? = null
    private var notificationArt: Bitmap? = null
    private var lyricsForUri: Uri? = null

    /** Play order as song indices; [orderPos] is where the current song sits in it. */
    private var order: List<Int> = emptyList()
    private var orderPos = -1

    // ---------- State shown by the screens ----------

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var folderName by mutableStateOf<String?>(null)
        private set
    /** Message instead of the list (no folder, loading, empty folder); null when there are songs. */
    var libraryMessage by mutableStateOf<String?>("Escolha a pasta com as suas músicas")
        private set
    var index by mutableIntStateOf(-1)
        private set
    val current: Song? get() = songs.getOrNull(index)
    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var shuffle by mutableStateOf(prefs.getBoolean(KEY_SHUFFLE, false))
        private set
    /** Cover of the current song (or the photo picked from the cover folder). */
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    /** The same cover, tiny and blurred, for the "Capa" background. */
    var backdrop by mutableStateOf<ImageBitmap?>(null)
        private set
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set
    var lyricsOffset by mutableLongStateOf(0L)
        private set
    var outputName by mutableStateOf("Este celular")
        private set
    var volume by mutableIntStateOf(0)
        private set
    val maxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var coverFolderName by mutableStateOf<String?>(null)
        private set
    var coverFolderEmpty by mutableStateOf(false)
        private set

    /** Called once a song really starts (the screen uses it to ask for notification permission). */
    var onSongStarted: (() -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            positionMs = p.currentPosition
            if (p.isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateOutputName()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == preferredDevice?.id }) preferredDevice = null
            updateOutputName()
        }
    }

    init {
        PlaybackService.commands = { command ->
            when (command) {
                PlaybackService.ACTION_PLAY_PAUSE -> togglePlay()
                PlaybackService.ACTION_NEXT -> next()
                PlaybackService.ACTION_PREVIOUS -> previous()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        refreshVolume()
        restoreCoverFolder()
        restoreFolder()
    }

    // ---------- Music folder ----------

    private fun restoreFolder() {
        val saved = prefs.getString(KEY_FOLDER, null)?.toUri()
        if (saved != null && isAllowed(saved)) loadFolder(saved)
    }

    fun onFolderChosen(uri: Uri) {
        val old = prefs.getString(KEY_FOLDER, null)?.toUri()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit { putString(KEY_FOLDER, uri.toString()) }
        if (old != null && old != uri) releaseIfUnused(old)
        loadFolder(uri)
    }

    private fun loadFolder(uri: Uri) {
        val name = FolderScanner.folderName(uri)
        folderName = name
        libraryMessage = "Carregando músicas…"
        scanExecutor.execute {
            val found = FolderScanner.scan(context, uri)
            handler.post {
                stopPlayback()
                songs = found
                folderName = "$name · ${found.size} músicas"
                libraryMessage = if (found.isEmpty()) "Nenhuma música nessa pasta" else null
            }
        }
    }

    // ---------- Cover photos ----------

    private fun restoreCoverFolder() {
        val saved = prefs.getString(KEY_COVER_FOLDER, null)?.toUri() ?: return
        if (isAllowed(saved)) loadCoverFolder(saved) else prefs.edit { remove(KEY_COVER_FOLDER) }
    }

    fun onCoverFolderChosen(uri: Uri) {
        val old = prefs.getString(KEY_COVER_FOLDER, null)?.toUri()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit { putString(KEY_COVER_FOLDER, uri.toString()) }
        if (old != null && old != uri) releaseIfUnused(old)
        loadCoverFolder(uri)
    }

    fun clearCoverFolder() {
        val old = prefs.getString(KEY_COVER_FOLDER, null)?.toUri() ?: return
        prefs.edit { remove(KEY_COVER_FOLDER) }
        releaseIfUnused(old)
        coverPicker.setCovers(emptyList())
        coverFolderName = null
        coverFolderEmpty = false
    }

    private fun loadCoverFolder(uri: Uri) {
        coverFolderName = FolderScanner.folderName(uri)
        scanExecutor.execute {
            val images = runCatching { FolderScanner.scanImages(context, uri) }.getOrDefault(emptyList())
            handler.post {
                coverPicker.setCovers(images)
                coverFolderEmpty = images.isEmpty()
                if (images.isEmpty()) toast("Nenhuma foto encontrada nessa pasta")
            }
        }
    }

    private fun isAllowed(uri: Uri) =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /** The music and cover folders may be the same, so only drop the permission when neither uses it. */
    private fun releaseIfUnused(uri: Uri) {
        val inUse = listOf(KEY_FOLDER, KEY_COVER_FOLDER).any { prefs.getString(it, null)?.toUri() == uri }
        if (!inUse) {
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    // ---------- Playback ----------

    /** The user picked this song: the play order restarts from it. */
    fun playAt(position: Int) {
        rebuildOrder(position)
        moveTo(position)
    }

    private fun moveTo(position: Int) {
        index = position
        startSong()
    }

    /**
     * In order: the list as it is. Shuffled: [start] first, then every other song once in a random
     * order, so nothing repeats until the whole list has played.
     */
    private fun rebuildOrder(start: Int) {
        val all = songs.indices.toList()
        order = when {
            !shuffle -> all
            start < 0 -> all.shuffled()
            else -> listOf(start) + (all - start).shuffled()
        }
        orderPos = order.indexOf(start)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        prefs.edit { putBoolean(KEY_SHUFFLE, shuffle) }
        rebuildOrder(index)
        toast(if (shuffle) "Ordem aleatória ativada" else "Ordem aleatória desativada")
    }

    private fun startSong() {
        val song = current ?: return
        player?.release()
        player = null

        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(context, song.uri)
            p.prepare()
        } catch (_: Exception) {
            p.release()
            toast("Não foi possível tocar \"${song.title}\"")
            return
        }
        preferredDevice?.let { p.setPreferredDevice(it) }
        p.setOnCompletionListener { next() }
        p.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { updateOutputName() }, handler)
        p.start()
        player = p

        durationMs = p.duration
        positionMs = 0
        showSong(song, p.duration)
        updatePlayState()
        onSongStarted?.invoke()
    }

    private fun stopPlayback() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        index = -1
        order = emptyList()
        orderPos = -1
        isPlaying = false
        art = null
        backdrop = null
        resetLyrics()
        headset.stopped()
        PlaybackService.hide(context)
    }

    fun togglePlay() {
        if (player?.isPlaying == true) pause() else play()
    }

    fun play() {
        val p = player
        if (p == null) {
            if (songs.isEmpty()) return
            // Nothing played yet: in shuffle mode, start with a random song
            playAt(if (index >= 0) index else if (shuffle) songs.indices.random() else 0)
            return
        }
        if (p.isPlaying) return
        p.start()
        updatePlayState()
    }

    fun pause() {
        val p = player ?: return
        if (p.isPlaying) p.pause()
        updatePlayState()
    }

    fun seekTo(positionMs: Int) {
        val p = player ?: return
        p.seekTo(positionMs)
        this.positionMs = positionMs
        headset.setState(p.isPlaying, positionMs.toLong())
    }

    fun next() {
        if (songs.isEmpty()) return
        if (order.size != songs.size) rebuildOrder(index)
        orderPos++
        if (orderPos >= order.size) {
            // End of the list: start over (with a fresh shuffle that doesn't open with the song that just played)
            if (shuffle) {
                val fresh = songs.indices.shuffled().toMutableList()
                if (fresh.size > 1 && fresh.first() == index) fresh.add(fresh.removeAt(0))
                order = fresh
            }
            orderPos = 0
        }
        moveTo(order[orderPos])
    }

    fun previous() {
        if (songs.isEmpty()) return
        val p = player
        if (p != null && p.currentPosition > 3000) {
            seekTo(0)
            return
        }
        if (order.size != songs.size) rebuildOrder(index)
        orderPos = if (orderPos > 0) orderPos - 1 else order.size - 1
        moveTo(order[orderPos])
    }

    private fun showSong(song: Song, duration: Int) {
        notificationArt = null
        headset.setSong(song, duration, null)
        resetLyrics()

        val onCover: (Bitmap?) -> Unit = { bitmap ->
            if (current == song) {
                art = bitmap?.asImageBitmap()
                backdrop = bitmap?.let { CoverBlur.make(it).asImageBitmap() }
                notificationArt = bitmap?.let { ThumbnailUtils.extractThumbnail(it, NOTIFICATION_ART_SIZE, NOTIFICATION_ART_SIZE) }
                headset.setSong(song, duration, notificationArt)
                updateNotification()
            }
        }
        val cover = coverPicker.next()
        if (cover != null) artLoader.image(cover, ART_SIZE, onCover) else artLoader.embedded(song.uri, ART_SIZE, onCover)
    }

    private fun updatePlayState() {
        val p = player
        isPlaying = p?.isPlaying == true
        p?.let {
            positionMs = it.currentPosition
            headset.setState(isPlaying, it.currentPosition.toLong())
        }
        handler.removeCallbacks(ticker)
        if (isPlaying) handler.post(ticker)
        updateNotification()
    }

    fun updateNotification() {
        val p = player ?: return
        val song = current ?: return
        PlaybackService.show(context, PlaybackService.build(context, headset.token, song, notificationArt, p.isPlaying))
    }

    // ---------- Lyrics ----------

    private fun resetLyrics() {
        lyrics = null
        lyricsForUri = null
        lyricsOffset = 0L
        lyricsLoading = false
    }

    /** Loads the lyrics of the current song (once per song unless [forceRefresh]). */
    fun ensureLyrics(forceRefresh: Boolean = false) {
        val song = current ?: return
        if (!forceRefresh && lyricsForUri == song.uri) return
        lyricsForUri = song.uri
        lyricsOffset = lyricsRepository.offset(song)
        lyricsLoading = true
        lyricsRepository.load(song, durationMs, forceRefresh) { result ->
            if (lyricsForUri == song.uri) {
                lyrics = result
                lyricsLoading = false
            }
        }
    }

    fun changeLyricsOffset(offsetMs: Long) {
        val song = current ?: return
        lyricsOffset = offsetMs
        lyricsRepository.setOffset(song, offsetMs)
    }

    /** Long press on a line while it is sung: the lyrics shift so that line starts now. */
    fun syncLineToNow(line: LyricLine) {
        val position = player?.currentPosition ?: return
        changeLyricsOffset(position - line.timeMs)
        toast("Letra sincronizada (${formatOffset(lyricsOffset)})")
    }

    fun lyricsQuery(): String = current?.let { lyricsRepository.defaultQuery(it) }.orEmpty()

    fun searchLyrics(query: String, callback: (List<LrcLib.Record>?) -> Unit) =
        lyricsRepository.search(query, callback)

    fun chooseLyrics(record: LrcLib.Record) {
        val song = current ?: return
        lyricsRepository.choose(song, record) { result ->
            if (current?.uri != song.uri) return@choose
            lyricsForUri = song.uri
            lyricsOffset = 0L
            lyrics = result
            lyricsLoading = false
        }
    }

    // ---------- Volume and audio output ----------

    fun refreshVolume() {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun changeVolume(value: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        volume = value
    }

    fun outputDevices(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in OUTPUT_TYPES }
            .distinctBy { deviceLabel(it) }

    fun deviceLabel(device: AudioDeviceInfo): String = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Este celular"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Fone com fio"
        else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Dispositivo externo"
    }

    fun selectOutput(device: AudioDeviceInfo) {
        preferredDevice = device
        player?.setPreferredDevice(device)
        updateOutputName()
    }

    fun updateOutputName() {
        val device = player?.routedDevice ?: preferredDevice
        outputName = device?.let(::deviceLabel) ?: "Este celular"
    }

    // ---------- Misc ----------

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun release() {
        handler.removeCallbacksAndMessages(null)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        player?.release()
        player = null
        headset.release()
        PlaybackService.commands = null
        PlaybackService.hide(context)
        scanExecutor.shutdownNow()
        artLoader.shutdown()
        lyricsRepository.shutdown()
        instance = null
    }

    companion object {
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_COVER_FOLDER = "cover_folder_uri"
        private const val KEY_SHUFFLE = "shuffle"
        private const val NOTIFICATION_ART_SIZE = 320
        private const val ART_SIZE = 1024
        private const val TICK_MS = 150L

        private val OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_LINE_ANALOG
        )

        private var instance: PlayerController? = null

        fun get(context: Context): PlayerController =
            instance ?: PlayerController(context.applicationContext).also { instance = it }
    }
}

fun formatOffset(offsetMs: Long): String =
    String.format(java.util.Locale.getDefault(), "%+.1fs", offsetMs / 1000.0)

fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
