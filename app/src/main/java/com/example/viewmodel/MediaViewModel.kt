package com.example.viewmodel

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
import com.example.data.*
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

    // Sleep Timer state
    var sleepTimerRemainingSec by mutableStateOf(0L)
        private set
    private var sleepTimerJob: Job? = null

    // Equalizer sliders state (dB values)
    var eqBass by mutableStateOf(50f)
    var eqVocal by mutableStateOf(50f)
    var eqTreble by mutableStateOf(50f)

    // Lyrics/Subtitles Parsing
    var currentSubtitleText by mutableStateOf("")
        private set

    // Downloads
    var downloadTasks = mutableStateListOf<DownloadTask>()
        private set

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
                delay(200)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
    }

    // Subtitle lyrics matching
    private fun updateSubtitleText(positionMs: Long) {
        val item = currentItem ?: return
        val lyrics = item.lyrics ?: return
        
        var matchedText = ""

        if (lyrics.trim().startsWith("WEBVTT")) {
            // VTT Parser
            val blocks = lyrics.trim().split(Regex("\\n\\s*\\n"))
            for (block in blocks) {
                val lines = block.split("\n")
                var timeLineIndex = -1
                for (i in lines.indices) {
                    if (lines[i].contains("-->")) {
                        timeLineIndex = i
                        break
                    }
                }
                if (timeLineIndex != -1) {
                    val timeStr = lines[timeLineIndex]
                    val parts = timeStr.split("-->").map { it.trim() }
                    if (parts.size == 2) {
                        val startMs = parseVttTime(parts[0])
                        val endMs = parseVttTime(parts[1])
                        if (positionMs in startMs..endMs) {
                            matchedText = lines.subList(timeLineIndex + 1, lines.size).joinToString("\n")
                            break
                        }
                    }
                }
            }
        } else {
            // LRC Parser
            val lines = lyrics.split("\n")
            var bestTime = -1L
            for (line in lines) {
                if (line.startsWith("[") && line.contains("]")) {
                    val closeBracketIndex = line.indexOf("]")
                    val timeStr = line.substring(1, closeBracketIndex) // "MM:SS" or "MM:SS.xx"
                    val text = line.substring(closeBracketIndex + 1).trim()
                    
                    // Parse MM:SS
                    val timeParts = timeStr.split(":")
                    if (timeParts.size >= 2) {
                        val min = timeParts[0].toLongOrNull() ?: 0L
                        val sec = timeParts[1].substringBefore(".").toLongOrNull() ?: 0L
                        val timeMs = (min * 60 + sec) * 1000
                        
                        if (timeMs <= positionMs && timeMs > bestTime) {
                            bestTime = timeMs
                            matchedText = text
                        }
                    }
                }
            }
        }

        currentSubtitleText = matchedText
    }

    private fun parseVttTime(timeStr: String): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val sParts = parts[2].split(".")
                val s = sParts[0].toLong()
                val ms = if (sParts.size > 1) sParts[1].toLong() else 0L
                return (h * 3600 + m * 60 + s) * 1000 + ms
            } else if (parts.size == 2) {
                val m = parts[0].toLong()
                val sParts = parts[1].split(".")
                val s = sParts[0].toLong()
                val ms = if (sParts.size > 1) sParts[1].toLong() else 0L
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
            val mediaItems = mutableListOf<com.example.data.MediaItemEntity>()
            for (item in items) {
                val media = repository.getMediaItemById(item.mediaItemId)
                if (media != null) {
                    mediaItems.add(media)
                }
            }
            
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val m3uFile = java.io.File(downloadsDir, "${playlist.name}.m3u")
                m3uFile.printWriter().use { out ->
                    out.println("#EXTM3U")
                    mediaItems.forEach { media ->
                        out.println("#EXTINF:${media.duration / 1000},${media.artist} - ${media.title}")
                        out.println(media.url)
                    }
                }
                android.widget.Toast.makeText(getApplication(), "Exported to Downloads: ${m3uFile.name}", android.widget.Toast.LENGTH_LONG).show()
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
        
        // Save to Room AppSettings
        viewModelScope.launch {
            val s = repository.getAppSettings()
            repository.saveAppSettings(s.copy(equalizorPreset = preset))
        }
    }

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
            val updated = item.copy(isDownloaded = true, localFilePath = "/downloads/${item.title}.mp4")
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

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
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

