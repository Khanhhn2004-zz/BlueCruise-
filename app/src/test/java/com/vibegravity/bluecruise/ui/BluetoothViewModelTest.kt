package com.vibegravity.bluecruise.ui

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain
import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncOutcome
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.customer.SlotSyncResult
import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bluetoothAdapterRepo.bluetoothEnabledFlow() } returns flowOf(true)
        every { settingsRepository.targetMacFlow } returns flowOf(null)
        every { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)
        every { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)
        every { settingsRepository.keepAppAliveFlow } returns flowOf(false)
        every { settingsRepository.autoPlayOnAndroidAutoFlow } returns flowOf(false)
        every { settingsRepository.aftermarketAndroidAutoTargetFlow } returns flowOf(false)
        every { settingsRepository.simulateAndroidAutoFlow } returns flowOf(false)
        every { settingsRepository.audioFilePathFlow } returns flowOf(null)
        every { settingsRepository.audioFilePath2Flow } returns flowOf(null)
        every { settingsRepository.song1SourceFlow } returns flowOf(SongSlotSource.SERVER)
        every { settingsRepository.song2SourceFlow } returns flowOf(SongSlotSource.SERVER)
        every { settingsRepository.routingTierFlow } returns flowOf(1)
        every { settingsRepository.autoStartDismissedFlow } returns flowOf(false)
        every { settingsRepository.skipAutoPlayWhenAaConnectingFlow } returns flowOf(false)
        every { settingsRepository.floatingBubbleEnabledFlow } returns flowOf(false)
        every { authSessionRepository.sessionFlow } returns flowOf(AuthSession())
        coEvery { authRepository.logout() } returns Unit
        coEvery { settingsRepository.setSongSlotSource(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val settingsRepository = mockk<SettingsRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val authSessionRepository = mockk<AuthSessionRepository>()
    private val customerSongSyncRepository = mockk<CustomerSongSyncRepository>()
    private val bluetoothAdapterRepo = mockk<IBluetoothAdapterRepo>(relaxed = true)
    private val manualPlaybackServiceController = mockk<ManualPlaybackServiceController>(relaxed = true)

    private fun createViewModel(provider: MediaControllerProvider? = null): BluetoothViewModel {
        return BluetoothViewModel(
            bluetoothAdapterRepo = bluetoothAdapterRepo,
            settingsRepository = settingsRepository,
            authRepository = authRepository,
            authSessionRepository = authSessionRepository,
            customerSongSyncRepository = customerSongSyncRepository,
            manualPlaybackServiceController = manualPlaybackServiceController,
            mediaControllerProvider = provider,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state loads preferences from repository`() = runTest(testDispatcher) {
        every { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        every { settingsRepository.autoPlayEnabledFlow } returns flowOf(false)
        every { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(3)
        every { settingsRepository.audioFilePath2Flow } returns flowOf("content://songs/slot2.mp3")
        every { settingsRepository.floatingBubbleEnabledFlow } returns flowOf(true)
        every { settingsRepository.routingTierFlow } returns flowOf(2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("AA:BB:CC:DD:EE:FF", state.targetMacAddress)
            assertEquals(false, state.autoPlayEnabled)
            assertEquals(3, state.connectionStartDelaySeconds)
            assertEquals("content://songs/slot2.mp3", state.audioFilePath2)
            assertTrue(state.floatingBubbleEnabled)
            assertEquals(2, state.routingTier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state defaults connection start delay to zero`() = runTest(testDispatcher) {
        every { settingsRepository.connectionStartDelaySecondsFlow } returns flowOf(0)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.connectionStartDelaySeconds)
    }

    @Test
    fun `setConnectionStartDelaySeconds persists clamped value and updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.setConnectionStartDelaySeconds(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setConnectionStartDelaySeconds(12)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setConnectionStartDelaySeconds(10) }
        assertEquals(10, viewModel.uiState.value.connectionStartDelaySeconds)
    }

    @Test
    fun `setAudioFilePath2 saves repository and updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.saveAudioFilePath2(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.setAudioFilePath2("content://songs/slot2-updated.mp3")
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals("content://songs/slot2-updated.mp3", updatedState.audioFilePath2)
            coVerify(exactly = 1) {
                settingsRepository.saveAudioFilePath2("content://songs/slot2-updated.mp3")
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFloatingBubbleEnabled saves repository and updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.setFloatingBubbleEnabled(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.setFloatingBubbleEnabled(true)
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertTrue(updatedState.floatingBubbleEnabled)
            coVerify(exactly = 1) { settingsRepository.setFloatingBubbleEnabled(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `floating bubble pending permission request survives until permission is granted`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isFloatingBubblePermissionPending)

            viewModel.markFloatingBubblePermissionPending()
            assertTrue(viewModel.uiState.value.isFloatingBubblePermissionPending)

            assertFalse(viewModel.consumeFloatingBubblePermissionGrant(hasPermission = false))
            assertTrue(viewModel.uiState.value.isFloatingBubblePermissionPending)

            assertTrue(viewModel.consumeFloatingBubblePermissionGrant(hasPermission = true))
            assertFalse(viewModel.uiState.value.isFloatingBubblePermissionPending)
        }

    @Test
    fun `floating bubble pending permission request can be cleared before grant`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.markFloatingBubblePermissionPending()
            assertTrue(viewModel.uiState.value.isFloatingBubblePermissionPending)

            viewModel.clearFloatingBubblePermissionPending()

            assertFalse(viewModel.uiState.value.isFloatingBubblePermissionPending)
            assertFalse(viewModel.consumeFloatingBubblePermissionGrant(hasPermission = true))
        }

    @Test
    fun `setTargetDevice updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.saveTargetMac(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.setTargetDevice("11:22:33:44:55:66")
            advanceUntilIdle()
            assertEquals("11:22:33:44:55:66", awaitItem().targetMacAddress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAutoPlayEnabled updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.setAutoPlayEnabled(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.setAutoPlayEnabled(false)
            advanceUntilIdle()
            assertEquals(false, awaitItem().autoPlayEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRoutingTier updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.saveRoutingTier(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.setRoutingTier(3)
            advanceUntilIdle()
            assertEquals(3, awaitItem().routingTier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state loads aftermarket android auto target setting`() = runTest(testDispatcher) {
        every { settingsRepository.autoPlayOnAndroidAutoFlow } returns flowOf(true)
        every { settingsRepository.aftermarketAndroidAutoTargetFlow } returns flowOf(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAftermarketAndroidAutoTargetEnabled)
    }

    @Test
    fun `setAftermarketAndroidAutoTargetEnabled updates state`() = runTest(testDispatcher) {
        coEvery { settingsRepository.setAftermarketAndroidAutoTarget(any()) } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAftermarketAndroidAutoTargetEnabled(true)
        advanceUntilIdle()

        coVerify { settingsRepository.setAftermarketAndroidAutoTarget(true) }
        assertTrue(viewModel.uiState.value.isAftermarketAndroidAutoTargetEnabled)
    }

    @Test
    fun `setAudioFilePath persists replayable item for android auto resumption`() = runTest(testDispatcher) {
        every { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        every { settingsRepository.autoPlayOnAndroidAutoFlow } returns flowOf(true)
        coEvery { settingsRepository.saveAudioFilePath(any()) } returns Unit
        coEvery { settingsRepository.setSongSlotSource(any(), any()) } returns Unit
        coEvery { settingsRepository.saveReplayableItem(any(), any(), any(), any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAudioFilePath("content://media/external/audio/media/42")
        advanceUntilIdle()

        coVerify {
            settingsRepository.setSongSlotSource(1, SongSlotSource.MANUAL)
        }
        coVerify {
            settingsRepository.saveReplayableItem(
                "content://media/external/audio/media/42",
                null,
                "AA:BB:CC:DD:EE:FF",
                true
            )
        }
        assertEquals("content://media/external/audio/media/42", viewModel.uiState.value.audioFilePath)
    }

    @Test
    fun `setAudioFilePath clears replayable item when selection is removed`() = runTest(testDispatcher) {
        coEvery { settingsRepository.saveAudioFilePath(null) } returns Unit
        coEvery { settingsRepository.clearReplayableItem() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAudioFilePath(null)
        advanceUntilIdle()

        coVerify { settingsRepository.clearReplayableItem() }
        assertEquals(null, viewModel.uiState.value.audioFilePath)
    }

    @Test
    fun `setAudioFilePath2 marks slot 2 as manual`() = runTest(testDispatcher) {
        coEvery { settingsRepository.saveAudioFilePath2(any()) } returns Unit
        coEvery { settingsRepository.setSongSlotSource(any(), any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAudioFilePath2("content://songs/manual-slot-2.mp3")
        advanceUntilIdle()

        coVerify { settingsRepository.setSongSlotSource(2, SongSlotSource.MANUAL) }
        assertEquals(SongSlotSource.MANUAL, viewModel.uiState.value.song2Source)
    }

    @Test
    fun `syncServerSongs requires a saved auth session`() = runTest(testDispatcher) {
        every { authSessionRepository.sessionFlow } returns flowOf(AuthSession())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncServerSongs()
        advanceUntilIdle()

        coVerify(exactly = 0) { customerSongSyncRepository.sync(any(), any()) }
        assertEquals("Please log in to sync server songs", viewModel.uiState.value.serverSyncMessage)
    }

    @Test
    fun `syncServerSongs updates audio slots from repository result`() = runTest(testDispatcher) {
        every {
            authSessionRepository.sessionFlow
        } returns flowOf(AuthSession(accessToken = "token-123", userId = "user-456"))
        every { settingsRepository.audioFilePathFlow } returns flowOf("file:///server-hello.mp3")
        every { settingsRepository.audioFilePath2Flow } returns flowOf("file:///server-goodbye.mp3")
        every { settingsRepository.song1SourceFlow } returns flowOf(SongSlotSource.SERVER)
        every { settingsRepository.song2SourceFlow } returns flowOf(SongSlotSource.SERVER)
        coEvery {
            customerSongSyncRepository.sync(
                AuthSession(accessToken = "token-123", userId = "user-456"),
                CustomerSongSyncTrigger.MANUAL
            )
        } returns CustomerSongSyncOutcome(
            trigger = CustomerSongSyncTrigger.MANUAL,
            hello = SlotSyncResult.UpdatedActive(
                cacheUri = "file:///server-hello.mp3",
                activeUri = "file:///server-hello.mp3"
            ),
            goodbye = SlotSyncResult.UpdatedActive(
                cacheUri = "file:///server-goodbye.mp3",
                activeUri = "file:///server-goodbye.mp3"
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncServerSongs()
        advanceUntilIdle()

        coVerify {
            customerSongSyncRepository.sync(
                AuthSession(accessToken = "token-123", userId = "user-456"),
                CustomerSongSyncTrigger.MANUAL
            )
        }
        assertEquals("file:///server-hello.mp3", viewModel.uiState.value.audioFilePath)
        assertEquals("file:///server-goodbye.mp3", viewModel.uiState.value.audioFilePath2)
        assertFalse(viewModel.uiState.value.isServerSyncing)
        assertEquals("Server songs updated", viewModel.uiState.value.serverSyncMessage)
    }

    @Test
    fun `refreshPairedDevices updates state with devices when Bluetooth enabled`() = runTest(testDispatcher) {
        every { bluetoothAdapterRepo.isBluetoothEnabled() } returns true
        val devices = listOf(
            BluetoothDeviceDomain("Car", "AA:BB:CC:DD:EE:FF"),
            BluetoothDeviceDomain("Phone", "11:22:33:44:55:66")
        )
        every { bluetoothAdapterRepo.getPairedDevices() } returns devices

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pairedDevices.size)
        assertEquals("Car", viewModel.uiState.value.pairedDevices[0].name)
    }

    @Test
    fun `refreshPairedDevices sets empty list when Bluetooth disabled`() = runTest(testDispatcher) {
        every { bluetoothAdapterRepo.bluetoothEnabledFlow() } returns flowOf(false)
        every { bluetoothAdapterRepo.isBluetoothEnabled() } returns false
        every { bluetoothAdapterRepo.getPairedDevices() } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.refreshPairedDevices()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.pairedDevices.size)
    }

    @Test
    fun `initial state has isBluetoothEnabled from flow`() = runTest(testDispatcher) {
        every { bluetoothAdapterRepo.bluetoothEnabledFlow() } returns flowOf(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(true, state.isBluetoothEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual playback shows pending until controller reports playing`() = runTest(testDispatcher) {
        val provider = RecordingMediaControllerProvider()
        val controller = mockk<MediaController>(relaxed = true)
        val listenerSlot = slot<Player.Listener>()
        every { controller.isPlaying } returns false
        every { controller.addListener(capture(listenerSlot)) } returns Unit

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        viewModel.onManualPlaybackStartRequested()
        assertTrue(viewModel.uiState.value.isPlaybackPending)
        assertFalse(viewModel.uiState.value.isPlaying)

        provider.dispatch(controller)
        runCurrent()

        verify { controller.play() }
        assertTrue(viewModel.uiState.value.isPlaybackPending)
        assertFalse(viewModel.uiState.value.isPlaying)

        listenerSlot.captured.onIsPlayingChanged(true)

        assertFalse(viewModel.uiState.value.isPlaybackPending)
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `manual playback starts immediately when AA skip gating is disabled even if AA device is connected`() =
        runTest(testDispatcher) {
            coEvery { bluetoothAdapterRepo.getConnectedA2dpAddresses() } returns listOf("AA:BB:CC:DD:EE:FF")
            coEvery { bluetoothAdapterRepo.isAndroidAutoDevice("AA:BB:CC:DD:EE:FF") } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onManualPlaybackStartRequested()

            assertTrue(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.showAndroidAutoWarning)
            assertFalse(viewModel.uiState.value.isWaitingForAndroidAutoConfirmation)
        }

    @Test
    fun `manual playback pending clears when controller is already playing`() = runTest(testDispatcher) {
        val provider = RecordingMediaControllerProvider()
        val controller = mockk<MediaController>(relaxed = true)
        every { controller.isPlaying } returns true
        every { controller.addListener(any()) } returns Unit

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        viewModel.onManualPlaybackStartRequested()
        assertTrue(viewModel.uiState.value.isPlaybackPending)

        provider.dispatch(controller)
        runCurrent()

        verify(exactly = 0) { controller.play() }
        assertFalse(viewModel.uiState.value.isPlaybackPending)
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `manual playback clears pending when controller initialization fails and retries on next tap`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            assertEquals(1, provider.initializeCount)

            viewModel.onManualPlaybackStartRequested()
            assertTrue(viewModel.uiState.value.isPlaybackPending)

            provider.dispatch(null)
            runCurrent()

            verify(exactly = 1) { manualPlaybackServiceController.stopPlayback() }
            assertFalse(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.isPlaying)

            viewModel.onManualPlaybackStartRequested()

            assertTrue(viewModel.uiState.value.isPlaybackPending)
            assertEquals(2, provider.initializeCount)
        }

    @Test
    fun `manual playback clears pending when controller play throws`() = runTest(testDispatcher) {
        val provider = RecordingMediaControllerProvider()
        val controller = mockk<MediaController>(relaxed = true)
        every { controller.isPlaying } returns false
        every { controller.addListener(any()) } returns Unit
        every { controller.play() } throws IllegalStateException("boom")

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        viewModel.onManualPlaybackStartRequested()
        provider.dispatch(controller)
        runCurrent()

        verify { controller.play() }
        verify(exactly = 1) { manualPlaybackServiceController.stopPlayback() }
        assertFalse(viewModel.uiState.value.isPlaybackPending)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `manual playback clears pending after timeout when playback never starts`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val controller = mockk<MediaController>(relaxed = true)
            every { controller.isPlaying } returns false
            every { controller.addListener(any()) } returns Unit

            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            viewModel.onManualPlaybackStartRequested()
            provider.dispatch(controller)
            runCurrent()

            assertTrue(viewModel.uiState.value.isPlaybackPending)

            advanceTimeBy(MANUAL_PLAYBACK_PENDING_TIMEOUT_MS)
            runCurrent()

            verify(exactly = 1) { manualPlaybackServiceController.stopPlayback() }
            assertFalse(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.isPlaying)
        }

    @Test
    fun `manual playback timeout releases a late controller from the abandoned init attempt`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val controller = mockk<MediaController>(relaxed = true)

            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            viewModel.onManualPlaybackStartRequested()
            advanceTimeBy(MANUAL_PLAYBACK_PENDING_TIMEOUT_MS)
            runCurrent()

            provider.dispatch(controller)
            runCurrent()

            verify(exactly = 1) { controller.release() }
            verify(exactly = 0) { controller.play() }
            assertFalse(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.isPlaying)
        }

    @Test
    fun `manual playback timeout resets init lock so next tap retries controller init`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            assertEquals(1, provider.initializeCount)

            viewModel.onManualPlaybackStartRequested()
            assertTrue(viewModel.uiState.value.isPlaybackPending)

            advanceTimeBy(MANUAL_PLAYBACK_PENDING_TIMEOUT_MS)
            runCurrent()

            assertFalse(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.isPlaying)

            viewModel.onManualPlaybackStartRequested()

            assertTrue(viewModel.uiState.value.isPlaybackPending)
            assertEquals(2, provider.initializeCount)
        }

    @Test
    fun `manual playback stop releases controller and allows a fresh later start`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val controller = mockk<MediaController>(relaxed = true)
            every { controller.isPlaying } returns true
            every { controller.addListener(any()) } returns Unit

            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            viewModel.onManualPlaybackStartRequested()
            provider.dispatch(controller)
            runCurrent()

            assertTrue(viewModel.uiState.value.isPlaying)
            assertFalse(viewModel.uiState.value.isPlaybackPending)

            viewModel.onManualPlaybackStopRequested()

            verify { controller.release() }
            assertFalse(viewModel.uiState.value.isPlaying)
            assertFalse(viewModel.uiState.value.isPlaybackPending)

            viewModel.onManualPlaybackStartRequested()

            assertTrue(viewModel.uiState.value.isPlaybackPending)
            assertEquals(2, provider.initializeCount)
        }

    @Test
    fun `logout stops playback cleanup clears auth and requests login navigation`() =
        runTest(testDispatcher) {
            val provider = RecordingMediaControllerProvider()
            val viewModel = createViewModel(provider)
            advanceUntilIdle()

            viewModel.onManualPlaybackStartRequested()
            assertTrue(viewModel.uiState.value.isPlaybackPending)

            viewModel.onLogoutRequested()
            advanceUntilIdle()

            verify(exactly = 1) { manualPlaybackServiceController.stopPlayback() }
            coVerify(exactly = 1) { authRepository.logout() }
            assertFalse(viewModel.uiState.value.isPlaybackPending)
            assertFalse(viewModel.uiState.value.isPlaying)
            assertFalse(viewModel.uiState.value.isLoggingOut)
            assertTrue(viewModel.uiState.value.navigateToLogin)
        }

    @Test
    fun `logout blocks new manual playback while cleanup is running`() =
        runTest(testDispatcher) {
            val logoutStarted = CompletableDeferred<Unit>()
            val finishLogout = CompletableDeferred<Unit>()
            coEvery { authRepository.logout() } coAnswers {
                logoutStarted.complete(Unit)
                finishLogout.await()
            }
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onLogoutRequested()
            runCurrent()
            assertTrue(logoutStarted.isCompleted)

            viewModel.onManualPlaybackStartRequested()
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoggingOut)
            assertFalse(viewModel.uiState.value.isPlaybackPending)

            finishLogout.complete(Unit)
            advanceUntilIdle()
        }

    private class RecordingMediaControllerProvider : MediaControllerProvider {
        private var callback: ((MediaController?) -> Unit)? = null
        var initializeCount: Int = 0
            private set

        override fun initialize(callback: (MediaController?) -> Unit) {
            initializeCount += 1
            this.callback = callback
        }

        fun dispatch(controller: MediaController?) {
            callback?.invoke(controller)
        }
    }
}
