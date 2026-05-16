package com.vibegravity.bluecruise.service

import android.content.Intent
import android.media.AudioManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutoPlayMusicServiceTest {

    @Test
    fun `onPlaybackResumption returns selected file with zero start position`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.google.android.projection.gearhead"
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackSessionStore", playbackSessionStore)

        val resultFuture = mediaSessionCallback(service).onPlaybackResumption(session, controller)
        val result = resultFuture.get()

        assertEquals(0, result.startIndex)
        assertEquals(0L, result.startPositionMs)
        assertEquals(1, result.mediaItems.size)
        assertEquals("content://media/external/audio/media/777", result.mediaItems.first().localConfiguration?.uri.toString())
        serviceController.destroy()
    }

    @Test
    fun `onPlaybackResumption fails when no replayable file is available`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val snapshot = PlaybackSessionSnapshot(
            audioUri = null,
            displayTitle = null,
            targetMac = null,
            aaEnabledForTarget = false,
            resumeEligible = false,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.google.android.projection.gearhead"
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackSessionStore", playbackSessionStore)

        val resultFuture = mediaSessionCallback(service).onPlaybackResumption(session, controller)

        try {
            resultFuture.get()
            fail("Expected playback resumption to fail without a replayable item")
        } catch (expected: ExecutionException) {
            assertTrue(expected.cause is IllegalStateException)
        }

        serviceController.destroy()
    }

    @Test
    fun `stop action pauses playback but keeps service resumable`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)
        invokePrivate(service, "startForegroundWithNotification")

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)

        verify { playbackOrchestrator.stopPlayback(player) }
        assertFalse(isStoppingService(service))
        assertTrue(routingSessionCompat.isActive)
        assertEquals(PlaybackStateCompat.STATE_PAUSED, routingSessionCompat.controller.playbackState?.state)
        assertEquals("Greeting track", shadowOf(service).lastForegroundNotification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `start command marks slot pending then active when playback starts successfully`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        coEvery { playbackSessionStore.snapshot() } returns snapshot
        coEvery {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                null
            )
        } returns true

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        service.onStartCommand(
            Intent(AutoPlayMusicService.ACTION_START_PLAYBACK).putExtra(
                AutoPlayMusicService.EXTRA_AUDIO_SLOT,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY
            ),
            0,
            0
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaying = true,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `failed playback startup resets runtime state back to idle`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        coEvery { playbackSessionStore.snapshot() } returns snapshot
        coEvery {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                null
            )
        } returns false

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        service.onStartCommand(
            Intent(AutoPlayMusicService.ACTION_START_PLAYBACK).putExtra(
                AutoPlayMusicService.EXTRA_AUDIO_SLOT,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY
            ),
            0,
            0
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `stale playback startup result is ignored after stop command`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )
        val startResult = CompletableDeferred<Boolean>()

        coEvery { playbackSessionStore.snapshot() } returns snapshot
        coEvery {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                null
            )
        } coAnswers { startResult.await() }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        service.onStartCommand(
            Intent(AutoPlayMusicService.ACTION_START_PLAYBACK).putExtra(
                AutoPlayMusicService.EXTRA_AUDIO_SLOT,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY
            ),
            0,
            0
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = true
            ),
            runtimeStateStore.state.value
        )

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)
        startResult.complete(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `duplicate start for same slot while pending is ignored`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markStartPending(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        service.onStartCommand(
            Intent(AutoPlayMusicService.ACTION_START_PLAYBACK).putExtra(
                AutoPlayMusicService.EXTRA_AUDIO_SLOT,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY
            ),
            0,
            0
        )
        shadowOf(Looper.getMainLooper()).idle()

        coVerify(exactly = 0) {
            playbackOrchestrator.startPlayback(any(), any(), any(), any(), any())
        }
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = true
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `duplicate start for same slot while already playing is ignored`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        service.onStartCommand(
            Intent(AutoPlayMusicService.ACTION_START_PLAYBACK).putExtra(
                AutoPlayMusicService.EXTRA_AUDIO_SLOT,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY
            ),
            0,
            0
        )
        shadowOf(Looper.getMainLooper()).idle()

        coVerify(exactly = 0) {
            playbackOrchestrator.startPlayback(any(), any(), any(), any(), any())
        }
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = true,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `stop while idle is ignored`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { playbackOrchestrator.stopPlayback(any()) }
        assertEquals(PlaybackRuntimeState(), runtimeStateStore.state.value)
        serviceController.destroy()
    }

    @Test
    fun `start foreground notification uses replayable track metadata`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackSessionStore", playbackSessionStore)

        invokePrivate(service, "startForegroundWithNotification")

        val notification = shadowOf(service).lastForegroundNotification
        assertEquals("Greeting track", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(
            service.getString(com.vibegravity.bluecruise.R.string.app_name),
            notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        )
        serviceController.destroy()
    }

    @Test
    fun `start foreground notification does not expose raw customer sync file name`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "file:///storage/emulated/0/Android/data/com.vibegravity.bluecruise/files/customer_69b123_hello.mp3",
            displayTitle = null,
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)

        invokePrivate(service, "startForegroundWithNotification")

        val notification = shadowOf(service).lastForegroundNotification
        assertEquals(
            service.getString(com.vibegravity.bluecruise.R.string.song_1_label),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        )
        serviceController.destroy()
    }

    @Test
    fun `resumable notification play action reuses the last requested slot`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/888",
            displayTitle = "Goodbye track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )

        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_SECONDARY)
        invokePrivate(service, "startForegroundWithNotification")

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)

        val notificationManager = service.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notificationManager).allNotifications.last()
        val playIntent = shadowOf(notification.actions.first().actionIntent as PendingIntent).savedIntent

        assertEquals(AutoPlayMusicService.ACTION_START_PLAYBACK, playIntent.action)
        assertEquals(
            AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
            playIntent.getIntExtra(AutoPlayMusicService.EXTRA_AUDIO_SLOT, AutoPlayMusicService.AUDIO_SLOT_PRIMARY)
        )
        serviceController.destroy()
    }

    @Test
    fun `media session play pause command while playing enters resumable paused state`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.android.systemui"
        every { player.isPlaying } returns true
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        val result = mediaSessionCallback(service).onPlayerCommandRequest(
            session,
            controller,
            Player.COMMAND_PLAY_PAUSE
        )

        assertEquals(SessionResult.RESULT_ERROR_NOT_SUPPORTED, result)
        verify { playbackOrchestrator.stopPlayback(player) }
        assertEquals(PlaybackStateCompat.STATE_PAUSED, routingSessionCompat.controller.playbackState?.state)
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `compat media session pause callback while playing enters resumable paused state`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )
        every { player.isPlaying } returns true
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)
        invokePrivate(service, "startForegroundWithNotification")

        compatSessionCallback(service).onPause()
        shadowOf(Looper.getMainLooper()).idle()

        verify { playbackOrchestrator.stopPlayback(player) }
        assertEquals(PlaybackStateCompat.STATE_PAUSED, routingSessionCompat(service).controller.playbackState?.state)
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `media session play pause command while paused restarts last requested slot from zero`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = true,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.android.systemui"
        every { player.isPlaying } returns false
        coEvery { playbackSessionStore.snapshot() } returns snapshot
        coEvery {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                null
            )
        } returns true
        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)
        clearMocks(playbackOrchestrator, answers = false)

        val result = mediaSessionCallback(service).onPlayerCommandRequest(
            session,
            controller,
            Player.COMMAND_PLAY_PAUSE
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(SessionResult.RESULT_ERROR_NOT_SUPPORTED, result)
        coVerify {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                null
            )
        }
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = true,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `media session play pause command while paused ignores passive systemui resume when auto play is off`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = false,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.android.systemui"
        every { player.isPlaying } returns false
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)
        clearMocks(playbackOrchestrator, answers = false)

        val result = mediaSessionCallback(service).onPlayerCommandRequest(
            session,
            controller,
            Player.COMMAND_PLAY_PAUSE
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(SessionResult.RESULT_ERROR_NOT_SUPPORTED, result)
        coVerify(exactly = 0) {
            playbackOrchestrator.startPlayback(any(), any(), any(), any(), any())
        }
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `compat media session play callback ignores resume when auto play is off`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = false,
            preparedAtElapsedMs = null
        )
        every { player.isPlaying } returns false
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackSessionStore", playbackSessionStore)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        service.onStartCommand(Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK), 0, 0)
        clearMocks(playbackOrchestrator, answers = false)

        compatSessionCallback(service).onPlay()
        shadowOf(Looper.getMainLooper()).idle()

        coVerify(exactly = 0) {
            playbackOrchestrator.startPlayback(any(), any(), any(), any(), any())
        }
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
        serviceController.destroy()
    }

    @Test
    fun `onPlaybackResumption fails when auto play is off even with replayable item`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackSessionStore = mockk<PlaybackSessionStore>()
        val snapshot = PlaybackSessionSnapshot(
            audioUri = "content://media/external/audio/media/777",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true,
            resumeEligible = false,
            preparedAtElapsedMs = null
        )
        val session = mockk<androidx.media3.session.MediaSession>()
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>()
        every { controller.packageName } returns "com.android.systemui"
        coEvery { playbackSessionStore.snapshot() } returns snapshot

        setField(service, "playbackSessionStore", playbackSessionStore)

        val resultFuture = mediaSessionCallback(service).onPlaybackResumption(session, controller)

        try {
            resultFuture.get()
            fail("Expected playback resumption to fail while auto-play is off")
        } catch (expected: ExecutionException) {
            assertTrue(expected.cause is IllegalStateException)
        }

        serviceController.destroy()
    }

    @Test
    fun `playback completion listener stops playback and service only once`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val runtimeStateStore = PlaybackRuntimeStateStore()
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "playbackRuntimeStateStore", runtimeStateStore)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)
        runtimeStateStore.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        playbackCompletionListener(service).onPlaybackStateChanged(Player.STATE_ENDED)
        playbackCompletionListener(service).onPlaybackStateChanged(Player.STATE_ENDED)

        verify(exactly = 1) { playbackOrchestrator.stopPlayback(player) }
        assertTrue(isStoppingService(service))
        assertFalse(routingSessionCompat.isActive)
        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            runtimeStateStore.state.value
        )
    }

    @Test
    fun `non ended playback state does not trigger cleanup`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = true
        }

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        playbackCompletionListener(service).onPlaybackStateChanged(Player.STATE_READY)

        verify(exactly = 0) { playbackOrchestrator.stopPlayback(any()) }
        assertFalse(isStoppingService(service))
        assertTrue(routingSessionCompat.isActive)
    }

    @Test
    fun `audioFocusListener pauses player on AUDIOFOCUS_LOSS`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val player = mockk<ExoPlayer>(relaxed = true)

        setField(service, "player", player)
        setField(service, "isStoppingService", false)

        audioFocusListener(service).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        verify { player.pause() }
    }

    @Test
    fun `audioFocusListener pauses player on AUDIOFOCUS_LOSS_TRANSIENT`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val player = mockk<ExoPlayer>(relaxed = true)

        setField(service, "player", player)
        setField(service, "isStoppingService", false)

        audioFocusListener(service).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        verify { player.pause() }
    }

    @Test
    fun `audioFocusListener ducks volume on AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val player = mockk<ExoPlayer>(relaxed = true)

        setField(service, "player", player)

        audioFocusListener(service).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        verify { player.volume = 0.3f }
    }

    @Test
    fun `audioFocusListener restores volume and resumes on AUDIOFOCUS_GAIN`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val player = mockk<ExoPlayer>(relaxed = true)

        setField(service, "player", player)
        setField(service, "isStoppingService", false)
        every { player.isPlaying } returns false

        audioFocusListener(service).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        verify { player.volume = 1.0f }
        verify { player.play() }
    }

    @Test
    fun `audioFocusListener does not resume when service is stopping`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java)
        val service = serviceController.get()
        val player = mockk<ExoPlayer>(relaxed = true)

        setField(service, "player", player)
        setField(service, "isStoppingService", true)
        every { player.isPlaying } returns false

        audioFocusListener(service).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        verify { player.volume = 1.0f }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `service startPlayback does not pass dummy fallback uri into orchestrator`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        coEvery { playbackOrchestrator.startPlayback(player, null, routingSessionCompat) } returns true

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        invokePrivate(service, "startPlayback")
        shadowOf(Looper.getMainLooper()).idle()

        coVerify {
            playbackOrchestrator.startPlayback(player, null, routingSessionCompat)
        }
        serviceController.destroy()
    }

    @Test
    fun `service startPlayback forwards slot and explicit uri override to orchestrator`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        coEvery {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                "content://songs/slot-2.mp3"
            )
        } returns true

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        invokePrivateStartPlayback(
            service,
            AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
            "content://songs/slot-2.mp3"
        )
        shadowOf(Looper.getMainLooper()).idle()

        coVerify {
            playbackOrchestrator.startPlayback(
                player,
                null,
                routingSessionCompat,
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                "content://songs/slot-2.mp3"
            )
        }
        serviceController.destroy()
    }

    @Test
    fun `service startPlayback stops service when orchestrator aborts startup`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        coEvery { playbackOrchestrator.startPlayback(player, null, routingSessionCompat) } returns false

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "player", player)
        setField(service, "routingSessionCompat", routingSessionCompat)

        invokePrivate(service, "startForegroundWithNotification")
        invokePrivate(service, "startPlayback")
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(isStoppingService(service))
        assertFalse(routingSessionCompat.isActive)
        verify { playbackOrchestrator.stopPlayback(player) }
        serviceController.destroy()
    }

    @Test
    fun `service startPlayback does not request audio focus manually`() {
        val serviceController = Robolectric.buildService(AutoPlayMusicService::class.java).create()
        val service = serviceController.get()
        val playbackOrchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
        val player = mockk<ExoPlayer>(relaxed = true)
        val audioManager = mockk<AudioManager>(relaxed = true)
        val routingSessionCompat = MediaSessionCompat(service, "AutoPlayMusicServiceTest").apply {
            isActive = false
        }
        coEvery { playbackOrchestrator.startPlayback(player, null, routingSessionCompat) } returns true

        setField(service, "playbackOrchestrator", playbackOrchestrator)
        setField(service, "player", player)
        setField(service, "audioManager", audioManager)
        setField(service, "routingSessionCompat", routingSessionCompat)

        invokePrivate(service, "startPlayback")
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) {
            audioManager.requestAudioFocus(any<AudioManager.OnAudioFocusChangeListener>(), any(), any())
        }
        serviceController.destroy()
    }

    private fun playbackCompletionListener(service: AutoPlayMusicService): Player.Listener {
        val field = AutoPlayMusicService::class.java.getDeclaredField("playbackCompletionListener")
        field.isAccessible = true
        return field.get(service) as Player.Listener
    }

    private fun mediaSessionCallback(service: AutoPlayMusicService): androidx.media3.session.MediaSession.Callback {
        val field = AutoPlayMusicService::class.java.getDeclaredField("mediaSessionCallback")
        field.isAccessible = true
        return field.get(service) as androidx.media3.session.MediaSession.Callback
    }

    private fun audioFocusListener(service: AutoPlayMusicService): AudioManager.OnAudioFocusChangeListener {
        val field = AutoPlayMusicService::class.java.getDeclaredField("audioFocusListener")
        field.isAccessible = true
        return field.get(service) as AudioManager.OnAudioFocusChangeListener
    }

    private fun compatSessionCallback(service: AutoPlayMusicService): MediaSessionCompat.Callback {
        val field = AutoPlayMusicService::class.java.getDeclaredField("compatSessionCallback")
        field.isAccessible = true
        return field.get(service) as MediaSessionCompat.Callback
    }

    private fun routingSessionCompat(service: AutoPlayMusicService): MediaSessionCompat {
        val field = AutoPlayMusicService::class.java.getDeclaredField("routingSessionCompat")
        field.isAccessible = true
        return field.get(service) as MediaSessionCompat
    }

    private fun isStoppingService(service: AutoPlayMusicService): Boolean {
        val field = AutoPlayMusicService::class.java.getDeclaredField("isStoppingService")
        field.isAccessible = true
        return field.getBoolean(service)
    }

    private fun setField(service: AutoPlayMusicService, name: String, value: Any?) {
        val field = AutoPlayMusicService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun invokePrivate(service: AutoPlayMusicService, name: String) {
        val method = AutoPlayMusicService::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(service)
    }

    private fun invokePrivateStartPlayback(
        service: AutoPlayMusicService,
        audioSlot: Int,
        audioUriOverride: String?
    ) {
        val method = AutoPlayMusicService::class.java.getDeclaredMethod(
            "startPlayback",
            Int::class.javaPrimitiveType,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(service, audioSlot, audioUriOverride)
    }
}



