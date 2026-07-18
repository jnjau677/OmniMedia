package com.nexusmedia.data

import android.content.Context
import android.provider.MediaStore

/**
 * Reference implementation for scanning local video files.
 * Produces a list of media paths, durations, and thumbnails for NexusMedia.
 */
object VideoScanner {

    fun scanLocalVideos(context: Context): List<MediaItemEntity> {
        val results = mutableListOf<MediaItemEntity>()
        // Real implementation uses MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        // with projection: DATA, TITLE, DURATION, DISPLAY_NAME
        // This is a reference stub; the current demo uses prepopulated local paths.
        return results
    }

    /** LOCAL THUMBNAILS (4): Extract thumbnail from local video file using MediaMetadataRetriever */
    fun extractLocalThumbnail(context: android.content.Context, videoPath: String): String? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val art = retriever.embeddedPicture ?: retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            art?.let { byteArray ->
                val fileName = videoPath.substringAfterLast("/") + ".thumb.jpg"
                val thumbFile = java.io.File(context.cacheDir, fileName)
                java.io.FileOutputStream(thumbFile).use { it.write(if (art != null && art.size > 0) art else byteArrayOf()) }
                thumbFile.absolutePath
            } ?: null
        } catch (e: Exception) { null }
    }
}
