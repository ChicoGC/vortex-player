package com.example.vortex_player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private enum class Sheet { NONE, SETTINGS, OUTPUT, LYRICS_SEARCH }

    private val handler = Handler(Looper.getMainLooper())
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("vortex", MODE_PRIVATE) }
    private val appearance by lazy { Appearance(prefs) }
    private lateinit var styler: Styler
    private lateinit var audioManager: AudioManager
    private lateinit var artLoader: ArtLoader
    private lateinit var coverPicker: CoverPicker
    private lateinit var headset: HeadsetControls
    private var notificationArt: Bitmap? = null
    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var adapter: SongAdapter

    private var player: MediaPlayer? = null
    private var songs: List<Song> = emptyList()
    private var index = -1
    private var preferredDevice: AudioDeviceInfo? = null
    private var userSeeking = false
    private var nowPlayingOpen = false
    private var sheet = Sheet.NONE
    private var settingsView: View? = null
    private var lyrics: Lyrics? = null
    private var lyricsForUri: Uri? = null
    private var lyricsVisible = false
    private var activeLine = -1
    private var lastUserScroll = 0L
    private var lyricsOffset = 0L

    private lateinit var root: View
    private lateinit var appLogo: ImageView
    private lateinit var libraryAmbient: AmbientView
    private lateinit var folderName: TextView
    private lateinit var listCard: View
    private lateinit var songList: ListView
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var btnEmptyPick: View
    private lateinit var miniPlayer: View
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayPause: ImageButton
    private lateinit var nowPlaying: View
    private lateinit var npAmbient: AmbientView
    private lateinit var npArt: ImageView
    private lateinit var npTitle: TextView
    private lateinit var npArtist: TextView
    private lateinit var npSeek: SeekBar
    private lateinit var npCurrent: TextView
    private lateinit var npRemaining: TextView
    private lateinit var npPlayPause: ImageButton
    private lateinit var npVolume: SeekBar
    private lateinit var npOutputName: TextView
    private lateinit var btnLyrics: ImageButton
    private lateinit var lyricsPanel: View
    private lateinit var lyricsScroll: ScrollView
    private lateinit var lyricsLines: LinearLayout
    private lateinit var lyricsStatus: View
    private lateinit var lyricsMessage: TextView
    private lateinit var lyricsRetry: View
    private lateinit var lyricsOffsetGroup: View
    private lateinit var lyricsOffsetLabel: TextView
    private lateinit var sheetOverlay: View
    private lateinit var sheetScrim: View
    private lateinit var sheetPanel: View
    private lateinit var sheetTitle: TextView
    private lateinit var sheetContent: FrameLayout

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onFolderChosen(uri)
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) updateNotification()
        }

    private val pickCoverFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onCoverFolderChosen(uri)
        }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (sheet != Sheet.NONE) closeSheet() else hideNowPlaying()
        }
    }

    private val updateProgress = object : Runnable {
        override fun run() {
            val p = player ?: return
            showProgress(p.currentPosition, p.duration)
            if (lyricsVisible) updateLyricsHighlight(p.currentPosition.toLong())
            if (p.isPlaying) handler.postDelayed(this, if (lyricsVisible) 150 else 500)
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateOutputLabel()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == preferredDevice?.id }) preferredDevice = null
            updateOutputLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        styler = Styler(this)
        audioManager = getSystemService(AudioManager::class.java)
        artLoader = ArtLoader(applicationContext)
        coverPicker = CoverPicker()
        headset = HeadsetControls(applicationContext, object : HeadsetControls.Listener {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onNext() = next()
            override fun onPrevious() = previous()
            override fun onSeek(positionMs: Long) = seekTo(positionMs.toInt())
        })
        PlaybackService.commands = { command ->
            when (command) {
                PlaybackService.ACTION_PLAY_PAUSE -> togglePlay()
                PlaybackService.ACTION_NEXT -> next()
                PlaybackService.ACTION_PREVIOUS -> previous()
            }
        }
        lyricsRepository = LyricsRepository(applicationContext)
        adapter = SongAdapter(this, artLoader, styler)
        volumeControlStream = AudioManager.STREAM_MUSIC

        bindViews()
        setupListeners()
        applyAppearance()

        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        onBackPressedDispatcher.addCallback(this, backCallback)
        restoreCoverFolder()
        restoreFolder()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        appLogo = findViewById(R.id.appLogo)
        appLogo.setImageBitmap(Logo.bitmap(this))
        libraryAmbient = findViewById(R.id.libraryAmbient)
        folderName = findViewById(R.id.folderName)
        listCard = findViewById(R.id.listCard)
        songList = findViewById(R.id.songList)
        emptyState = findViewById(R.id.emptyState)
        emptyText = findViewById(R.id.emptyText)
        btnEmptyPick = findViewById(R.id.btnEmptyPick)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniArt = findViewById(R.id.miniArt)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        nowPlaying = findViewById(R.id.nowPlaying)
        npAmbient = findViewById(R.id.npAmbient)
        npArt = findViewById(R.id.npArt)
        npTitle = findViewById(R.id.npTitle)
        npArtist = findViewById(R.id.npArtist)
        npSeek = findViewById(R.id.npSeek)
        npCurrent = findViewById(R.id.npCurrent)
        npRemaining = findViewById(R.id.npRemaining)
        npPlayPause = findViewById(R.id.npPlayPause)
        npVolume = findViewById(R.id.npVolume)
        npOutputName = findViewById(R.id.npOutputName)
        btnLyrics = findViewById(R.id.btnLyrics)
        lyricsPanel = findViewById(R.id.lyricsPanel)
        lyricsScroll = findViewById(R.id.lyricsScroll)
        lyricsLines = findViewById(R.id.lyricsLines)
        lyricsStatus = findViewById(R.id.lyricsStatus)
        lyricsMessage = findViewById(R.id.lyricsMessage)
        lyricsRetry = findViewById(R.id.lyricsRetry)
        lyricsOffsetGroup = findViewById(R.id.lyricsOffsetGroup)
        lyricsOffsetLabel = findViewById(R.id.lyricsOffsetLabel)
        sheetOverlay = findViewById(R.id.sheetOverlay)
        sheetScrim = findViewById(R.id.sheetScrim)
        sheetPanel = findViewById(R.id.sheetPanel)
        sheetTitle = findViewById(R.id.sheetTitle)
        sheetContent = findViewById(R.id.sheetContent)
    }

    private fun setupListeners() {
        songList.adapter = adapter
        songList.setOnItemClickListener { _, _, position, _ -> playAt(position) }

        findViewById<View>(R.id.btnPickFolder).setOnClickListener { pickFolder.launch(null) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openSettings() }
        btnEmptyPick.setOnClickListener { pickFolder.launch(null) }

        miniPlayer.setOnClickListener { showNowPlaying() }
        miniPlayPause.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.miniNext).setOnClickListener { next() }

        findViewById<View>(R.id.btnClose).setOnClickListener { hideNowPlaying() }
        npPlayPause.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.npNext).setOnClickListener { next() }
        findViewById<View>(R.id.npPrevious).setOnClickListener { previous() }
        findViewById<View>(R.id.btnOutput).setOnClickListener { showOutputPicker() }
        btnLyrics.setOnClickListener { toggleLyrics() }
        lyricsRetry.setOnClickListener { ensureLyrics(forceRefresh = true) }
        findViewById<View>(R.id.lyricsMinus).setOnClickListener { changeLyricsOffset(lyricsOffset - OFFSET_STEP_MS) }
        findViewById<View>(R.id.lyricsPlus).setOnClickListener { changeLyricsOffset(lyricsOffset + OFFSET_STEP_MS) }
        findViewById<View>(R.id.lyricsSearch).setOnClickListener { openLyricsSearch() }
        lyricsScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) lastUserScroll = SystemClock.uptimeMillis()
            false
        }
        sheetScrim.setOnClickListener { closeSheet() }

        npSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) showTimes(progress, sb.max)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                userSeeking = false
                seekTo(sb.progress)
                lastUserScroll = 0L
                updateLyricsHighlight(sb.progress.toLong())
            }
        })

        npVolume.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        npVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ---------- Aparência ----------

    private fun applyAppearance() {
        val theme = appearance.theme
        styler.theme = theme
        styler.glass = appearance.glass
        styler.applyTree(root)
        styler.styleSeekBar(npSeek)
        appLogo.colorFilter = Logo.colorFilter(theme)
        styler.styleSeekBar(npVolume)
        libraryAmbient.setStyle(theme, appearance.coverBackground, appearance.animated)
        npAmbient.setStyle(theme, appearance.coverBackground, appearance.animated)
        sheetScrim.setBackgroundColor(withAlpha(0xFF000000.toInt(), if (theme.isLight) 0.20f else 0.45f))
        adapter.notifyDataSetChanged()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = theme.isLight
            isAppearanceLightNavigationBars = theme.isLight
        }
        updateLyricsButton()
        lyrics?.let { renderLyrics(it) }
        settingsView?.let { bindSettings(it) }
    }

    private fun onGlassChanged(value: Float) {
        appearance.glass = value
        styler.glass = value
        styler.applyTree(root)
    }

    private fun openSettings() {
        val view = layoutInflater.inflate(R.layout.sheet_settings, sheetContent, false)
        view.findViewById<View>(R.id.coverPick).setOnClickListener { pickCoverFolder.launch(null) }
        view.findViewById<View>(R.id.coverClear).setOnClickListener { clearCoverFolder() }
        view.findViewById<View>(R.id.segCover).setOnClickListener {
            appearance.coverBackground = true
            applyAppearance()
        }
        view.findViewById<View>(R.id.segGradient).setOnClickListener {
            appearance.coverBackground = false
            applyAppearance()
        }
        view.findViewById<SeekBar>(R.id.glassSeek).apply {
            progress = (appearance.glass * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) onGlassChanged(progress / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        view.findViewById<Switch>(R.id.animSwitch).apply {
            isChecked = appearance.animated
            setOnCheckedChangeListener { _, checked ->
                appearance.animated = checked
                applyAppearance()
            }
        }
        settingsView = view
        openSheet("Aparência", view, Sheet.SETTINGS)
        bindSettings(view)
    }

    private fun bindSettings(view: View) {
        val coverFolder = prefs.getString(KEY_COVER_FOLDER, null)?.toUri()
        view.findViewById<TextView>(R.id.coverFolderName).text = when {
            coverFolder == null -> "Capa original da música"
            coverPicker.isEmpty -> "${FolderScanner.folderName(coverFolder)} · sem fotos"
            else -> FolderScanner.folderName(coverFolder)
        }
        view.findViewById<View>(R.id.coverClear).visibility = if (coverFolder != null) View.VISIBLE else View.GONE
        styler.styleSegment(view.findViewById(R.id.segCover), appearance.coverBackground)
        styler.styleSegment(view.findViewById(R.id.segGradient), !appearance.coverBackground)
        styler.styleSeekBar(view.findViewById(R.id.glassSeek))
        styler.styleSwitch(view.findViewById(R.id.animSwitch))
        buildThemeRow(view.findViewById(R.id.themeRow))
    }

    private fun buildThemeRow(row: LinearLayout) {
        row.removeAllViews()
        val current = styler.theme
        val swatchSize = styler.dp(52f).toInt()
        for (theme in Themes.ALL) {
            val selected = theme.id == current.id
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, theme.blobs).apply {
                    shape = GradientDrawable.OVAL
                    if (selected) {
                        setStroke(styler.dp(3f).toInt(), current.textPrimary)
                    } else {
                        setStroke(styler.dp(1f).toInt(), withAlpha(current.textPrimary, 0.25f))
                    }
                }
            }
            val label = TextView(this).apply {
                text = theme.name
                textSize = 12f
                setTextColor(if (selected) current.textPrimary else current.textSecondary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = styler.dp(6f).toInt() }
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = styler.dp(7f).toInt()
                setPadding(pad, 0, pad, 0)
                contentDescription = "Tema ${theme.name}"
                addView(swatch)
                addView(label)
                setOnClickListener {
                    appearance.themeId = theme.id
                    applyAppearance()
                }
            }
            row.addView(item)
        }
    }

    // ---------- Painel inferior ----------

    private fun openSheet(title: String, content: View, kind: Sheet) {
        sheetPanel.animate().cancel()
        sheetScrim.animate().cancel()
        sheetTitle.text = title
        sheetContent.removeAllViews()
        sheetContent.addView(content)
        styler.applyTree(sheetOverlay)
        sheet = kind
        updateBackCallback()

        sheetOverlay.visibility = View.VISIBLE
        sheetScrim.alpha = 0f
        sheetScrim.animate().alpha(1f).setDuration(250).start()
        sheetPanel.translationY = root.height.toFloat()
        sheetPanel.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun closeSheet() {
        if (sheet == Sheet.NONE) return
        hideKeyboard()
        sheet = Sheet.NONE
        settingsView = null
        updateBackCallback()
        sheetScrim.animate().alpha(0f).setDuration(200).start()
        sheetPanel.animate()
            .translationY(root.height.toFloat())
            .setDuration(240)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                sheetOverlay.visibility = View.GONE
                sheetContent.removeAllViews()
            }
            .start()
    }

    private fun updateBackCallback() {
        backCallback.isEnabled = sheet != Sheet.NONE || nowPlayingOpen
    }

    // ---------- Pasta ----------

    private fun restoreFolder() {
        val saved = prefs.getString(KEY_FOLDER, null)?.toUri()
        val stillAllowed = saved != null &&
            contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }
        if (stillAllowed) loadFolder(saved) else showEmpty("Escolha a pasta com as suas músicas", true)
    }

    private fun onFolderChosen(uri: Uri) {
        val old = prefs.getString(KEY_FOLDER, null)?.toUri()
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit { putString(KEY_FOLDER, uri.toString()) }
        if (old != null && old != uri) releaseIfUnused(old)
        loadFolder(uri)
    }

    // ---------- Fotos de capa ----------

    private fun restoreCoverFolder() {
        val saved = prefs.getString(KEY_COVER_FOLDER, null)?.toUri() ?: return
        val stillAllowed = contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }
        if (stillAllowed) loadCoverFolder(saved) else prefs.edit { remove(KEY_COVER_FOLDER) }
    }

    private fun onCoverFolderChosen(uri: Uri) {
        val old = prefs.getString(KEY_COVER_FOLDER, null)?.toUri()
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit { putString(KEY_COVER_FOLDER, uri.toString()) }
        if (old != null && old != uri) releaseIfUnused(old)
        loadCoverFolder(uri)
    }

    private fun clearCoverFolder() {
        val old = prefs.getString(KEY_COVER_FOLDER, null)?.toUri() ?: return
        prefs.edit { remove(KEY_COVER_FOLDER) }
        releaseIfUnused(old)
        coverPicker.setCovers(emptyList())
        settingsView?.let { bindSettings(it) }
    }

    /** The music and cover folders may be the same, so only drop the permission when neither uses it. */
    private fun releaseIfUnused(uri: Uri) {
        val inUse = listOf(KEY_FOLDER, KEY_COVER_FOLDER).any { prefs.getString(it, null)?.toUri() == uri }
        if (!inUse) {
            runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private fun loadCoverFolder(uri: Uri) {
        scanExecutor.execute {
            val images = runCatching { FolderScanner.scanImages(applicationContext, uri) }.getOrDefault(emptyList())
            handler.post {
                if (isDestroyed) return@post
                coverPicker.setCovers(images)
                settingsView?.let { bindSettings(it) }
                if (images.isEmpty()) {
                    Toast.makeText(this, "Nenhuma foto encontrada nessa pasta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadFolder(uri: Uri) {
        val name = FolderScanner.folderName(uri)
        folderName.text = name
        showEmpty("Carregando músicas…", false)
        scanExecutor.execute {
            val found = FolderScanner.scan(applicationContext, uri)
            handler.post {
                if (isDestroyed) return@post
                stopPlayback()
                songs = found
                adapter.songs = found
                folderName.text = "$name · ${found.size} músicas"
                if (found.isEmpty()) {
                    showEmpty("Nenhuma música nessa pasta", true)
                } else {
                    emptyState.visibility = View.GONE
                    listCard.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showEmpty(message: String, showButton: Boolean) {
        listCard.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyText.text = message
        btnEmptyPick.visibility = if (showButton) View.VISIBLE else View.GONE
    }

    // ---------- Reprodução ----------

    private fun playAt(position: Int) {
        index = position
        startSong()
    }

    private fun startSong() {
        val song = songs.getOrNull(index) ?: return
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
            p.setDataSource(applicationContext, song.uri)
            p.prepare()
        } catch (_: Exception) {
            p.release()
            Toast.makeText(this, "Não foi possível tocar \"${song.title}\"", Toast.LENGTH_SHORT).show()
            return
        }
        preferredDevice?.let { p.setPreferredDevice(it) }
        p.setOnCompletionListener { next() }
        p.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { updateOutputLabel() }, handler)
        p.start()
        player = p

        showSong(song, p.duration)
        updatePlayState()
        askNotificationPermissionOnce()
        handler.removeCallbacks(updateProgress)
        handler.post(updateProgress)
    }

    private fun stopPlayback() {
        handler.removeCallbacks(updateProgress)
        player?.release()
        player = null
        index = -1
        adapter.playingIndex = -1
        miniPlayer.visibility = View.GONE
        if (nowPlayingOpen) hideNowPlaying()
        headset.stopped()
        PlaybackService.hide(this)
    }

    /** Android 13+ hides notifications until allowed; ask once, on the first song played. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, true) }
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun updateNotification() {
        val p = player ?: return
        val song = songs.getOrNull(index) ?: return
        PlaybackService.show(this, PlaybackService.build(this, headset.token, song, notificationArt, p.isPlaying))
    }

    private fun togglePlay() {
        if (player?.isPlaying == true) pause() else play()
    }

    private fun play() {
        val p = player
        if (p == null) {
            if (songs.isNotEmpty()) playAt(index.coerceAtLeast(0))
            return
        }
        if (p.isPlaying) return
        p.start()
        handler.removeCallbacks(updateProgress)
        handler.post(updateProgress)
        updatePlayState()
    }

    private fun pause() {
        val p = player ?: return
        if (p.isPlaying) p.pause()
        updatePlayState()
    }

    private fun seekTo(positionMs: Int) {
        val p = player ?: return
        p.seekTo(positionMs)
        showProgress(positionMs, p.duration)
        headset.setState(p.isPlaying, positionMs.toLong())
    }

    private fun next() {
        if (songs.isEmpty()) return
        playAt((index + 1) % songs.size)
    }

    private fun previous() {
        if (songs.isEmpty()) return
        val p = player
        if (p != null && p.currentPosition > 3000) {
            seekTo(0)
            return
        }
        playAt(if (index > 0) index - 1 else songs.size - 1)
    }

    // ---------- Tela ----------

    private fun showSong(song: Song, duration: Int) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = song.title
        miniArtist.text = song.artist
        npTitle.text = song.title
        npArtist.text = song.artist
        notificationArt = null
        headset.setSong(song, duration, null)
        npSeek.max = duration
        showProgress(0, duration)
        adapter.playingIndex = index
        resetLyrics()
        if (lyricsVisible) ensureLyrics(forceRefresh = false)

        val onCover: (Bitmap?) -> Unit = { bitmap ->
            val backdrop = bitmap?.let { AmbientView.makeBackdrop(it) }
            libraryAmbient.setBackdrop(backdrop)
            npAmbient.setBackdrop(backdrop)
            notificationArt = bitmap?.let { ThumbnailUtils.extractThumbnail(it, NOTIFICATION_ART_SIZE, NOTIFICATION_ART_SIZE) }
            headset.setSong(song, duration, notificationArt)
            updateNotification()
        }
        val cover = coverPicker.next()
        if (cover != null) {
            artLoader.loadImage(cover, 160, miniArt)
            artLoader.loadImage(cover, 1024, npArt, onCover)
        } else {
            artLoader.load(song.uri, 160, miniArt)
            artLoader.load(song.uri, 1024, npArt, onCover)
        }
    }

    private fun updatePlayState() {
        val playing = player?.isPlaying == true
        player?.let { headset.setState(playing, it.currentPosition.toLong()) }
        updateNotification()
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayPause.setImageResource(icon)
        npPlayPause.setImageResource(icon)
        val scale = if (playing) 1f else 0.84f
        npArt.animate().scaleX(scale).scaleY(scale).setDuration(350).start()
    }

    private fun showProgress(position: Int, duration: Int) {
        if (!userSeeking) npSeek.progress = position
        showTimes(position, duration)
    }

    private fun showTimes(position: Int, duration: Int) {
        npCurrent.text = formatTime(position)
        npRemaining.text = "-" + formatTime((duration - position).coerceAtLeast(0))
    }

    private fun showNowPlaying() {
        if (player == null) return
        nowPlayingOpen = true
        updateBackCallback()
        nowPlaying.visibility = View.VISIBLE
        nowPlaying.translationY = root.height.toFloat()
        nowPlaying.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .start()
        refreshVolume()
        updateOutputLabel()
    }

    private fun hideNowPlaying() {
        nowPlayingOpen = false
        updateBackCallback()
        nowPlaying.animate()
            .translationY(root.height.toFloat())
            .setDuration(250)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { nowPlaying.visibility = View.GONE }
            .start()
    }

    // ---------- Letra ----------

    private fun toggleLyrics() {
        lyricsVisible = !lyricsVisible
        val show = if (lyricsVisible) lyricsPanel else npArt
        val hide = if (lyricsVisible) npArt else lyricsPanel
        hide.animate().alpha(0f).setDuration(180).withEndAction { hide.visibility = View.INVISIBLE }.start()
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(220).start()
        updateLyricsButton()
        if (lyricsVisible) {
            ensureLyrics(forceRefresh = false)
            handler.removeCallbacks(updateProgress)
            handler.post(updateProgress)
        }
    }

    private fun updateLyricsButton() {
        btnLyrics.imageTintList = ColorStateList.valueOf(
            if (lyricsVisible) styler.theme.accent else styler.theme.textPrimary
        )
    }

    private fun resetLyrics() {
        lyrics = null
        lyricsForUri = null
        lyricsOffset = 0L
        activeLine = -1
        lyricsLines.removeAllViews()
    }

    private fun ensureLyrics(forceRefresh: Boolean) {
        val song = songs.getOrNull(index) ?: return
        if (!forceRefresh && lyricsForUri == song.uri) return
        lyricsForUri = song.uri
        lyricsOffset = lyricsRepository.offset(song)
        showLyricsStatus("Procurando letra…", retry = false)
        lyricsRepository.load(song, player?.duration ?: 0, forceRefresh) { result ->
            if (lyricsForUri == song.uri) renderLyrics(result)
        }
    }

    private fun renderLyrics(result: Lyrics) {
        lyrics = result
        activeLine = -1
        lyricsLines.removeAllViews()
        when (result) {
            is Lyrics.Synced -> {
                result.lines.forEach { line ->
                    val view = addLyricLine(line.text.ifBlank { "♪" }, synced = true)
                    view.setOnClickListener {
                        val target = (line.timeMs + lyricsOffset).coerceAtLeast(0L)
                        seekTo(target.toInt())
                        lastUserScroll = 0L
                        updateLyricsHighlight(target)
                    }
                    view.setOnLongClickListener {
                        val position = player?.currentPosition ?: return@setOnLongClickListener false
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        changeLyricsOffset(position - line.timeMs)
                        Toast.makeText(this, "Letra sincronizada (${formatOffset(lyricsOffset)})", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                showLyricsLines()
                lyricsOffsetGroup.visibility = View.VISIBLE
                lyricsOffsetLabel.text = formatOffset(lyricsOffset)
                player?.let { updateLyricsHighlight(it.currentPosition.toLong()) }
                showSyncHintOnce()
            }
            is Lyrics.Plain -> {
                result.lines.forEach { addLyricLine(it, synced = false) }
                showLyricsLines()
            }
            Lyrics.Instrumental -> showLyricsStatus("Música instrumental", retry = false)
            Lyrics.NotFound -> showLyricsStatus("Letra não encontrada", retry = true)
            Lyrics.Offline -> showLyricsStatus("Sem conexão com a internet", retry = true)
        }
    }

    private fun addLyricLine(text: String, synced: Boolean): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = if (synced) 24f else 19f
            setTypeface(typeface, if (synced) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (synced) inactiveLyricColor() else styler.theme.textPrimary)
            val vertical = styler.dp(if (synced) 9f else 4f).toInt()
            setPadding(0, vertical, 0, vertical)
        }
        lyricsLines.addView(view)
        return view
    }

    private fun inactiveLyricColor() = withAlpha(styler.theme.textPrimary, 0.35f)

    private fun showLyricsLines() {
        lyricsStatus.visibility = View.GONE
        lyricsScroll.visibility = View.VISIBLE
        lyricsOffsetGroup.visibility = View.GONE
        lyricsScroll.scrollTo(0, 0)
    }

    private fun showLyricsStatus(message: String, retry: Boolean) {
        lyricsScroll.visibility = View.GONE
        lyricsOffsetGroup.visibility = View.GONE
        lyricsStatus.visibility = View.VISIBLE
        lyricsMessage.text = message
        lyricsRetry.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun changeLyricsOffset(offsetMs: Long) {
        val song = songs.getOrNull(index) ?: return
        lyricsOffset = offsetMs
        lyricsRepository.setOffset(song, offsetMs)
        lyricsOffsetLabel.text = formatOffset(offsetMs)
        for (i in 0 until lyricsLines.childCount) {
            (lyricsLines.getChildAt(i) as? TextView)?.setTextColor(inactiveLyricColor())
        }
        activeLine = -1
        lastUserScroll = 0L
        player?.let { updateLyricsHighlight(it.currentPosition.toLong()) }
    }

    private fun formatOffset(offsetMs: Long): String =
        String.format(Locale.getDefault(), "%+.1fs", offsetMs / 1000.0)

    private fun showSyncHintOnce() {
        if (prefs.getBoolean(KEY_SYNC_HINT, false)) return
        prefs.edit { putBoolean(KEY_SYNC_HINT, true) }
        Toast.makeText(this, "Dica: segure numa linha quando ela for cantada para sincronizar", Toast.LENGTH_LONG).show()
    }

    private fun openLyricsSearch() {
        val song = songs.getOrNull(index) ?: return
        val view = layoutInflater.inflate(R.layout.sheet_lyrics_search, sheetContent, false)
        val input = view.findViewById<EditText>(R.id.searchInput)
        val status = view.findViewById<TextView>(R.id.searchStatus)
        val results = view.findViewById<LinearLayout>(R.id.searchResults)

        fun runSearch() {
            val query = input.text.toString().trim()
            if (query.isEmpty()) return
            hideKeyboard()
            results.removeAllViews()
            status.visibility = View.VISIBLE
            status.text = "Buscando…"
            lyricsRepository.search(query) { records ->
                if (sheet != Sheet.LYRICS_SEARCH || songs.getOrNull(index)?.uri != song.uri) return@search
                when {
                    records == null -> status.text = "Sem conexão com a internet"
                    records.isEmpty() -> status.text = "Nenhum resultado. Tente outro nome."
                    else -> {
                        status.visibility = View.GONE
                        records.forEach { addLyricsResult(results, song, it) }
                        styler.applyTree(results)
                    }
                }
            }
        }

        input.setText(lyricsRepository.defaultQuery(song))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }
        view.findViewById<View>(R.id.searchButton).setOnClickListener { runSearch() }
        openSheet("Buscar letra", view, Sheet.LYRICS_SEARCH)
        input.setHintTextColor(styler.theme.textSecondary)
        runSearch()
    }

    private fun addLyricsResult(container: LinearLayout, song: Song, record: LrcLib.Record) {
        val row = layoutInflater.inflate(R.layout.item_lyrics_result, container, false)
        row.findViewById<TextView>(R.id.resultTitle).text = record.trackName ?: "Sem título"
        val kind = when {
            !record.synced.isNullOrBlank() -> "Sincronizada"
            record.instrumental -> "Instrumental"
            else -> "Sem sincronia"
        }
        row.findViewById<TextView>(R.id.resultInfo).text =
            listOfNotNull(record.artistName, formatTime((record.duration * 1000).toInt()), kind).joinToString(" · ")
        row.setOnClickListener {
            lyricsRepository.choose(song, record) { result ->
                if (songs.getOrNull(index)?.uri != song.uri) return@choose
                lyricsForUri = song.uri
                lyricsOffset = 0L
                renderLyrics(result)
            }
            closeSheet()
        }
        container.addView(row)
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    private fun updateLyricsHighlight(positionMs: Long) {
        val synced = lyrics as? Lyrics.Synced ?: return
        val line = synced.lines.indexOfLast { it.timeMs + lyricsOffset <= positionMs }
        if (line == activeLine) return
        (lyricsLines.getChildAt(activeLine) as? TextView)?.setTextColor(inactiveLyricColor())
        (lyricsLines.getChildAt(line) as? TextView)?.setTextColor(styler.theme.textPrimary)
        activeLine = line
        val view = lyricsLines.getChildAt(line) ?: return
        if (SystemClock.uptimeMillis() - lastUserScroll > USER_SCROLL_PAUSE_MS) {
            val target = view.top + view.height / 2 - lyricsScroll.height / 2
            lyricsScroll.smoothScrollTo(0, target.coerceAtLeast(0))
        }
    }

    // ---------- Volume ----------

    private fun refreshVolume() {
        npVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    override fun onResume() {
        super.onResume()
        refreshVolume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handler.post { refreshVolume() }
        }
        return handled
    }

    // ---------- Saída de áudio ----------

    private fun outputDevices(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in OUTPUT_TYPES }
            .distinctBy { deviceLabel(it) }

    private fun deviceLabel(device: AudioDeviceInfo): String = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Este celular"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Fone com fio"
        else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Dispositivo externo"
    }

    private fun showOutputPicker() {
        val devices = outputDevices()
        if (devices.isEmpty()) return
        val currentLabel = (player?.routedDevice ?: preferredDevice)?.let(::deviceLabel)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (device in devices) {
            val label = deviceLabel(device)
            val row = layoutInflater.inflate(R.layout.item_output, list, false)
            row.findViewById<TextView>(R.id.outputName).text = label
            row.findViewById<View>(R.id.outputCheck).visibility =
                if (label == currentLabel) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener {
                preferredDevice = device
                player?.setPreferredDevice(device)
                updateOutputLabel()
                closeSheet()
            }
            list.addView(row)
        }
        openSheet("Saída de áudio", list, Sheet.OUTPUT)
    }

    private fun updateOutputLabel() {
        val device = player?.routedDevice ?: preferredDevice
        npOutputName.text = device?.let(::deviceLabel) ?: "Este celular"
    }

    // ---------- Utilitários ----------

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        player?.release()
        player = null
        headset.release()
        PlaybackService.commands = null
        PlaybackService.hide(this)
        scanExecutor.shutdownNow()
        artLoader.shutdown()
        lyricsRepository.shutdown()
    }

    private companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_COVER_FOLDER = "cover_folder_uri"
        const val USER_SCROLL_PAUSE_MS = 3000L
        const val OFFSET_STEP_MS = 500L
        const val NOTIFICATION_ART_SIZE = 320
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        const val KEY_SYNC_HINT = "lyrics_sync_hint_shown"

        val OUTPUT_TYPES = setOf(
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
    }
}
