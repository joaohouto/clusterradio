package com.joaohouto.clusterradio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS
import androidx.media3.common.Player.COMMAND_STOP
import androidx.media3.common.Player.Commands
import androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaohouto.clusterradio.MainActivity
import com.joaohouto.clusterradio.R
import com.joaohouto.clusterradio.engine.CompositeRadioManager
import com.joaohouto.clusterradio.engine.RadioPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RadioPlaybackService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "cluster_radio_playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.joaohouto.clusterradio.action.START"
        const val ACTION_TOGGLE_PLAY = "com.joaohouto.clusterradio.action.TOGGLE_PLAY"
        const val ACTION_PREVIOUS = "com.joaohouto.clusterradio.action.PREVIOUS"
        const val ACTION_NEXT = "com.joaohouto.clusterradio.action.NEXT"
        const val ACTION_STOP = "com.joaohouto.clusterradio.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var mediaSession: MediaSession? = null
    private lateinit var radioPlayer: RadioPlayer
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val radioManager = CompositeRadioManager.getInstance(this)
        radioPlayer = RadioPlayer(Looper.getMainLooper(), radioManager)

        val activityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, radioPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Satisfy Android 8+ foreground service startup within 5 seconds
        val initialNotification = buildNotification(radioManager.playbackState.value)
        startForeground(NOTIFICATION_ID, initialNotification)
        isForeground = true

        serviceScope.launch {
            radioManager.playbackState.collect { state ->
                radioPlayer.refreshState()
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val radioManager = CompositeRadioManager.getInstance(this)
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> {
                radioManager.togglePlayPause()
            }
            ACTION_PREVIOUS -> {
                radioManager.step(stepUp = false)
            }
            ACTION_NEXT -> {
                radioManager.step(stepUp = true)
            }
            ACTION_STOP -> {
                radioManager.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                updateNotification(radioManager.playbackState.value)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification(state: RadioPlaybackState) {
        val notification = buildNotification(state)
        if (state.isPlaying) {
            if (!isForeground) {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            // When paused, detach from foreground but keep notification in drawer with play action
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: RadioPlaybackState): Notification {
        val session = mediaSession

        val activityIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "${state.currentBand.displayName} ${state.displayFrequency} ${state.unitLabel}"
        val text = when {
            !state.stationName.isNullOrBlank() && !state.radioText.isNullOrBlank() -> "${state.stationName} • ${state.radioText}"
            !state.stationName.isNullOrBlank() -> state.stationName
            !state.radioText.isNullOrBlank() -> state.radioText
            else -> "${state.currentBand.displayName} STEREO"
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(getString(R.string.app_name))
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .addAction(
                R.drawable.ic_notification_prev,
                getString(R.string.desc_previous_station),
                prevPendingIntent
            )
            .addAction(
                if (state.isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                if (state.isPlaying) getString(R.string.desc_pause) else getString(R.string.desc_play),
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_notification_next,
                getString(R.string.desc_next_station),
                nextPendingIntent
            )
            .addAction(
                R.drawable.ic_notification_close,
                getString(R.string.btn_close),
                stopPendingIntent
            )

        if (session != null) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPendingIntent)
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Custom lightweight Media3 Player bridging MediaSession directly to CompositeRadioManager
     * without DefaultMediaSourceFactory, preventing any NullPointerExceptions.
     */
    private class RadioPlayer(
        looper: Looper,
        private val radioManager: CompositeRadioManager
    ) : SimpleBasePlayer(looper) {

        fun refreshState() {
            invalidateState()
        }

        override fun getState(): State {
            val playbackState = radioManager.playbackState.value
            val title = "${playbackState.currentBand.displayName} ${playbackState.displayFrequency} ${playbackState.unitLabel}"
            val subtitle = playbackState.stationName
                ?: if (!playbackState.radioText.isNullOrBlank()) playbackState.radioText
                else "${playbackState.currentBand.displayName} STEREO"

            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId("cluster_radio")
                .setMediaMetadata(metadata)
                .build()

            val itemData = MediaItemData.Builder("cluster_radio")
                .setMediaItem(mediaItem)
                .build()

            return State.Builder()
                .setAvailableCommands(
                    Commands.Builder().addAll(
                        COMMAND_PLAY_PAUSE,
                        COMMAND_SEEK_TO_NEXT,
                        COMMAND_SEEK_TO_PREVIOUS,
                        COMMAND_STOP
                    ).build()
                )
                .setPlayWhenReady(playbackState.isPlaying, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(if (playbackState.isPlaying) STATE_READY else STATE_IDLE)
                .setPlaylist(listOf(itemData))
                .setPlaylistMetadata(metadata)
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) {
                radioManager.play()
            } else {
                radioManager.pause()
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int
        ): ListenableFuture<*> {
            if (seekCommand == COMMAND_SEEK_TO_NEXT) {
                radioManager.step(stepUp = true)
            } else if (seekCommand == COMMAND_SEEK_TO_PREVIOUS) {
                radioManager.step(stepUp = false)
            }
            return Futures.immediateVoidFuture()
        }
    }
}
