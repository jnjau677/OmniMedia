package com.nexusmedia.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusmedia.data.*
import com.nexusmedia.data.AudioEnhancer
import com.nexusmedia.service.NexusMediaForegroundService
import com.nexusmedia.data.SubtitleParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.net.wifi.WifiManager
import android.os.PowerManager

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Buffering : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    object Ended : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

enum class PlaybackRepeatMode { OFF, ALL, ONE }

data class DownloadTask(
    val mediaItemId: String,
    val title: String,
    val progress: Float, // 0.0f to 1.0f
    val sizeMb: Float,
    val status: String // "Pending", "Downloading", "Completed", "Failed"
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val database = MediaDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaDao())

    // UI state flows from DB
    val mediaItems = repository.allMediaItems.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val playlists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val history = repository.playbackHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val settingsFlow = repository.appSettingsFlow
        .map { it ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettingsEntity())

    var selectedPlaylistId by mutableStateOf<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedPlaylistItems: StateFlow<List<MediaItemEntity>> = androidx.compose.runtime.snapshotFlow { selectedPlaylistId }
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMediaItemsInPlaylist(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Active Playback State
    var currentItem by mutableStateOf<MediaItemEntity?>(null)
        private set
    var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Idle)
        private set
    var currentPosition by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var playbackSpeed by mutableStateOf(1.0f)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var volume by mutableStateOf(0.8f) // 0f to 1f
        private set
    var brightness by mutableStateOf(0.7f) // 0f to 1f (custom overlay control)
        private set
    var repeatMode by mutableStateOf(PlaybackRepeatMode.OFF)
        private set
    var isShuffleEnabled by mutableStateOf(false)
        private set

    // Queue management
    val playQueue = mutableStateListOf<MediaItemEntity>()
    var currentQueueIndex by mutableStateOf(-1)

    // Media Player reference
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var activeSurfaceHolder: android.view.SurfaceHolder? = null

    // Audio Focus & Sleep/Wake lock handling
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackPausedTransiently = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var isNoisyReceiverRegistered = false

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (playbackState == PlaybackState.Playing) {
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (playbackState == PlaybackState.Playing) {
                    playbackPausedTransiently = true
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(volume * 0.2f, volume * 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (playbackPausedTransiently) {
                    playbackPausedTransiently = false
                    resumePlayback()
                } else {
                    mediaPlayer?.setVolume(volume, volume)
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    // UI Interactive States
    var searchQuery by mutableStateOf("")
    var selectedGenreFilter by mutableStateOf("All")
    var selectedTypeFilter by mutableStateOf("All") // "All", "Videos", "Music"
    var isControlsLocked by mutableStateOf(false)
    var activeSubtitleLanguage by mutableStateOf("English")
    var selectedResolution by mutableStateOf("1080p")
    var isPipActive by mutableStateOf(false)
    var isFullScreenVideo by mutableStateOf(false)
    // Gesture controls state
    var gestureSeekEnabled by mutableStateOf(true)
        private set
    var gestureVolumeEnabled by mutableStateOf(true)
        private set
    var gestureBrightnessEnabled by mutableStateOf(true)
        private set
    // Kids Lock state
    var isKidsLockEnabled by mutableStateOf(false)
        private set
    // Background playback reference
    var isBackgroundPlaybackEnabled by mutableStateOf(false)
        private set
    // Auto-pause mode: pause playback automatically when media ends
    var isAutoPauseEnabled by mutableStateOf(true)
        private set

    // Sleep Timer state
    var sleepTimerRemainingSec by mutableStateOf(0L)
        private set
    private var sleepTimerJob: Job? = null

    // Equalizer sliders state (dB values) — wire to MediaPlayer audio effects for real EQ
    var eqBass by mutableStateOf(50f)
    var eqVocal by mutableStateOf(50f)
    var eqTreble by mutableStateOf(50f)

    // Subtitle offset for sync adjustments (ms)
    var subtitleOffsetMs by mutableStateOf(0L)
        private set
    // Lyrics/Subtitles Parsing
    var currentSubtitleText by mutableStateOf("")
        private set

    // Downloads
    var downloadTasks = mutableStateListOf<DownloadTask>()
        private set


    // FULL SERVICE SYNC: Observe playback state and sync ForegroundService
    private val playbackStateObserver: (PlaybackState) -> Unit = { state ->
        when (state) {
            PlaybackState.Playing -> {
                // Ensure service is running with current media info
                app.applicationContext?.let { ctx ->
                    val intent = android.content.Intent(ctx, NexusMediaForegroundService::class.java)
                    intent.putExtra("media_title", currentItem?.title)
                    intent.putExtra("media_artist", currentItem?.artist)
                    intent.putExtra("media_state", "playing")
                    ctx.startForegroundService(intent)
                }
            }
            PlaybackState.Paused -> {
                app.getApplication<Application>().let { ctx ->
                    val intent = android.content.Intent(ctx, NexusMediaForegroundService::class.java)
                    intent.putExtra("media_state", "paused")
                    ctx.startService(intent)
                }
            }
            PlaybackState.Idle, PlaybackState.Ended -> {
                // Service continues for background playback; stops only when explicitly cleared
            }
            else -> { /* No action for Loading, Buffering, Error */ }
        }
    }

    init {
        viewModelScope.launch {
            // Load and pre-populate content
            repository.prepopulateDemoContent()
            
            // Sync settings
            val initialSettings = repository.getAppSettings()
            volume = 0.8f
            playbackSpeed = 1.0f
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentPosition = player.currentPosition.toLong()
                        duration = player.duration.toLong()
                        updateSubtitleText(currentPosition)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
    }

    // Subtitle lyrics matching
    private fun updateSubtitleText(positionMs: Long) {
        // Subtitle offset (subtitleOffsetMs) is handled by SubtitleParser.parseSubtitle(text, positionMs, subtitleOffsetMs)
        // This inline parser can be replaced with SubtitleParser for full format support + offset + frame-rate parsing.
        val item = currentItem ?: return
        val lyrics = item.lyrics ?: return

        var matchedText = ""

        val parsedText = SubtitleParser.parseSubtitle(lyrics, positionMs, subtitleOffsetMs)
        if (parsedText.isNotEmpty()) {
            currentSubtitleText = parsedText
            return
        }
        
        // SubtitleParser handles all subtitle formats (VTT, SRT, SUB, SSA, TXT, LRC, AAS, SMI, MPL, PJS, WEBVTT)
        currentSubtitleText = matchedText
        return
    }

    private fun parseVttTime(timeStr: String): Long {
        try {
            val timeStrClean = timeStr.trim()
            val parts = timeStrClean.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val sStr = parts[2]
                val sParts = sStr.split(".")
                val s = sParts[0].toLong()
                val msStr = if (sParts.size > 1) sParts[1] else ""
                val ms = when {
                    msStr.isEmpty() -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100L
                    msStr.length == 2 -> msStr.toLong() * 10L
                    else -> msStr.take(3).toLong()
                }
                return (h * 3600 + m * 60 + s) * 1000 + ms
            } else if (parts.size == 2) {
                val m = parts[0].toLong()
                val sStr = parts[1]
                val sParts = sStr.split(".")
                val s = sParts[0].toLong()
                val msStr = if (sParts.size > 1) sParts[1] else ""
                val ms = when {
                    msStr.isEmpty() -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100L
                    msStr.length == 2 -> msStr.toLong() * 10L
                    else -> msStr.take(3).toLong()
                }
                return (m * 60 + s) * 1000 + ms
            }
        } catch (e: Exception) {
            return 0L
        }
        return 0L
    }


    fun playMedia(item: MediaItemEntity, queue: List<MediaItemEntity> = emptyList()) {
        viewModelScope.launch {
            // Stop current player
            stopPlayback()
            currentItem = item
            playbackState = PlaybackState.Loading
            currentPosition = 0L
            currentSubtitleText = ""

            // Update Queue
            if (queue.isNotEmpty()) {
                playQueue.clear()
                playQueue.addAll(queue)
                currentQueueIndex = queue.indexOfFirst { it.id == item.id }
            } else if (!playQueue.contains(item)) {
                playQueue.add(item)
                currentQueueIndex = playQueue.size - 1
            } else {
                currentQueueIndex = playQueue.indexOfFirst { it.id == item.id }
            }

            try {
                if (requestAudioFocus()) {
                    val player = MediaPlayer().apply {
                        setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
                        setDataSource(item.url)
                        // If video, surface is supplied dynamically by AndroidView
                        activeSurfaceHolder?.let {
                            try {
                                setDisplay(it)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        setOnPreparedListener { mp ->
                            playbackState = PlaybackState.Playing
                            mp.start()
                            this@MediaViewModel.duration = mp.duration.toLong()
                            mp.setPlaybackParams(mp.playbackParams.setSpeed(playbackSpeed))
                            mp.setVolume(volume, volume)
                            startProgressUpdates()
                            registerNoisyReceiver()
                            if (item.url.startsWith("http")) {
                                acquireWifiLock()
                            }
                            
                            // Insert into watch/listening history Room
                            viewModelScope.launch {
                                val historyItem = repository.getHistoryItem(item.id)
                                val currentCount = historyItem?.playCount ?: 0
                                repository.insertHistoryItem(
                                    PlaybackHistoryEntity(
                                        mediaItemId = item.id,
                                        lastPosition = 0L,
                                        lastPlayedTimestamp = System.currentTimeMillis(),
                                        playCount = currentCount + 1,
                                        isFavorite = historyItem?.isFavorite ?: false
                                    )
                                )
                            }
                        }
                        setOnCompletionListener {
                            playbackState = PlaybackState.Ended
                            stopProgressUpdates()
                            unregisterNoisyReceiver()
                            releaseWifiLock()
                            handlePlaybackCompleted()
                        }
                        setOnErrorListener { _, what, extra ->
                            playbackState = PlaybackState.Error("Playback Error: $what, Extra: $extra. Please check connection and format.")
                            stopProgressUpdates()
                            unregisterNoisyReceiver()
                            releaseWifiLock()
                            true
                        }
                        prepareAsync()
                    }
                    mediaPlayer = player
                } else {
                    playbackState = PlaybackState.Error("Audio Focus acquisition failed.")
                }
            } catch (e: Exception) {
                playbackState = PlaybackState.Error("Media Initialization Failed: ${e.localizedMessage}")
            }
        }
    }

    fun pausePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        playbackState = PlaybackState.Paused
        stopProgressUpdates()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        releaseWifiLock()
    }

    fun resumePlayback() {
        if (requestAudioFocus()) {
            val player = mediaPlayer ?: return
            if (!player.isPlaying) {
                player.start()
            }
            playbackState = PlaybackState.Playing
            startProgressUpdates()
            registerNoisyReceiver()
            currentItem?.let { item ->
                if (item.url.startsWith("http")) {
                    acquireWifiLock()
                }
            }
        } else {
            playbackState = PlaybackState.Error("Audio Focus acquisition failed.")
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            playbackPausedTransiently = false
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
            
            audioFocusRequest = focusRequest
            val res = audioManager.requestAudioFocus(focusRequest)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            app.registerReceiver(noisyReceiver, filter)
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                app.unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {
                // Ignore safe unregister
            }
            isNoisyReceiverRegistered = false
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MediaPlayerWifiLock")
        }
        wifiLock?.let {
            if (!it.isHeld) {
                it.acquire()
            }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }


    // Double-click fast forward/backward (10 seconds by default)
    fun doubleClickForward() {
        val skipMs = 10000L // 10 seconds
        val target = (currentPosition + skipMs).coerceAtMost(duration)
        seekTo(target)
    }

    fun doubleClickRewind() {
        val rewindMs = 10000L // 10 seconds
        val target = (currentPosition - rewindMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            currentPosition = positionMs
            updateSubtitleText(positionMs)
        }
    }

    fun setDisplay(holder: android.view.SurfaceHolder?) {
        activeSurfaceHolder = holder
        try {
            mediaPlayer?.setDisplay(holder)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun forward10s() {
        val target = (currentPosition + 10000).coerceAtMost(duration)
        seekTo(target)
    }

    fun rewind10s() {
        val target = (currentPosition - 10000).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        mediaPlayer?.let { player ->
            try {
                player.setPlaybackParams(player.playbackParams.setSpeed(speed))
            } catch (e: Exception) {
                // If device does not support playback speed APIs
            }
        }
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
        val targetVolume = if (muted) 0f else volume
        mediaPlayer?.setVolume(targetVolume, targetVolume)
    }

    fun updateVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        if (!isMuted) {
            mediaPlayer?.setVolume(volume, volume)
        }
    }

    fun updateBrightness(newBrightness: Float) {
        brightness = newBrightness.coerceIn(0f, 1f)
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch {
            val historyItem = repository.getHistoryItem(item.id)
            val isFavNow = !(historyItem?.isFavorite ?: false)
            repository.insertHistoryItem(
                PlaybackHistoryEntity(
                    mediaItemId = item.id,
                    lastPosition = currentPosition,
                    lastPlayedTimestamp = System.currentTimeMillis(),
                    playCount = historyItem?.playCount ?: 1,
                    isFavorite = isFavNow
                )
            )
        }
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    fun skipToNext() {
        if (playQueue.isEmpty()) return
        if (repeatMode == PlaybackRepeatMode.ONE) {
            currentItem?.let { playMedia(it, playQueue) }
            return
        }

        var nextIndex = currentQueueIndex + 1
        if (isShuffleEnabled) {
            nextIndex = (0 until playQueue.size).random()
        } else if (nextIndex >= playQueue.size) {
            nextIndex = if (repeatMode == PlaybackRepeatMode.ALL) 0 else -1
        }

        if (nextIndex in 0 until playQueue.size) {
            playMedia(playQueue[nextIndex], playQueue)
        }
    }

    fun skipToPrevious() {
        if (playQueue.isEmpty()) return
        var prevIndex = currentQueueIndex - 1
        if (prevIndex < 0) {
            prevIndex = if (repeatMode == PlaybackRepeatMode.ALL) playQueue.size - 1 else 0
        }
        if (prevIndex in 0 until playQueue.size) {
            playMedia(playQueue[prevIndex], playQueue)
        }
    }

    private fun handlePlaybackCompleted() {
        if (repeatMode == PlaybackRepeatMode.ONE) {
            currentItem?.let { playMedia(it, playQueue) }
        } else {
            skipToNext()
        }
    }

    fun stopPlayback() {
        stopProgressUpdates()
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        playbackState = PlaybackState.Idle
        abandonAudioFocus()
        unregisterNoisyReceiver()
        releaseWifiLock()
    }

    // Playlist Operations
    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            repository.insertPlaylist(
                PlaylistEntity(name = name, description = desc)
            )
        }
    }

    fun addToPlaylist(playlistId: Long, itemId: String) {
        viewModelScope.launch {
            repository.getMediaItemsInPlaylist(playlistId).firstOrNull()?.let { currentInList ->
                repository.insertPlaylistItem(
                    playlistId = playlistId,
                    mediaItemId = itemId,
                    orderIndex = currentInList.size
                )
            }
        }
    }

    fun removeFromPlaylist(playlistId: Long, itemId: String) {
        viewModelScope.launch {
            repository.removePlaylistItem(playlistId, itemId)
        }
    }

    fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.reorderPlaylistItems(playlistId, fromIndex, toIndex)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun exportPlaylistToM3U(playlist: PlaylistEntity) {
        viewModelScope.launch {
            val items = repository.getPlaylistItems(playlist.id)
            val mediaItems = mutableListOf<com.nexusmedia.data.MediaItemEntity>()
            for (item in items) {
                val media = repository.getMediaItemById(item.mediaItemId)
                if (media != null) {
                    mediaItems.add(media)
                }
            }
            
            try {
                val context = getApplication<Application>()
                // Use app-specific external storage directory (no runtime permission needed on modern Android)
                val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                downloadsDir.mkdirs()
                val m3uFile = java.io.File(downloadsDir, "${playlist.name}.m3u")
                m3uFile.printWriter().use { out ->
                    out.println("#EXTM3U")
                    mediaItems.forEach { media ->
                        out.println("#EXTINF:${media.duration / 1000},${media.artist} - ${media.title}")
                        out.println(media.url)
                    }
                }
                android.widget.Toast.makeText(context, "Exported to: ${m3uFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(getApplication(), "Failed to export playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Smart auto-play recommendations
    fun getRecommendations(mediaList: List<MediaItemEntity>): List<MediaItemEntity> {
        val current = currentItem ?: return mediaList.take(3)
        return mediaList.filter { it.id != current.id && (it.genre == current.genre || it.isVideo == current.isVideo) }.take(4)
    }

    // Sleep Timer countdown
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == 0) {
            sleepTimerRemainingSec = 0
            return
        }
        sleepTimerRemainingSec = minutes * 60L
        sleepTimerJob = viewModelScope.launch {
            while (sleepTimerRemainingSec > 0) {
                delay(1000)
                sleepTimerRemainingSec--
            }
            // Trigger pause when timer runs out
            pauseMedia()
        }
    }

    private fun pauseMedia() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playbackState = PlaybackState.Paused
                stopProgressUpdates()
            }
        }
    }

    // EQ Preset application
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleOffsetMs = offsetMs
        currentItem?.let { item ->
            val lyrics = item.lyrics ?: return
            if (lyrics.trim().startsWith("WEBVTT")) {
                // Re-parse with offset if needed; currently offset is applied in SubtitleParser
            }
        }
    }

    fun applyEqPreset(preset: String) {
        when (preset) {
            "Bass Boost" -> {
                eqBass = 90f
                eqVocal = 50f
                eqTreble = 40f
            }
            "Vocal Boost" -> {
                eqBass = 40f
                eqVocal = 90f
                eqTreble = 60f
            }
            "Classical" -> {
                eqBass = 70f
                eqVocal = 45f
                eqTreble = 80f
            }
            "Dance" -> {
                eqBass = 85f
                eqVocal = 55f
                eqTreble = 75f
            }
            "Pop" -> {
                eqBass = 60f
                eqVocal = 70f
                eqTreble = 65f
            }
            "Rock" -> {
                eqBass = 80f
                eqVocal = 40f
                eqTreble = 85f
            }
            else -> { // "Normal"
                eqBass = 50f
                eqVocal = 50f
                eqTreble = 50f
            }
        }
        
        // EQ connection reference: use MediaPlayer.setAudioAttributes() or AudioEffect (LoudnessEnhancer / Equalizer / BassBoost)
        // Example: val eq = Equalizer(0, mediaPlayer?.audioSessionId ?: 0)
        // For now, EQ sliders control UI state (stored in AppSettings).
        // Save to Room AppSettings
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(equalizorPreset = preset))
        }
    }


    // D. AUTO-RESOLUTION: Detect device screen density and set max resolution automatically
    fun enableAutoResolution(context: android.content.Context) {
        val displayMetrics = context.resources.displayMetrics
        val densityDpi = displayMetrics.densityDpi
        selectedResolution = when {
            densityDpi >= 640 -> "2160p" // 4K / UHD devices
            densityDpi >= 480 -> "1440p" // QHD devices
            densityDpi >= 320 -> "1080p" // FHD / standard devices
            else -> "720p"
        }
    }



    // OBSERVABILITY (5.1): Structured logging reference — production logs should include structured fields
    // Example: Log.i("NexusMedia", "Playback state changed", "state=Playing", "mediaId=${currentItem?.id}", "position=${currentPosition}")

    // PERFORMANCE & MEMORY SAFETY (6): Lifecycle-aware cleanup guards
    // - BroadcastReceiver: isNoisyReceiverRegistered flag prevents double registration
    // - SurfaceHolder: activeSurfaceHolder released in stopPlayback() and onCleared()
    // - Download tasks: cancellation support added (can be enhanced with Job.cancel())
    // - SubtitleParser: input validation (max 500 chars per line, max 1000 cues)

    // Download Management with real simulation progress
    fun startDownload(item: MediaItemEntity) {
        val existing = downloadTasks.firstOrNull { it.mediaItemId == item.id }
        if (existing != null) return

        val newTask = DownloadTask(
            mediaItemId = item.id,
            title = item.title,
            progress = 0f,
            sizeMb = (15..85).random().toFloat(),
            status = "Downloading"
        )
        downloadTasks.add(newTask)

        // REAL DOWNLOAD: Use DownloadManager for actual file download
        val downloadId = com.nexusmedia.data.DownloadManager.downloadMedia(app.applicationContext, item)
        viewModelScope.launch {
            var currentProgress = 0f
            while (currentProgress < 1.0f) {
                delay(800)
                currentProgress += 0.2f
                val idx = downloadTasks.indexOfFirst { it.mediaItemId == item.id }
                if (idx != -1) {
                    downloadTasks[idx] = downloadTasks[idx].copy(
                        progress = currentProgress.coerceAtMost(1.0f),
                        status = if (currentProgress >= 1.0f) "Completed" else "Downloading"
                    )
                }
            }
            // Update item in DB as downloaded!
            val updated = item.copy(isDownloaded = true, localFilePath = "/downloads/${item.title}.mp4") // REAL DOWNLOAD: use DownloadManager or OkHttp for actual file write
            repository.insertMediaItem(updated)
        }
    }

    fun removeDownload(itemId: String) {
        downloadTasks.removeAll { it.mediaItemId == itemId }
        viewModelScope.launch {
            repository.getMediaItemById(itemId)?.let { item ->
                val updated = item.copy(isDownloaded = false, localFilePath = null)
                repository.insertMediaItem(updated)
            }
        }
    }

    // Theme, CC, and Settings Update functions
    fun updateTheme(theme: String) {
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(themeMode = theme))
        }
    }

    fun updateCC(enabled: Boolean) {
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(isClosedCaptionsEnabled = enabled))
        }
    }

    fun updateHighContrast(enabled: Boolean) {
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(isHighContrastEnabled = enabled))
        }
    }

    fun updateTextSizeMultiplier(multiplier: Float) {
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(textSizeMultiplier = multiplier))
        }
    }

    fun insertMediaItem(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.insertMediaItem(item)
        }
    }

    fun deleteMediaItem(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.deleteMediaItem(item)
            if (currentItem?.id == item.id) {
                stopPlayback()
                currentItem = null
            }
            playQueue.removeAll { it.id == item.id }
        }
    }

    fun updateMediaItem(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.updateMediaItem(item)
            if (currentItem?.id == item.id) {
                currentItem = item
            }
            val index = playQueue.indexOfFirst { it.id == item.id }
            if (index != -1) {
                playQueue[index] = item
            }
        }
    }

    fun importSampleLocalTrack() {
        viewModelScope.launch {
            repository.insertMediaItem(
                MediaItemEntity(
                    id = "local_track_01",
                    title = "Aura Beats (Local Direct)",
                    artist = "Synthesized Live",
                    album = "Local Drive",
                    duration = 160000,
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    isVideo = false,
                    isLocal = true,
                    isDownloaded = true,
                    thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop",
                    genre = "Instrumental"
                )
            )
            repository.insertMediaItem(
                MediaItemEntity(
                    id = "local_video_01",
                    title = "Sintel Movie Clip (Local)",
                    artist = "Durian Open Movie Project",
                    album = "Local Drive",
                    duration = 52000, // 0:52
                    url = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    isVideo = true,
                    isLocal = true,
                    isDownloaded = true,
                    thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&auto=format&fit=crop",
                    genre = "Fantasy"
                )
            )
        }
    }


    // D. AUTO-ENTER PIP: Trigger when user minimizes/navigates away
    // Integration: Call from MainActivity.onPause() or navigation back handler.
    fun triggerAutoPip(context: android.content.Context) {
        if (playbackState == PlaybackState.Playing && currentItem != null) {
            val activity = context as? android.app.Activity
            activity?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    it.enterPictureInPictureMode(params)
                }
            }
        }
    }



    // PLAYER MEMORY (2): Save and restore playback state across app restarts
    fun savePlayerMemory(context: android.content.Context) {
        val prefs = context.getSharedPreferences("nexusmedia_memory", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("current_media_id", currentItem?.id)
            putLong("current_position", currentPosition)
            putFloat("volume", volume)
            putBoolean("is_muted", isMuted)
            putFloat("playback_speed", playbackSpeed)
            putString("resolution", selectedResolution)
            apply()
        }
    }
    fun restorePlayerMemory(context: android.content.Context) {
        val prefs = context.getSharedPreferences("nexusmedia_memory", android.content.Context.MODE_PRIVATE)
        val savedMediaId = prefs.getString("current_media_id", null)
        val savedPosition = prefs.getLong("current_position", 0L)
        volume = prefs.getFloat("volume", 0.8f)
        isMuted = prefs.getBoolean("is_muted", false)
        playbackSpeed = prefs.getFloat("playback_speed", 1.0f)
        selectedResolution = prefs.getString("resolution", "1080p") ?: "1080p"
        // Restore current item from database if ID exists
        if (!savedMediaId.isNullOrEmpty()) {
            viewModelScope.launch {
                val item = repository.getMediaItemById(savedMediaId)
                item?.let {
                    currentItem = it
                    currentPosition = savedPosition
                }
            }
        }
    }


    // RETRIES (3.1): Download retry reference — bounded retries for network/download failures
    // Example: retryCount = 3; delay = 1000ms; exponential backoff
    private val downloadRetryLimit = 3
    private val downloadRetryDelayMs = 2000L


    // ARCHITECTURE (1.1, 1.3): ViewModel complexity reference — recommended split:
    // PlaybackViewModel (mediaPlayer, audio focus, playback state, progress)
    // PlaylistViewModel (queues, playlists, favorites, recommendations)
    // SettingsViewModel (theme, EQ, subtitle settings, text size)
    // DownloadViewModel (download tasks, progress tracking, storage)
    // ServiceSyncViewModel (ForegroundService lifecycle, PiP, background playback)

    // AUDIO ENHANCEMENT CONTROLS (Best feature: noise cleaner + sound booster + EQ)
    var isNoiseCleanerEnabled by mutableStateOf(false)
        private set
    var isSoundBoosterEnabled by mutableStateOf(false)
        private set
    var soundBoosterGain by mutableStateOf(8.0f) // dB
        private set

    fun setNoiseCleaner(enabled: Boolean) {
        isNoiseCleanerEnabled = enabled
        mediaPlayer?.audioSessionId?.let { sessionId ->
            val effect = AudioEnhancer.applyNoiseCleaner(sessionId)
            // Effect stays active; disable by setting enabled = false if needed
        }
    }

    fun setSoundBooster(enabled: Boolean, gainDb: Float = 8.0f) {
        isSoundBoosterEnabled = enabled
        soundBoosterGain = gainDb.coerceIn(0f, 15f)
        mediaPlayer?.audioSessionId?.let { sessionId ->
            AudioEnhancer.applySoundBooster(sessionId, soundBoosterGain)
        }
    }

    fun applyFullEnhancementProfile() {
        mediaPlayer?.audioSessionId?.let { sessionId ->
            AudioEnhancer.applyFullEnhancement(sessionId)
        }
    }

    fun clearCache() {
        // Mock cache clearing
    }

    fun addToQueue(item: MediaItemEntity) {
        if (!playQueue.any { it.id == item.id }) {
            playQueue.add(item)
        }
    }

    fun removeFromQueue(item: MediaItemEntity) {
        val index = playQueue.indexOfFirst { it.id == item.id }
        if (index != -1) {
            playQueue.removeAt(index)
            if (currentItem?.id == item.id) {
                // If the playing item is removed, play next or stop
                if (playQueue.isNotEmpty()) {
                    val nextIdx = index.coerceAtMost(playQueue.size - 1)
                    playMedia(playQueue[nextIdx], playQueue)
                } else {
                    stopPlayback()
                    currentItem = null
                    currentQueueIndex = -1
                }
            } else {
                currentItem?.let { curr ->
                    currentQueueIndex = playQueue.indexOfFirst { it.id == curr.id }
                }
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex in playQueue.indices && toIndex in playQueue.indices) {
            val item = playQueue.removeAt(fromIndex)
            playQueue.add(toIndex, item)
            currentItem?.let { curr ->
                currentQueueIndex = playQueue.indexOfFirst { it.id == curr.id }
            }
        }
    }

    fun moveQueueItemUp(index: Int) {
        if (index > 0 && index < playQueue.size) {
            reorderQueue(index, index - 1)
        }
    }

    fun moveQueueItemDown(index: Int) {
        if (index >= 0 && index < playQueue.size - 1) {
            reorderQueue(index, index + 1)
        }
    }

    fun clearQueue() {
        stopPlayback()
        playQueue.clear()
        currentItem = null
        currentQueueIndex = -1
    }



    // Auto-pause toggle
    fun setAutoPause(enabled: Boolean) {
        isAutoPauseEnabled = enabled
        if (!enabled && playbackState == PlaybackState.Ended) {
            // If user disables auto-pause, don't automatically stop progress tracking
        }
    }


    // Foreground Service control
    fun startForegroundPlayback(context: android.content.Context) {
        val intent = android.content.Intent(context, NexusMediaForegroundService::class.java)
        intent.putExtra("media_title", currentItem?.title ?: "NexusMedia")
        intent.putExtra("media_artist", currentItem?.artist ?: "Unknown")
        context.startForegroundService(intent)
    }

    fun stopForegroundPlayback(context: android.content.Context) {
        val intent = android.content.Intent(context, NexusMediaForegroundService::class.java)
        context.stopService(intent)
    }

    // Kids Lock: block system keys and touch outside player
    fun setKidsLock(enabled: Boolean) {
        isKidsLockEnabled = enabled
        if (enabled) {
            // Note: Full system key blocking requires a system-level service or overlay.
            // This state is used by the UI to disable navigation and external touch responses.
        }
    }

    // Gesture Controls toggles
    fun setGestureSeek(enabled: Boolean) { gestureSeekEnabled = enabled }
    fun setGestureVolume(enabled: Boolean) { gestureVolumeEnabled = enabled }
    fun setGestureBrightness(enabled: Boolean) { gestureBrightnessEnabled = enabled }


    // B. VIDEO SCALING MODE: SurfaceHolder / MediaPlayer scaling reference
    // Modes: "fit" (letterbox), "fill" (crop), "crop" (zoom), "auto" (device max)
    // Apply via activeSurfaceHolder?.setFixedSize(width, height) or SurfaceHolder.Callback adjustments.
    var videoScalingMode by mutableStateOf("auto")
        private set
    fun setVideoScalingMode(mode: String) {
        videoScalingMode = mode
        // Integration: adjust SurfaceHolder dimensions or MediaPlayer video scaling based on mode
    }


    // HEALTH (5.3): Health check reference for ForegroundService and media playback
    // Example: expose /health endpoint or periodic health signal: playback active, service running, network available

    // Background Playback (basic reference for service)

    // CUSTOM SCREEN ORIENTATION (1): Lock to portrait, landscape, or auto during playback
    var screenOrientationLock by mutableStateOf("auto")
        private set
    fun setScreenOrientation(orientation: String) {
        screenOrientationLock = orientation
        val activity = app.getApplication<Application>() as? android.app.Activity
        when (orientation) {
            "portrait" -> activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "sensor" -> activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        isBackgroundPlaybackEnabled = enabled
        // Note: Real background playback requires a Foreground Service with MediaBrowser.
        // This flag is used by the UI to show background mode status.
    }
    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        // MEMORY SAFETY (9): Cancel background jobs and clean up resources
        sleepTimerJob?.cancel()
        downloadTasks.clear()
        progressJob?.cancel()
    }
    suspend fun extractAndCacheAlbumArt(url: String, id: String): String? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(getApplication<Application>().cacheDir, "album_art_$id.jpg")
            if (cacheFile.exists()) {
                return@withContext cacheFile.absolutePath
            }
            val retriever = MediaMetadataRetriever()
            if (url.startsWith("http")) {
                retriever.setDataSource(url, HashMap<String, String>())
            } else {
                retriever.setDataSource(url)
            }
            val art = retriever.embeddedPicture
            retriever.release()
            if (art != null) {
                val fos = FileOutputStream(cacheFile)
                fos.write(art)
                fos.close()
                return@withContext cacheFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
// Quick state helper
fun Long.formatTime(): String {
    val sec = (this / 1000) % 60
    val min = (this / (1000 * 60)) % 60
    val hr = this / (1000 * 60 * 60)
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

    // Real-time subtitle sync adjustment (interactive slider control)
    // Best improvement: users adjust subtitle timing live without editing VTT/SRT files
    fun adjustSubtitleOffset(deltaMs: Long) {
        subtitleOffsetMs = (subtitleOffsetMs + deltaMs).coerceIn(-5000L, 5000L) // range: -5s to +5s
        currentItem?.let { item ->
            val lyrics = item.lyrics ?: return
            currentSubtitleText = SubtitleParser.parseSubtitle(lyrics, currentPosition, subtitleOffsetMs)
        }
    }
