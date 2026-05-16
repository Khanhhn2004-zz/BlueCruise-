package com.vibegravity.bluecruise.receiver

import android.app.Application
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.domain.BluetoothConnectionAction
import com.vibegravity.bluecruise.domain.BluetoothConnectionHandler
import com.vibegravity.bluecruise.domain.BluetoothEventType
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE, sdk = [30])
class BluetoothConnectionReceiverAndroidAutoTest {
    private val baseContext = ApplicationProvider.getApplicationContext<Application>()
    private val handler = mockk<BluetoothConnectionHandler>()
    private val repo = mockk<IBluetoothAdapterRepo>()
    private val settings = mockk<SettingsRepository>()

    @Before
    fun resetSharedState() {
        clearMocks(handler, repo, settings)
        AndroidAutoHandoffSessionStore.resetForTest()
    }

    @Test
    fun `bluetooth only target connect starts after debounce when AA candidate stays false`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns false
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf(TARGET_MAC)
        every { repo.isAndroidAutoProcessRunning() } returns false

        context.setCarMode(false)
        context.setRemoteSubmixPresent(false)
        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(AutoPlayMusicService.ACTION_START_PLAYBACK, context.lastForegroundService?.action)
    }

    @Test
    fun `bluetooth only target connect respects configured connection start delay`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings, autoPlayOnAndroidAuto = false, connectionStartDelaySeconds = 3)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns false
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(0, context.startPlaybackCount)

        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `fast signals appearing at debounce recheck switch from bluetooth path into AA wait`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns false
        every { repo.isAndroidAutoProcessRunning() } returnsMany listOf(false, true, true, true)

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(500)
        runCurrent()
        assertNull(context.lastForegroundService)

        context.setCarMode(true)
        context.setRemoteSubmixPresent(true)
        advanceTimeBy(1000)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `confirmed aa target device remains pending past short window and falls back later`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(5_000)
        runCurrent()
        assertNull(context.lastForegroundService)

        advanceTimeBy(14_999)
        runCurrent()
        assertNull(context.lastForegroundService)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(AutoPlayMusicService.ACTION_START_PLAYBACK, context.lastForegroundService?.action)
    }

    @Test
    fun `aa fallback to bluetooth respects configured connection start delay`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings, connectionStartDelaySeconds = 2)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `confirmed aa target device enters AA wait when fast signals appear during pending window`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returnsMany listOf(false, false, false, true, true, true, true)

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(10_000)
        runCurrent()
        assertNull(context.lastForegroundService)

        context.setCarMode(true)
        context.setRemoteSubmixPresent(true)
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
        assertEquals(AndroidAutoHandoffState.COMPLETED, AndroidAutoHandoffSessionStore.shared().snapshot().state)
    }

    @Test
    fun `state on fallback reuses the same handoff logic as acl connected`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf(TARGET_MAC)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true

        receiver.handleStateOnFallback(context)
        advanceUntilIdle()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `same target duplicate connect reuses one active session and dispatches playback once`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true
        coEvery { repo.getConnectedA2dpAddresses() } returns emptyList()

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()

        assertEquals(1, context.startPlaybackCount)
        assertEquals(AndroidAutoHandoffState.COMPLETED, AndroidAutoHandoffSessionStore.shared().snapshot().state)
    }

    @Test
    fun `android auto wait does not time out when fast signals stay true but readiness stays false`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val harness = newReceiverHarness(this)
        val receiver = harness.receiver
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true

        val job = backgroundScope.launch {
            receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        }
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(0, context.startPlaybackCount)
        val snapshot = AndroidAutoHandoffSessionStore.shared().snapshot()
        assertEquals(AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, snapshot.state)
        assertEquals(false, snapshot.timedOut)

        AndroidAutoHandoffSessionStore.shared().markDisconnected(TARGET_MAC)
        job.cancel()
        harness.scope.cancel()
        runCurrent()
    }

    @Test
    fun `aftermarket aa target with stable partial signals falls back earlier than max window`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val harness = newReceiverHarness(this)
        val receiver = harness.receiver
        stubTargetSettings(settings, aftermarketAndroidAutoTarget = true)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(10_000)
        runCurrent()
        context.setRemoteSubmixPresent(true)

        advanceTimeBy(5_499)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        advanceTimeBy(1)
        runCurrent()

        AndroidAutoHandoffSessionStore.shared().markDisconnected(TARGET_MAC)
        harness.scope.cancel()
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `no fast AA signals keeps bluetooth path even when AA enabled`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns false
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf(TARGET_MAC)
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `target mac comparison is case-insensitive in receiver gating`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings, targetMac = "AA:BB:CC:DD:EE:FF")
        coEvery { handler.handle(BluetoothEventType.CONNECTED, any()) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(any()) } returns false
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf("AA:BB:CC:DD:EE:FF")
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, "aa:bb:cc:dd:ee:ff", "test_acl")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `android auto wait starts playback when readiness becomes true after long delay`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(false)
            setRemoteSubmixPresent(false)
        }
        val harness = newReceiverHarness(this)
        val receiver = harness.receiver
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true

        val job = backgroundScope.launch {
            receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        }
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        context.setCarMode(true)
        context.setRemoteSubmixPresent(true)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)

        AndroidAutoHandoffSessionStore.shared().markDisconnected(TARGET_MAC)
        job.cancel()
        harness.scope.cancel()
        runCurrent()
    }

    @Test
    fun `fully ready android auto path ignores connection start delay`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings, connectionStartDelaySeconds = 10)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `sample readiness snapshot returns null instead of throwing for stale session`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings, autoPlayOnAndroidAuto = false)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns false
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        val sessionId = AndroidAutoHandoffSessionStore.shared().snapshot().sessionId!!
        AndroidAutoHandoffSessionStore.shared().markDisconnected(TARGET_MAC)

        assertNull(receiver.sampleReadinessSnapshotOrNull(context, sessionId))
    }

    @Test
    fun `bluetooth adapter restart creates a fresh session after wait`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returnsMany listOf(true, false)
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf(TARGET_MAC)
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, context.startPlaybackCount)

        receiver.handleBluetoothStateChanged(context, BluetoothAdapter.STATE_ON)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, context.startPlaybackCount)
    }

    @Test
    fun `disconnect during AA wait cancels pending playback and later ready signals do not start`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns false

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        receiver.handleDisconnect(context, TARGET_MAC)
        context.setCarMode(true)
        context.setRemoteSubmixPresent(true)
        advanceTimeBy(11_000)
        advanceUntilIdle()

        assertEquals(0, context.startPlaybackCount)
        assertEquals(AndroidAutoHandoffState.IDLE, AndroidAutoHandoffSessionStore.shared().snapshot().state)
    }

    @Test
    fun `android auto started session suppresses immediate stop while candidate signals remain`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true
        coEvery { repo.getConnectedA2dpAddresses() } returns emptyList()

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()
        assertEquals(AndroidAutoHandoffState.COMPLETED, AndroidAutoHandoffSessionStore.shared().snapshot().state)
        receiver.handleDisconnect(context, TARGET_MAC)

        assertEquals(0, context.stopPlaybackCount)
        assertNotNull(AndroidAutoHandoffSessionStore.shared().snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `android auto stop verification re-arms when any readiness signal remains`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true
        coEvery { repo.getConnectedA2dpAddresses() } returns emptyList()

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()
        receiver.handleDisconnect(context, TARGET_MAC)
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(0, context.stopPlaybackCount)
        assertNotNull(AndroidAutoHandoffSessionStore.shared().snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `android auto started session stops after verification when fast readiness signals clear`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returnsMany listOf(true, true, true, true, false)
        coEvery { repo.getConnectedA2dpAddresses() } returns emptyList()

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()
        receiver.handleDisconnect(context, TARGET_MAC)
        context.setCarMode(false)
        context.setRemoteSubmixPresent(false)
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(1, context.stopPlaybackCount)
    }

    @Test
    fun `target A2DP reconnect during stop verification prevents stale stop`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returnsMany listOf(true, true, true, true, false)
        coEvery { repo.getConnectedA2dpAddresses() } returns listOf(TARGET_MAC)

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()
        assertEquals(AndroidAutoHandoffState.COMPLETED, AndroidAutoHandoffSessionStore.shared().snapshot().state)
        receiver.handleDisconnect(context, TARGET_MAC)
        assertEquals(0, context.stopPlaybackCount)
        assertNotNull(AndroidAutoHandoffSessionStore.shared().snapshot().pendingStopVerificationAtElapsedMs)
        context.setCarMode(false)
        context.setRemoteSubmixPresent(false)
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(0, context.stopPlaybackCount)
        assertNull(AndroidAutoHandoffSessionStore.shared().snapshot().pendingStopVerificationAtElapsedMs)
        coVerify(exactly = 1) { repo.getConnectedA2dpAddresses() }
    }

    @Test
    fun `target reconnect clears pending stop verification and prevents stale stop`() = runTest {
        val context = RecordingReceiverContext(baseContext).apply {
            setCarMode(true)
            setRemoteSubmixPresent(true)
        }
        val receiver = newReceiver(this)
        stubTargetSettings(settings)
        coEvery { handler.handle(BluetoothEventType.CONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StartPlayback
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback
        coEvery { repo.isAndroidAutoDevice(TARGET_MAC) } returns true
        every { repo.isAndroidAutoProcessRunning() } returns true
        coEvery { repo.getConnectedA2dpAddresses() } returns emptyList()

        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceUntilIdle()
        receiver.handleDisconnect(context, TARGET_MAC)
        receiver.handleTargetAutoPlayTrigger(context, TARGET_MAC, "test_acl")
        advanceTimeBy(6_000)
        advanceUntilIdle()

        assertEquals(0, context.stopPlaybackCount)
        assertNull(AndroidAutoHandoffSessionStore.shared().snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `bluetooth only session still stops immediately on target disconnect`() = runTest {
        val context = RecordingReceiverContext(baseContext)
        val receiver = newReceiver(this)
        stubTargetSettings(settings, autoPlayOnAndroidAuto = false)
        coEvery { handler.handle(BluetoothEventType.DISCONNECTED, TARGET_MAC) } returns BluetoothConnectionAction.StopPlayback

        receiver.handleDisconnect(context, TARGET_MAC)
        advanceUntilIdle()

        assertEquals(1, context.stopPlaybackCount)
    }

    private fun newReceiver(scope: TestScope): BluetoothConnectionReceiver {
        return newReceiverWithParams(
            scope = scope,
            maxPollCount = 100,  // Đủ lớn cho test với virtual time
            totalPollTimeoutMs = 60_000L,  // 60 giây cho test
            maxPollIntervalMs = 5_000L
        )
    }

    private fun newReceiverWithParams(
        scope: TestScope,
        maxPollCount: Int,
        totalPollTimeoutMs: Long,
        maxPollIntervalMs: Long
    ): BluetoothConnectionReceiver {
        val receiverCoroutineScope = CoroutineScope(
            SupervisorJob(scope.backgroundScope.coroutineContext[Job]) +
                StandardTestDispatcher(scope.testScheduler)
        )
        val receiver = BluetoothConnectionReceiver(
            receiverScope = receiverCoroutineScope,
            startPlaybackDelayMs = 500L,
            stateOnFallbackDelayMs = 1500L,
            readinessPollIntervalMs = 500L,
            stopVerificationDelayMs = 6_000L,
            maxPollCount = maxPollCount,
            totalPollTimeoutMs = totalPollTimeoutMs,
            maxPollIntervalMs = maxPollIntervalMs,
            startDebouncer = PlaybackStartDebouncer(receiverCoroutineScope, 500L),
            stopDebouncer = PlaybackStopDebouncer(receiverCoroutineScope, 6_000L),
            elapsedRealtimeMs = { scope.testScheduler.currentTime }
        )
        receiver.injectForTest(
            bluetoothConnectionHandler = handler,
            bluetoothAdapterRepo = repo,
            settingsRepository = settings
        )
        return receiver
    }

    private fun newReceiverHarness(scope: TestScope): ReceiverHarness {
        val receiverCoroutineScope = CoroutineScope(
            SupervisorJob(scope.backgroundScope.coroutineContext[Job]) +
                StandardTestDispatcher(scope.testScheduler)
        )
        val receiver = BluetoothConnectionReceiver(
            receiverScope = receiverCoroutineScope,
            startPlaybackDelayMs = 500L,
            stateOnFallbackDelayMs = 1500L,
            readinessPollIntervalMs = 500L,
            stopVerificationDelayMs = 6_000L,
            maxPollCount = 100,  // Đủ lớn cho test với virtual time
            totalPollTimeoutMs = 60_000L,  // 60 giây cho test
            maxPollIntervalMs = 5_000L,
            startDebouncer = PlaybackStartDebouncer(receiverCoroutineScope, 500L),
            stopDebouncer = PlaybackStopDebouncer(receiverCoroutineScope, 6_000L),
            elapsedRealtimeMs = { scope.testScheduler.currentTime }
        )
        receiver.injectForTest(
            bluetoothConnectionHandler = handler,
            bluetoothAdapterRepo = repo,
            settingsRepository = settings
        )
        return ReceiverHarness(receiver, receiverCoroutineScope)
    }

    private fun stubTargetSettings(
        settingsRepository: SettingsRepository,
        targetMac: String? = TARGET_MAC,
        autoPlayEnabled: Boolean = true,
        autoPlayOnAndroidAuto: Boolean = true,
        aftermarketAndroidAutoTarget: Boolean = false,
        connectionStartDelaySeconds: Int = 0,
        simulateAndroidAuto: Boolean = false
    ) {
        every { settingsRepository.targetMacFlow } returns flowOf(targetMac)
        every { settingsRepository.autoPlayEnabledFlow } returns flowOf(autoPlayEnabled)
        every { settingsRepository.autoPlayOnAndroidAutoFlow } returns flowOf(autoPlayOnAndroidAuto)
        every { settingsRepository.aftermarketAndroidAutoTargetFlow } returns flowOf(aftermarketAndroidAutoTarget)
        every { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(connectionStartDelaySeconds)
        every { settingsRepository.simulateAndroidAutoFlow } returns flowOf(simulateAndroidAuto)
        every { settingsRepository.keepAppAliveFlow } returns flowOf(false)
        every { settingsRepository.audioFilePathFlow } returns flowOf(null)
        every { settingsRepository.routingTierFlow } returns flowOf(0)
        every { settingsRepository.autoStartDismissedFlow } returns flowOf(false)
        every { settingsRepository.skipAutoPlayWhenAaConnectingFlow } returns flowOf(false)
    }

    private fun BluetoothConnectionReceiver.injectForTest(
        bluetoothConnectionHandler: BluetoothConnectionHandler,
        bluetoothAdapterRepo: IBluetoothAdapterRepo,
        settingsRepository: SettingsRepository
    ) {
        injectField("bluetoothConnectionHandler", bluetoothConnectionHandler)
        injectField("bluetoothAdapterRepo", bluetoothAdapterRepo)
        injectField("settingsRepository", settingsRepository)
    }

    private fun BluetoothConnectionReceiver.injectField(name: String, value: Any) {
        BluetoothConnectionReceiver::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@injectField, value)
        }
    }

    private class RecordingReceiverContext(base: Context) : ContextWrapper(base) {
        private val uiModeManager = mockk<UiModeManager>()
        private val audioManager = mockk<AudioManager>(relaxed = true)
        private val remoteSubmixDevice = mockk<AudioDeviceInfo>()

        var lastForegroundService: Intent? = null
        var lastService: Intent? = null
        var startPlaybackCount = 0
        var stopPlaybackCount = 0
        private var carMode = false
        private var remoteSubmixPresent = false

        init {
            every { uiModeManager.currentModeType } answers {
                if (carMode) Configuration.UI_MODE_TYPE_CAR else Configuration.UI_MODE_TYPE_NORMAL
            }
            every { remoteSubmixDevice.type } returns AudioDeviceInfo.TYPE_REMOTE_SUBMIX
            every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } answers {
                if (remoteSubmixPresent) arrayOf(remoteSubmixDevice) else emptyArray()
            }
        }

        fun setCarMode(enabled: Boolean) {
            carMode = enabled
        }

        fun setRemoteSubmixPresent(enabled: Boolean) {
            remoteSubmixPresent = enabled
        }

        override fun getApplicationContext(): Context {
            return this
        }

        override fun getSystemService(name: String): Any? {
            return when (name) {
                Context.UI_MODE_SERVICE -> uiModeManager
                Context.AUDIO_SERVICE -> audioManager
                else -> super.getSystemService(name)
            }
        }

        override fun startForegroundService(service: Intent): ComponentName {
            lastForegroundService = service
            if (service.action == AutoPlayMusicService.ACTION_START_PLAYBACK) {
                startPlaybackCount += 1
            }
            return service.component ?: ComponentName(this, AutoPlayMusicService::class.java)
        }

        override fun startService(service: Intent): ComponentName {
            lastService = service
            if (service.action == AutoPlayMusicService.ACTION_STOP_PLAYBACK) {
                stopPlaybackCount += 1
            }
            return service.component ?: ComponentName(this, AutoPlayMusicService::class.java)
        }
    }

    private companion object {
        private const val TARGET_MAC = "32:64:36:35:31:64"
    }

    private data class ReceiverHarness(
        val receiver: BluetoothConnectionReceiver,
        val scope: CoroutineScope
    )
}





