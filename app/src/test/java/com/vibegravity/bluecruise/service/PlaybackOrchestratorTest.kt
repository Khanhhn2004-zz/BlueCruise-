package com.vibegravity.bluecruise.service

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.domain.RoutingExecutor
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PlaybackOrchestratorTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val routingExecutor = mockk<RoutingExecutor>(relaxed = true)
    private val player = mockk<ExoPlayer>(relaxed = true)

    private val orchestrator = PlaybackOrchestrator(
        context = context,
        settingsRepository = settingsRepository,
        routingExecutor = routingExecutor
    )

    init {
        coEvery { settingsRepository.serverAudioTitle1Flow } returns flowOf(null)
        coEvery { settingsRepository.serverAudioTitle2Flow } returns flowOf(null)
    }

    @Test
    fun `startPlayback forwards routing session token for Tier 2`() = runTest {
        val routingSessionToken = Any()
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf("https://example.com/audio.mp3")
        coEvery { settingsRepository.routingTierFlow } returns flowOf(2)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player, routingSessionToken = routingSessionToken)
    }

    @Test
    fun `startPlayback ignores connection start delay setting and still configures player and plays`() = runTest {
        val resourceUri = "android.resource://${context.packageName}/${R.raw.dummy_silence}"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf(resourceUri)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(10)

        orchestrator.startPlayback(player)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback replays selected file from zero position`() = runTest {
        val resourceUri = "android.resource://${context.packageName}/${R.raw.dummy_silence}"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf(resourceUri)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with secondary slot uses secondary saved audio path`() = runTest {
        val resourceUri = "android.resource://${context.packageName}/${R.raw.dummy_silence}"
        coEvery { settingsRepository.audioFilePath2Flow } returns flowOf(resourceUri)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player, audioSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with empty primary slot falls back to bundled greeting audio`() = runTest {
        val defaultGreetingId = context.resources.getIdentifier(
            "default_greeting",
            "raw",
            context.packageName
        )
        val resourceUri = "android.resource://${context.packageName}/$defaultGreetingId"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf(null)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player, audioSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with empty secondary slot falls back to bundled goodbye audio`() = runTest {
        val defaultGoodbyeId = context.resources.getIdentifier(
            "default_goodbye",
            "raw",
            context.packageName
        )
        val resourceUri = "android.resource://${context.packageName}/$defaultGoodbyeId"
        coEvery { settingsRepository.audioFilePath2Flow } returns flowOf(null)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player, audioSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with explicit override uses override ahead of stored slot`() = runTest {
        val resourceUri = "android.resource://${context.packageName}/${R.raw.dummy_silence}"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf("https://example.com/audio.mp3")
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(
            player,
            audioSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
            audioUriOverride = resourceUri
        )

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with empty audio path and no explicit default uses bundled greeting audio`() = runTest {
        val defaultGreetingId = context.resources.getIdentifier(
            "default_greeting",
            "raw",
            context.packageName
        )
        val resourceUri = "android.resource://${context.packageName}/$defaultGreetingId"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf(null)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with empty string audio path and no explicit default uses bundled greeting audio`() = runTest {
        val defaultGreetingId = context.resources.getIdentifier(
            "default_greeting",
            "raw",
            context.packageName
        )
        val resourceUri = "android.resource://${context.packageName}/$defaultGreetingId"
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf("")
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player)

        verify {
            player.setMediaItem(match { mediaItem ->
                mediaItem.localConfiguration?.uri.toString() == resourceUri
            }, 0L)
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback with empty audio path but defaultUri runs exploit and plays default`() = runTest {
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf(null)
        coEvery { settingsRepository.routingTierFlow } returns flowOf(1)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)
        coEvery { routingExecutor.executeRoutingExploit(any(), any()) } returns Unit
        coEvery { settingsRepository.saveAudioFilePath(any()) } returns Unit

        orchestrator.startPlayback(player, defaultAudioUri = "https://example.com/dummy.mp3")
    }

    @Test
    fun `startPlayback with missing local file aborts before routing or player configuration`() = runTest {
        coEvery { settingsRepository.audioFilePathFlow } returns flowOf("file:///definitely-missing.mp3")
        coEvery { settingsRepository.routingTierFlow } returns flowOf(2)
        coEvery { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        orchestrator.startPlayback(player)

        coVerify(exactly = 0) { routingExecutor.executeRoutingExploit(any(), any()) }
        verify(exactly = 0) { player.setMediaItem(any(), any<Long>()) }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `stopPlayback pauses and stops player`() {
        orchestrator.stopPlayback(player)

        verify { player.pause() }
        verify { player.stop() }
    }
}
