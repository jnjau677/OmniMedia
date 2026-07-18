package com.nexusmedia.data

import kotlinx.coroutines.flow.Flow
// SUPPORTED FORMATS REFERENCE:
// Video: MP4, MKV, AVI, MOV, 3GP, FLV, F4V, WEBM, WMV, RMVB, TS, MPG, M4V
// Subtitles: VTT, SRT, SUB, SSA, SMI, MPL, PJS, TXT, LRC, AAS

import kotlinx.coroutines.flow.firstOrNull
import com.nexusmedia.network.MuxVideoService
import com.nexusmedia.data.VideoScanner

class MediaRepository(private val mediaDao: MediaDao) {

    val allMediaItems: Flow<List<MediaItemEntity>> = mediaDao.getAllMediaItems()
    val allPlaylists: Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylists()
    val playbackHistory: Flow<List<PlaybackHistoryEntity>> = mediaDao.getPlaybackHistory()
    val appSettingsFlow: Flow<AppSettingsEntity?> = mediaDao.getAppSettingsFlow()

    suspend fun getMediaItemById(id: String): MediaItemEntity? = mediaDao.getMediaItemById(id)

    suspend fun insertMediaItems(items: List<MediaItemEntity>) = mediaDao.insertMediaItems(items)

    suspend fun insertMediaItem(item: MediaItemEntity) = mediaDao.insertMediaItem(item)

    suspend fun updateMediaItem(item: MediaItemEntity) = mediaDao.updateMediaItem(item)

    suspend fun deleteMediaItem(item: MediaItemEntity) = mediaDao.deleteMediaItem(item)

    suspend fun insertPlaylist(playlist: PlaylistEntity): Long = mediaDao.insertPlaylist(playlist)

    suspend fun updatePlaylist(playlist: PlaylistEntity) = mediaDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(playlist: PlaylistEntity) = mediaDao.deletePlaylist(playlist)

    suspend fun getPlaylistById(id: Long): PlaylistEntity? = mediaDao.getPlaylistById(id)

    fun getMediaItemsInPlaylist(playlistId: Long): Flow<List<MediaItemEntity>> =
        mediaDao.getMediaItemsInPlaylist(playlistId)

    suspend fun reorderPlaylistItems(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val items = mediaDao.getPlaylistItems(playlistId).toMutableList()
        if (fromIndex in items.indices && toIndex in items.indices) {
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
            // Update orderIndex for all items
            val updatedItems = items.mapIndexed { index, entity ->
                entity.copy(orderIndex = index)
            }
            mediaDao.updatePlaylistItems(updatedItems)
        }
    }

