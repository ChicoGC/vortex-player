package com.example.vortex_player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession

/**
 * Foreground service that shows the "now playing" notification (cover, title, previous/play/next)
 * and keeps the app alive while music plays in the background. The player itself stays in
 * MainActivity; the notification buttons are forwarded to it through [commands].
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action -> commands?.invoke(action) }
        val notification = current
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "vortex.PLAY_PAUSE"
        const val ACTION_NEXT = "vortex.NEXT"
        const val ACTION_PREVIOUS = "vortex.PREVIOUS"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback"

        /** Set by MainActivity to receive the notification buttons. */
        var commands: ((String) -> Unit)? = null

        private var current: Notification? = null
        private var running = false

        fun show(context: Context, notification: Notification) {
            current = notification
            if (running) {
                context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            } else {
                // Android can refuse to start it while the app is in the background; music keeps playing anyway.
                runCatching { context.startForegroundService(Intent(context, PlaybackService::class.java)) }
            }
        }

        fun hide(context: Context) {
            current = null
            context.stopService(Intent(context, PlaybackService::class.java))
        }

        fun build(
            context: Context,
            session: MediaSession.Token,
            song: Song,
            art: Bitmap?,
            playing: Boolean,
        ): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setLargeIcon(art)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .addAction(action(context, R.drawable.ic_previous, "Anterior", ACTION_PREVIOUS))
                .addAction(
                    if (playing) action(context, R.drawable.ic_pause, "Pausar", ACTION_PLAY_PAUSE)
                    else action(context, R.drawable.ic_play, "Tocar", ACTION_PLAY_PAUSE)
                )
                .addAction(action(context, R.drawable.ic_next, "Próxima", ACTION_NEXT))
                .setStyle(Notification.MediaStyle().setMediaSession(session).setShowActionsInCompactView(0, 1, 2))
                .build()
        }

        private fun action(context: Context, icon: Int, title: String, command: String): Notification.Action {
            val intent = PendingIntent.getService(
                context, command.hashCode(),
                Intent(context, PlaybackService::class.java).setAction(command),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent).build()
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reprodução", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Música tocando agora, com os controles"
                    setShowBadge(false)
                }
            )
        }
    }
}
