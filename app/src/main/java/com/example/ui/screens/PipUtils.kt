package com.example.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import android.app.PictureInPictureParams

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
        val params = PictureInPictureParams.Builder().build()
        activity?.enterPictureInPictureMode(params)
    }
}
