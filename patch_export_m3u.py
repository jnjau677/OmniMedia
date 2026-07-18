with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "r") as f:
    text = f.read()

import re

export_func = """    fun deletePlaylist(playlist: PlaylistEntity) {
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
    }"""

text = text.replace("""    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }""", export_func)

with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "w") as f:
    f.write(text)
