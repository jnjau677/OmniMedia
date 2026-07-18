package com.nexusmedia.ui.screens

import android.app.Activity
import android.net.Uri

/**
 * Storage Access Framework (SAF) file picker reference for NexusMedia.
 * Allows users to browse and select local video/audio files for playback.
 *
 * Usage in Activity/Composable:
 *   val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
 *       uri?.let { selectedUri -> loadMediaFromUri(selectedUri) }
 *   }
 *   launcher.launch(arrayOf("video/*", "audio/*"))
 *
 * Permissions required (already in AndroidManifest):
 *   READ_EXTERNAL_STORAGE, READ_MEDIA_AUDIO, READ_MEDIA_VIDEO
 */
object SAFFilePicker {

    fun supportedMimeTypes(): Array<String> {
        return arrayOf("video/*", "audio/*", "application/*")
    }

    fun isVideoUri(uri: Uri): Boolean {
        val mime = uri.toString().lowercase()
        return mime.endsWith(".mp4") || mime.endsWith(".mkv") || mime.endsWith(".avi") ||
               mime.endsWith(".mov") || mime.endsWith(".3gp") || mime.endsWith(".flv") ||
               mime.endsWith(".wmv") || mime.endsWith(".rmvb") || mime.endsWith(".ts") ||
               mime.endsWith(".mpg") || mime.endsWith(".m4v") || mime.endsWith(".f4v") ||
               mime.endsWith(".webm") || mime.endsWith(".mkv")
    }

    fun isAudioUri(uri: Uri): Boolean {
        val mime = uri.toString().lowercase()
        return mime.endsWith(".mp3") || mime.endsWith(".aac") || mime.endsWith(".flac") ||
               mime.endsWith(".wav") || mime.endsWith(".m4a") || mime.endsWith(".ogg")
    }
}
