package com.nexusmedia.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent

@Composable
fun rememberIsInPipMode(): Boolean {
    val activity = LocalContext.current as? ComponentActivity
    var pipMode by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            pipMode = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
    return pipMode
}

fun enterPipMode(activity: Activity?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val playAction = RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity?.applicationContext ?: activity?.applicationContext ?: LocalContext.current, android.R.drawable.ic_media_play),
            "Play", "Play media",
            PendingIntent.getService(
                activity?.applicationContext ?: activity?.applicationContext ?: LocalContext.current,
                0,
                Intent(activity?.applicationContext ?: activity?.applicationContext ?: LocalContext.current, com.nexusmedia.service.NexusMediaForegroundService::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(listOf(playAction))
            .build()
        activity?.enterPictureInPictureMode(params)
    }
}