    suspend fun insertPlaylistItem(playlistId: Long, mediaItemId: String, orderIndex: Int) {
        mediaDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaItemId = mediaItemId,
                orderIndex = orderIndex
            )
        )
    }

    suspend fun removePlaylistItem(playlistId: Long, mediaItemId: String) =
        mediaDao.removePlaylistItem(playlistId, mediaItemId)

    suspend fun clearPlaylist(playlistId: Long) = mediaDao.clearPlaylist(playlistId)

    suspend fun getHistoryItem(mediaItemId: String): PlaybackHistoryEntity? =
        mediaDao.getHistoryItem(mediaItemId)

    suspend fun insertHistoryItem(history: PlaybackHistoryEntity) =
        mediaDao.insertHistoryItem(history)

    suspend fun deleteHistoryItem(mediaItemId: String) =
        mediaDao.deleteHistoryItem(mediaItemId)

    suspend fun getAppSettings(): AppSettingsEntity {
        return mediaDao.getAppSettings() ?: AppSettingsEntity().also {
            mediaDao.saveAppSettings(it)
        }
    }

    suspend fun saveAppSettings(settings: AppSettingsEntity) =
        mediaDao.saveAppSettings(settings)

    // Pre-populate Database with local demo content (local-first)
    suspend fun prepopulateDemoContent() {
        val currentItems = allMediaItems.firstOrNull()
        if (currentItems.isNullOrEmpty()) {
            val demoItems = listOf(
                MediaItemEntity(
                    id = "local_video_01",
                    title = "Sintel Movie Clip (Local)",
                    artist = "Durian Open Movie Project",
                    album = "Local Drive",
                    duration = 52000, // 0:52
                    url = "/storage/emulated/0/Movies/Sintel.mp4",
                    isVideo = true,
                    isLocal = true,
                    isDownloaded = true,
                    thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&auto=format&fit=crop",
                    genre = "Fantasy"
                ),
                MediaItemEntity(
                    id = "video_synth_01",
                    title = "Cosmic Synthesizer Wave",
                    artist = "Stellar AudioLabs",
                    album = "Galaxy Journeys",
                    duration = 180000, // 3:00
                    url = "/storage/emulated/0/Movies/BigBuckBunny.mp4",
                    isVideo = true,
                    isLocal = true,
                    isDownloaded = false,
                    thumbnailUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&auto=format&fit=crop",
                    genre = "Synthwave",
                    lyrics = "[00:00] (Cosmic ambient intro)\n[00:15] Synthesizer begins to pulsate...\n[00:30] Floating in deep vacuum void...\n[01:00] Beautiful synthesizer filter sweeps...\n[01:30] Entering the asteroid belt...\n[02:00] Climax of the solar winds...\n[02:40] Fading back into celestial silence."
                ),
                MediaItemEntity(
                    id = "video_nature_02",
                    title = "Deep Wilderness Stream",
                    artist = "Ambient Earth",
                    album = "Nature Escapes",
                    duration = 150000, // 2:30
                    url = "/storage/emulated/0/Movies/ElephantsDream.mp4",
                    isVideo = true,
                    isLocal = true,
                    isDownloaded = false,
                    thumbnailUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=500&auto=format&fit=crop",
                    genre = "Nature",
                    lyrics = "[00:00] (Sounds of rushing water)\n[00:20] Birds chirping in a dense fir forest\n[00:50] Rustling of leaves under the gentle summer breeze\n[01:20] distant mountain horn echoing softly"
                ),
                MediaItemEntity(
                    id = "audio_lofi_03",
                    title = "Late Night Code Beats",
                    artist = "Retro Coding Collective",
                    album = "Syntax & Coffee",
                    duration = 240000, // 4:00
                    url = "/storage/emulated/0/Music/SoundHelix-Song-1.mp3",
                    isVideo = false,
                    isLocal = true,
                    isDownloaded = false,
                    thumbnailUrl = "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=500&auto=format&fit=crop",
                    genre = "Lofi",
                    lyrics = "[00:00] (Static vinyl crackle)\n[00:10] Smooth Rhodes chord progression\n[00:30] Lo-fi drum break drops in\n[01:00] Code compiled. Tests are green.\n[01:30] A gentle saxophone melody carries the mind\n[02:30] Deep focus loop repeat\n[03:40] Rhodes fades back into vinyl crackles"
                ),
                MediaItemEntity(
                    id = "audio_synth_04",
                    title = "Cyberpunk Highway",
                    artist = "Neon Driver",
                    album = "Grid Runner",
                    duration = 195000, // 3:15
                    url = "/storage/emulated/0/Music/SoundHelix-Song-2.mp3",
                    isVideo = false,
                    isLocal = true,
                    isDownloaded = false,
                    thumbnailUrl = "https://images.unsplash.com/photo-1515462277126-270d878326e5?w=500&auto=format&fit=crop",
                    genre = "Electronic",
                    lyrics = "[00:00] Ready...\n[00:05] Set...\n[00:07] GO!\n[00:15] Speeding at 120 MPH on neon streets\n[00:45] Retro lasers firing all around\n[01:15] We are running the grid tonight\n[02:00] Bassline intensive solo\n[02:45] The lights are growing distant..."
                ),
                MediaItemEntity(
                    id = "audio_acoustic_05",
                    title = "Sunrise Acoustic Guitar",
                    artist = "Elena Woods",
                    album = "Woodland Mornings",
                    duration = 210000, // 3:30
                    url = "/storage/emulated/0/Music/SoundHelix-Song-3.mp3",
                    isVideo = false,
                    isLocal = true,
                    isDownloaded = false,
                    thumbnailUrl = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=500&auto=format&fit=crop",
                    genre = "Acoustic",
                    lyrics = "[00:00] (Soft acoustic guitar picking)\n[00:20] Sunrise over the misty valley\n[00:50] Birds rise into the golden warmth\n[01:30] The wood-scented cabin awakens\n[02:30] Flute accompaniment swells in harmony\n[03:15] Fading guitar strums to peace"
                )
            )
            mediaDao.insertMediaItems(demoItems)
        }
        // Only create playlists if none exist, to avoid duplicates
        val existingPlaylists = allPlaylists.firstOrNull()
        if (existingPlaylists.isNullOrEmpty()) {
            val favPlaylistId = mediaDao.insertPlaylist(
                PlaylistEntity(
                    name = "My Favorites",
                    description = "Media items you love and marked as favorite",
                    isSmart = true,
                    smartType = "favorites"
                )
            )
            val codePlaylistId = mediaDao.insertPlaylist(
                PlaylistEntity(
                    name = "Deep Coding Focus",
                    description = "Music for maximum productivity"
                )
            )
            mediaDao.insertPlaylistItem(
                PlaylistItemEntity(playlistId = codePlaylistId, mediaItemId = "audio_lofi_03", orderIndex = 0)
            )
            mediaDao.insertPlaylistItem(
                PlaylistItemEntity(playlistId = codePlaylistId, mediaItemId = "audio_synth_04", orderIndex = 1)
            )
        }
    }

    // Reference: scan local video files using SAF / MediaStore
    suspend fun scanLocalVideos(context: android.content.Context): List<MediaItemEntity> {
        return VideoScanner.scanLocalVideos(context)
    }

    // MUX REFERENCE: Generate Mux playback URL for local/streamed media
    // If using Mux encoding, the playback URL replaces item.url in the UI/player.
    suspend fun generateMuxPlaybackUrl(playbackId: String): String {
        return MuxVideoService.playbackUrl(playbackId)
    }
}
