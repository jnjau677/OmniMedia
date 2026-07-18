package com.nexusmedia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val url: String, // Streaming URL or Local file path
    val isVideo: Boolean,
    val isLocal: Boolean,
    val isDownloaded: Boolean,
    val localFilePath: String? = null,
    val thumbnailUrl: String,
    val genre: String,
    val lyrics: String? = null,
    val resolution: String = "2160p",
    val bitRate: String = "320kbps"
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val isSmart: Boolean = false,
    val smartType: String? = null, // e.g., "recently_played", "favorites"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaItemId: String,
    val orderIndex: Int
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaItemId: String,
    val lastPosition: Long, // in milliseconds
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    val isFavorite: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1, // Single row configuration
    val themeMode: String = "system", // "light", "dark", "system"
    val subtitleLanguage: String = "English",
    val defaultStreamingQuality: String = "High (1080p)",
    val defaultDownloadQuality: String = "High (1080p)",
    val isClosedCaptionsEnabled: Boolean = true,
    val isHighContrastEnabled: Boolean = false,
    val textSizeMultiplier: Float = 1.0f,
    val isCrossfadeEnabled: Boolean = false,
    val crossfadeDurationSec: Int = 3,
    val isGaplessPlaybackEnabled: Boolean = true,
    val equalizorPreset: String = "Normal" // "Normal", "Bass Boost", "Vocal Boost", "Classical", "Dance", "Pop", "Rock"
)
