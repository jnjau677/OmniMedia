package com.nexusmedia.data

// SEARCH FTS INDEXING (3): Room Full-Text Search reference
// To enable: @Fts4(languageId = "en-us") on media_items entity
// Query: @Query("SELECT * FROM media_items WHERE title MATCH :query")
// Note: Requires Room FTS dependency and schema migration.


import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // Media Items
    @Query("SELECT * FROM media_items")
    fun getAllMediaItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaItemById(id: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity)

    @Update
    suspend fun updateMediaItem(item: MediaItemEntity)

    @Delete
    suspend fun deleteMediaItem(item: MediaItemEntity)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    // Playlist Items
    @Query("SELECT m.* FROM media_items m INNER JOIN playlist_items p ON m.id = p.mediaItemId WHERE p.playlistId = :playlistId ORDER BY p.orderIndex ASC")
    fun getMediaItemsInPlaylist(playlistId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemEntity>

    @Update
    suspend fun updatePlaylistItems(items: List<PlaylistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaItemId = :mediaItemId")
    suspend fun removePlaylistItem(playlistId: Long, mediaItemId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    // Playback History
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedTimestamp DESC")
    fun getPlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaItemId = :mediaItemId")
    suspend fun getHistoryItem(mediaItemId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaItemId = :mediaItemId")
    suspend fun deleteHistoryItem(mediaItemId: String)

    // App Settings (Single Row, id = 1)
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getAppSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(settings: AppSettingsEntity)
}
