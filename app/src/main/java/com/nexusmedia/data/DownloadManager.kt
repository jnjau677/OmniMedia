package com.nexusmedia.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Real download logic using Android DownloadManager.
 * Downloads media files to Downloads/NexusMedia/ with persistent notifications.
 */
object DownloadManager {

    fun downloadMedia(context: Context, item: MediaItemEntity): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(item.url))
            .setTitle(item.title)
            .setDescription("Downloading: ${item.title} by ${item.artist}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexusMedia/${item.title}.${if (item.isVideo) "mp4" else "mp3"}")
            .setMimeType(if (item.isVideo) "video/mp4" else "audio/mpeg")
        return downloadManager.enqueue(request)
    }

    fun queryDownloadStatus(context: Context, downloadId: Long): String {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        return if (cursor.moveToFirst()) {
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = if (statusCol >= 0) cursor.getInt(statusCol) else -1
            when (status) {
                DownloadManager.STATUS_PENDING -> "Pending"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                else -> "Unknown"
            }
        } else {
            "Unknown"
        }.also { cursor.close() }
    }
}
