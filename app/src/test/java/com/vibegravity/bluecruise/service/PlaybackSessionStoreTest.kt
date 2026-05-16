package com.vibegravity.bluecruise.service

import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStoreTest {

    @Test
    fun `store returns selected audio as replayable item with resume at zero`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val store = PlaybackSessionStore(settingsRepository)

        store.saveReplayableItem(
            audioUri = "content://media/external/audio/media/42",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true
        )

        val snapshot = store.snapshot()

        assertEquals("content://media/external/audio/media/42", snapshot.audioUri)
        assertEquals("Greeting track", snapshot.displayTitle)
        assertEquals("AA:BB:CC:DD:EE:FF", snapshot.targetMac)
        assertTrue(snapshot.aaEnabledForTarget)
        assertTrue(snapshot.resumeEligible)
        assertEquals(0L, snapshot.startPositionMs)
    }

    @Test
    fun `store clears replayable item when audio file is removed`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val store = PlaybackSessionStore(settingsRepository)

        store.saveReplayableItem(
            audioUri = "content://media/external/audio/media/42",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true
        )

        store.clearReplayableItem()

        val snapshot = store.snapshot()
        assertNull(snapshot.audioUri)
        assertNull(snapshot.displayTitle)
        assertNull(snapshot.targetMac)
        assertFalse(snapshot.aaEnabledForTarget)
        assertFalse(snapshot.resumeEligible)
        assertEquals(0L, snapshot.startPositionMs)
    }

    @Test
    fun `store disables resume eligibility when auto play is turned off`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val store = PlaybackSessionStore(settingsRepository)

        settingsRepository.autoPlayEnabledFlow.value = false
        store.saveReplayableItem(
            audioUri = "content://media/external/audio/media/42",
            displayTitle = "Greeting track",
            targetMac = "AA:BB:CC:DD:EE:FF",
            aaEnabledForTarget = true
        )

        val snapshot = store.snapshot()

        assertEquals("content://media/external/audio/media/42", snapshot.audioUri)
        assertFalse(snapshot.resumeEligible)
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val targetMacFlow: Flow<String?> = MutableStateFlow(null)
        override val autoPlayEnabledFlow = MutableStateFlow(true)
        override val maxVolumeEnabledFlow: Flow<Boolean> = MutableStateFlow(false)
        override val connectionStartDelaySecondsFlow: Flow<Int> = MutableStateFlow(0)
        override val keepAppAliveFlow: Flow<Boolean> = MutableStateFlow(false)
        override val audioFilePathFlow: Flow<String?> = MutableStateFlow(null)
        override val audioFilePath2Flow: Flow<String?> = MutableStateFlow(null)
        override val floatingBubbleEnabledFlow: Flow<Boolean> = MutableStateFlow(false)
        override val routingTierFlow: Flow<Int> = MutableStateFlow(1)
        override val autoStartDismissedFlow: Flow<Boolean> = MutableStateFlow(false)
        override val skipAutoPlayWhenAaConnectingFlow: Flow<Boolean> = MutableStateFlow(false)
        override val autoPlayOnAndroidAutoFlow: Flow<Boolean> = MutableStateFlow(false)
        override val aftermarketAndroidAutoTargetFlow: Flow<Boolean> = MutableStateFlow(false)
        override val simulateAndroidAutoFlow: Flow<Boolean> = MutableStateFlow(false)

        override val replayAudioUriFlow = MutableStateFlow<String?>(null)
        override val replayAudioTitleFlow = MutableStateFlow<String?>(null)
        override val replayTargetMacFlow = MutableStateFlow<String?>(null)
        override val replayAaEnabledForTargetFlow = MutableStateFlow(false)
        override val replayResumeEligibleFlow = MutableStateFlow(false)
        override val replayPreparedAtElapsedMsFlow = MutableStateFlow<Long?>(null)
        override val serverAudioFilePath1Flow: Flow<String?> = MutableStateFlow(null)
        override val serverAudioFilePath2Flow: Flow<String?> = MutableStateFlow(null)
        override val serverAudioTitle1Flow: Flow<String?> = MutableStateFlow(null)
        override val serverAudioTitle2Flow: Flow<String?> = MutableStateFlow(null)
        override val song1SourceFlow: Flow<SongSlotSource> = MutableStateFlow(SongSlotSource.SERVER)
        override val song2SourceFlow: Flow<SongSlotSource> = MutableStateFlow(SongSlotSource.SERVER)
        override val lastServerSyncUserIdFlow: Flow<String?> = MutableStateFlow(null)
        override val lastServerSyncAtEpochMsFlow: Flow<Long?> = MutableStateFlow(null)

        override suspend fun saveTargetMac(mac: String) = Unit
        override suspend fun setAutoPlayEnabled(enabled: Boolean) = Unit
        override suspend fun setMaxVolumeEnabled(enabled: Boolean) = Unit
        override suspend fun setConnectionStartDelaySeconds(seconds: Int) = Unit
        override suspend fun setKeepAppAlive(enabled: Boolean) = Unit
        override suspend fun saveAudioFilePath(path: String?) = Unit
        override suspend fun saveAudioFilePath2(path: String?) = Unit
        override suspend fun setFloatingBubbleEnabled(enabled: Boolean) = Unit
        override suspend fun saveRoutingTier(tier: Int) = Unit
        override suspend fun setAutoStartDismissed(dismissed: Boolean) = Unit
        override suspend fun setSkipAutoPlayWhenAaConnecting(enabled: Boolean) = Unit
        override suspend fun setAutoPlayOnAndroidAuto(enabled: Boolean) = Unit
        override suspend fun setAftermarketAndroidAutoTarget(enabled: Boolean) = Unit
        override suspend fun setSimulateAndroidAuto(enabled: Boolean) = Unit

        override suspend fun saveReplayableItem(
            audioUri: String,
            displayTitle: String?,
            targetMac: String?,
            aaEnabledForTarget: Boolean
        ) {
            replayAudioUriFlow.value = audioUri
            replayAudioTitleFlow.value = displayTitle
            replayTargetMacFlow.value = targetMac
            replayAaEnabledForTargetFlow.value = aaEnabledForTarget
            replayResumeEligibleFlow.value = true
        }

        override suspend fun clearReplayableItem() {
            replayAudioUriFlow.value = null
            replayAudioTitleFlow.value = null
            replayTargetMacFlow.value = null
            replayAaEnabledForTargetFlow.value = false
            replayResumeEligibleFlow.value = false
            replayPreparedAtElapsedMsFlow.value = null
        }

        override suspend fun setReplayResumeEligible(eligible: Boolean) {
            replayResumeEligibleFlow.value = eligible
        }

        override suspend fun setReplayPreparedAtElapsedMs(elapsedMs: Long?) {
            replayPreparedAtElapsedMsFlow.value = elapsedMs
        }

        override suspend fun saveServerAudioSlot(slot: Int, path: String?, title: String?) = Unit

        override suspend fun setSongSlotSource(slot: Int, source: SongSlotSource) = Unit

        override suspend fun setLastServerSync(userId: String, atEpochMs: Long) = Unit

        override suspend fun clearUserScopedData() {
            clearReplayableItem()
        }
    }
}
