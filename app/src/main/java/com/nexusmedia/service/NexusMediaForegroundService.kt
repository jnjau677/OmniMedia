package com.nexusmedia.service

/**
 * Foreground Service for NexusMedia background playback.
 * Keeps audio/video playback active when app minimizes or device locks.
 *
 * Integration:
 * - Manifest: <service android:name=".service.NexusMediaForegroundService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
 * - Activity: startForegroundService(Intent(context, NexusMediaForegroundService::class.java))
 * - Pass playback info via Intent extras and manage with MediaSession.
 */
class NexusMediaForegroundService : android.app.Service() {

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Notification channel setup (required for Android 8+)
        val channelId = "nexusmedia_channel"
        val channel = android.app.NotificationChannel(
            channelId,
            "NexusMedia Playback",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)

        val notification = android.app.Notification.Builder(this, channelId)
            .setContentTitle("NexusMedia")
            .setContentText("Background playback active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }


    // FULL SERVICE SYNC (4): Notification actions send commands back to player
    // Play action: send play command to MediaPlayer via broadcast or service binder
    // Skip action: send skip/next command
    // The RemoteAction in PipUtils.kt connects to this service; full two-way sync requires binder communication
    override fun onDestroy() {
        super.onDestroy()
        // Clean up any active playback or release resources
    }
}
