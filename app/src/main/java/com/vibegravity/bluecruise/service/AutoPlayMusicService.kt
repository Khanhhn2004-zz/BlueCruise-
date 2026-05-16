package com.vibegravity.bluecruise.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import com.vibegravity.bluecruise.R
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AutoPlayMusicService : MediaSessionService() {

    @Inject
    lateinit var playbackOrchestrator: PlaybackOrchestrator

    @Inject
    lateinit var playbackSessionStore: PlaybackSessionStore

    @Inject
    lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var routingSessionCompat: MediaSessionCompat? = null
    private var isStoppingService = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackCommandVersion = 0L

    private lateinit var audioManager: AudioManager
    private var audioFocusRequested = false
    private var focusRecoveryJob: Job? = null
    private val mediaSessionId by lazy { "BlueCruiseMediaSession-${System.identityHashCode(this)}" }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Timber.d("AudioFocus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("AudioFocus LOSS - pausing playback & scheduling recovery")
                player?.pause()
                scheduleFocusRecovery()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.d("AudioFocus LOSS_TRANSIENT - pausing & scheduling recovery")
                player?.pause()
                scheduleFocusRecovery()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("AudioFocus LOSS_TRANSIENT_CAN_DUCK - ducking volume & scheduling recovery")
                player?.volume = 0.3f
                scheduleFocusRecovery()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("AudioFocus GAIN - resuming playback")
                focusRecoveryJob?.cancel()
                player?.volume = 1.0f
                if (player?.isPlaying == false && !isStoppingService) {
                    player?.play()
                }
            }
        }
    }

    companion object {
        const val ACTION_START_PLAYBACK = "com.vibegravity.bluecruise.action.START_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.vibegravity.bluecruise.action.STOP_PLAYBACK"
        const val EXTRA_AUDIO_SLOT = "com.vibegravity.bluecruise.extra.AUDIO_SLOT"
        const val EXTRA_AUDIO_URI_OVERRIDE = "com.vibegravity.bluecruise.extra.AUDIO_URI_OVERRIDE"
        const val AUDIO_SLOT_PRIMARY = 1
        const val AUDIO_SLOT_SECONDARY = 2
        private const val CHANNEL_ID = "bluecruise_playback"
        private const val NOTIFICATION_ID = 1001
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializeSessionAndPlayer()
    }

    private fun requestAudioFocus(): Boolean {
        if (audioFocusRequested) return true
        val result = audioManager.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        audioFocusRequested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Timber.d("AudioFocus request result: $result, granted: $audioFocusRequested")
        return audioFocusRequested
    }

    private fun abandonAudioFocus() {
        if (audioFocusRequested) {
            audioManager.abandonAudioFocus(audioFocusListener)
            audioFocusRequested = false
            Timber.d("AudioFocus abandoned")
        }
        focusRecoveryJob?.cancel()
        focusRecoveryJob = null
    }

    private fun scheduleFocusRecovery(delayMs: Long = 1500L) {
        if (isStoppingService) return
        focusRecoveryJob?.cancel()
        focusRecoveryJob = scope.launch {
            delay(delayMs)
            if (!isStoppingService && requestAudioFocus()) {
                Timber.d("Focus recovery granted, resuming playback")
                player?.volume = 1.0f
                player?.play()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ph�t nh?c BlueCruise",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeSessionAndPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playbackCompletionListener) }

        val sessionBuilder = MediaSession.Builder(this, player!!)
            .setId(mediaSessionId)
            .setCallback(mediaSessionCallback)
        buildSessionActivityPendingIntent()?.let { sessionBuilder.setSessionActivity(it) }
        mediaSession = sessionBuilder.build()

        // Tier 2 relies on compat playback-state transitions, so keep a compat session available.
        routingSessionCompat = MediaSessionCompat(this, "BlueCruiseRouting-$mediaSessionId").apply {
            setCallback(compatSessionCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
        restoreResumableSessionState()
    }

    private val playbackCompletionListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Timber.d("Playback completed naturally; cleaning up service")
                stopPlaybackAndService()
            }
        }
    }

    private val compatSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            handleCompatSessionPlayCommand()
        }

        override fun onPause() {
            handleStopPlaybackCommand()
        }

        override fun onStop() {
            handleStopPlaybackCommand()
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.d("MediaSession callback: onPlaybackResumption from controller: ${controller.packageName}")
            val snapshot = runBlocking { playbackSessionStore.snapshot() }
            if (!snapshot.resumeEligible) {
                return Futures.immediateFailedFuture(
                    IllegalStateException("Playback resumption disabled while auto-play is off")
                )
            }
            val replayUri = snapshot.audioUri?.takeIf { it.isNotBlank() }
                ?: return Futures.immediateFailedFuture(
                    IllegalStateException("No replayable item available for playback resumption")
                )
            val mediaItem = buildPlayableMediaItem(snapshot, replayUri)
            val mediaItems = listOf(mediaItem)
            val result = MediaSession.MediaItemsWithStartPosition(mediaItems, 0, 0L)
            return Futures.immediateFuture(result)
        }

        @Deprecated("Media3 callback still needed to intercept pause/play from system surfaces")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            return when (playerCommand) {
                Player.COMMAND_PLAY_PAUSE -> handlePlayPauseCommand(controller.packageName)
                Player.COMMAND_STOP -> {
                    pausePlaybackIntoResumableState()
                    SessionResult.RESULT_ERROR_NOT_SUPPORTED
                }
                else -> SessionResult.RESULT_SUCCESS
            }
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Timber.d("MediaSession onConnect from: ${controller.packageName}")
            // Allow connections but don't compete aggressively
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.d("onStartCommand: $action")
        
        when (action) {
            ACTION_START_PLAYBACK -> {
                val requestedSlot = intent.getIntExtra(EXTRA_AUDIO_SLOT, AUDIO_SLOT_PRIMARY)
                val audioUriOverride = intent.getStringExtra(EXTRA_AUDIO_URI_OVERRIDE)
                acceptStartPlaybackCommand(requestedSlot, audioUriOverride)
            }
            ACTION_STOP_PLAYBACK -> {
                handleStopPlaybackCommand()
            }
        }
        // START_STICKY ensures the service restarts if killed by system
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val snapshot = loadPlaybackSnapshot()
        updateCompatSessionState(snapshot, PlaybackStateCompat.STATE_PLAYING)
        val notification = buildPlaybackNotification(snapshot, isPlaying = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startPlayback() {
        startPlayback(AUDIO_SLOT_PRIMARY, null)
    }

    private fun startPlayback(audioSlot: Int, audioUriOverride: String?) {
        startPlayback(audioSlot, audioUriOverride, playbackCommandVersion)
    }

    private fun startPlayback(audioSlot: Int, audioUriOverride: String?, commandVersion: Long) {
        scope.launch {
            val currentPlayer = player ?: return@launch
            val routingSession = routingSessionCompat?.apply { isActive = true }
            val started = playbackOrchestrator.startPlayback(
                currentPlayer,
                routingSessionToken = routingSession,
                audioSlot = audioSlot,
                audioUriOverride = audioUriOverride
            )
            if (commandVersion != playbackCommandVersion) {
                Timber.d("Ignoring stale playback startup result for slot=%d", audioSlot)
                return@launch
            }
            if (!started) {
                Timber.w("Playback startup aborted; stopping service")
                playbackRuntimeStateStore.markIdle()
                stopPlaybackAndService()
            } else {
                playbackRuntimeStateStore.markPlaying(audioSlot)
                updateCompatSessionState(loadPlaybackSnapshot(), PlaybackStateCompat.STATE_PLAYING)
            }
        }
    }

    private fun stopPlayback(deactivateSession: Boolean = true) {
        if (deactivateSession) {
            routingSessionCompat?.isActive = false
        }
        player?.let { playbackOrchestrator.stopPlayback(it) }
    }

    private fun pausePlaybackIntoResumableState() {
        val snapshot = loadPlaybackSnapshot()
        stopPlayback(deactivateSession = false)
        playbackRuntimeStateStore.markIdle()
        updateCompatSessionState(snapshot, PlaybackStateCompat.STATE_PAUSED)
        showResumableNotification(snapshot)
        isStoppingService = false
    }

    private fun stopPlaybackAndService() {
        if (isStoppingService) return
        isStoppingService = true
        invalidatePlaybackCommandVersion()
        playbackRuntimeStateStore.markIdle()
        updateCompatSessionState(loadPlaybackSnapshot(), PlaybackStateCompat.STATE_STOPPED)
        stopPlayback()
        clearForegroundNotification()
        stopSelf()
    }

    private fun restoreResumableSessionState() {
        val snapshot = loadPlaybackSnapshot()
        if (!snapshot.resumeEligible || snapshot.audioUri.isNullOrBlank()) return
        updateCompatSessionState(snapshot, PlaybackStateCompat.STATE_PAUSED)
    }

    private fun loadPlaybackSnapshot(): PlaybackSessionSnapshot {
        if (!::playbackSessionStore.isInitialized) {
            return PlaybackSessionSnapshot(
                audioUri = null,
                displayTitle = null,
                targetMac = null,
                aaEnabledForTarget = false,
                resumeEligible = false,
                preparedAtElapsedMs = null
            )
        }

        return runCatching { runBlocking { playbackSessionStore.snapshot() } }
            .getOrElse {
                Timber.w(it, "Failed to load playback snapshot for notification/session state")
                PlaybackSessionSnapshot(
                    audioUri = null,
                    displayTitle = null,
                    targetMac = null,
                    aaEnabledForTarget = false,
                    resumeEligible = false,
                    preparedAtElapsedMs = null
                )
            }
    }

    private fun resolveDisplayTitle(snapshot: PlaybackSessionSnapshot): String {
        val explicitTitle = snapshot.displayTitle?.trim().orEmpty()
        if (explicitTitle.isNotEmpty()) return explicitTitle

        resolveSlotLabel(currentRuntimeSlotHint())?.let { return it }

        val uri = snapshot.audioUri.orEmpty()
        if (uri.isBlank()) return getString(R.string.app_name)

        return normalizeFallbackTitle(
            uri.substringAfterLast('/').substringAfterLast(':')
        )
    }

    private fun handlePlayPauseCommand(controllerPackageName: String?): Int {
        val currentPlayer = player ?: return SessionResult.RESULT_SUCCESS
        val runtimeState = playbackRuntimeStateStore.state.value
        return if (runtimeState.isPlaying || currentPlayer.isPlaying) {
            handleStopPlaybackCommand()
            SessionResult.RESULT_ERROR_NOT_SUPPORTED
        } else {
            val requestedSlot = runtimeState.lastRequestedSlot ?: return SessionResult.RESULT_SUCCESS
            val snapshot = loadPlaybackSnapshot()
            if (!snapshot.resumeEligible && isPassiveSystemResumptionController(controllerPackageName)) {
                Timber.d(
                    "Ignoring passive resume request while auto-play is off from controller=%s",
                    controllerPackageName
                )
                return SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            acceptStartPlaybackCommand(requestedSlot, null)
            SessionResult.RESULT_ERROR_NOT_SUPPORTED
        }
    }

    private fun handleCompatSessionPlayCommand() {
        val currentPlayer = player ?: return
        val runtimeState = playbackRuntimeStateStore.state.value
        if (runtimeState.isPlaying || currentPlayer.isPlaying) {
            return
        }

        val requestedSlot = runtimeState.lastRequestedSlot ?: return
        val snapshot = loadPlaybackSnapshot()
        if (!snapshot.resumeEligible) {
            Timber.d("Ignoring compat session play request while resume is disabled")
            return
        }

        acceptStartPlaybackCommand(requestedSlot, null)
    }

    private fun isPassiveSystemResumptionController(controllerPackageName: String?): Boolean {
        val normalizedPackageName = controllerPackageName?.trim()?.lowercase() ?: return false
        return normalizedPackageName == "com.android.systemui" ||
            normalizedPackageName == "miui.systemui.plugin"
    }

    private fun acceptStartPlaybackCommand(audioSlot: Int, audioUriOverride: String?) {
        val runtimeState = playbackRuntimeStateStore.state.value
        val isDuplicateStart = runtimeState.activeSlot == audioSlot &&
            (runtimeState.isPlaying || runtimeState.isTransitionPending)
        if (isDuplicateStart) {
            Timber.d(
                "Ignoring duplicate start command for slot=%d state=%s",
                audioSlot,
                runtimeState
            )
            return
        }

        isStoppingService = false
        val commandVersion = invalidatePlaybackCommandVersion()
        playbackRuntimeStateStore.markStartPending(audioSlot)
        startForegroundWithNotification()
        startPlayback(audioSlot, audioUriOverride, commandVersion)
    }

    private fun handleStopPlaybackCommand() {
        val runtimeState = playbackRuntimeStateStore.state.value
        val isIdle = runtimeState.activeSlot == null &&
            !runtimeState.isPlaying &&
            !runtimeState.isTransitionPending &&
            player?.isPlaying != true
        if (isIdle) {
            Timber.d("Ignoring stop command while playback is already idle")
            return
        }

        invalidatePlaybackCommandVersion()
        pausePlaybackIntoResumableState()
    }

    private fun invalidatePlaybackCommandVersion(): Long {
        playbackCommandVersion += 1
        return playbackCommandVersion
    }

    private fun buildPlayableMediaItem(
        snapshot: PlaybackSessionSnapshot,
        replayUri: String
    ): MediaItem {
        return MediaItem.Builder()
            .setUri(replayUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(resolveDisplayTitle(snapshot))
                    .build()
            )
            .build()
    }

    private fun currentRuntimeSlotHint(): Int? {
        return if (::playbackRuntimeStateStore.isInitialized) {
            playbackRuntimeStateStore.state.value.lastRequestedSlot
        } else {
            null
        }
    }

    private fun resolveSlotLabel(slot: Int?): String? {
        return when (slot) {
            AUDIO_SLOT_PRIMARY -> getString(R.string.song_1_label)
            AUDIO_SLOT_SECONDARY -> getString(R.string.song_2_label)
            else -> null
        }
    }

    private fun normalizeFallbackTitle(rawTitle: String): String {
        val strippedExtension = rawTitle.substringBeforeLast('.', missingDelimiterValue = rawTitle).trim()
        if (strippedExtension.isBlank()) return getString(R.string.app_name)

        val normalized = if (strippedExtension.startsWith("customer_")) {
            strippedExtension.removePrefix("customer_").substringAfter('_', strippedExtension)
        } else {
            strippedExtension
        }.replace('_', ' ').trim()

        return when (normalized.lowercase()) {
            "hello" -> getString(R.string.song_1_label)
            "goodbye" -> getString(R.string.song_2_label)
            else -> normalized.ifBlank { getString(R.string.app_name) }
        }
    }

    private fun buildPlaybackNotification(
        snapshot: PlaybackSessionSnapshot,
        isPlaying: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(resolveDisplayTitle(snapshot))
            .setContentText(
                if (isPlaying) {
                    getString(R.string.media_notification_playing)
                } else {
                    getString(R.string.media_notification_ready_to_replay)
                }
            )
            .setSubText(getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (isPlaying) R.string.media_notification_pause else R.string.media_notification_play),
                buildServicePendingIntent(if (isPlaying) ACTION_STOP_PLAYBACK else ACTION_START_PLAYBACK)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(routingSessionCompat?.sessionToken)
                    .setShowActionsInCompactView(0)
            )

        buildSessionActivityPendingIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    private fun showResumableNotification(snapshot: PlaybackSessionSnapshot) {
        val notification = buildPlaybackNotification(snapshot, isPlaying = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun updateCompatSessionState(
        snapshot: PlaybackSessionSnapshot,
        state: Int
    ) {
        val compatSession = routingSessionCompat ?: return
        val title = resolveDisplayTitle(snapshot)
        compatSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, snapshot.audioUri)
                .build()
        )
        compatSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    state,
                    if (state == PlaybackStateCompat.STATE_PLAYING) player?.currentPosition ?: 0L else 0L,
                    if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )
        compatSession.isActive = state != PlaybackStateCompat.STATE_STOPPED || snapshot.resumeEligible
    }

    private fun buildServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AutoPlayMusicService::class.java).setAction(action)
        if (action == ACTION_START_PLAYBACK) {
            intent.putExtra(
                EXTRA_AUDIO_SLOT,
                playbackRuntimeStateStore.state.value.lastRequestedSlot ?: AUDIO_SLOT_PRIMARY
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun buildSessionActivityPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(this, 0, launchIntent, flags)
    }

    private fun clearForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        abandonAudioFocus()
        player?.removeListener(playbackCompletionListener)
        routingSessionCompat?.run {
            isActive = false
            release()
            routingSessionCompat = null
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        focusRecoveryJob?.cancel()
        focusRecoveryJob = null
        if (::playbackRuntimeStateStore.isInitialized) {
            playbackRuntimeStateStore.reset()
        }
        super.onDestroy()
    }
}


