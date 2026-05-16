package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import com.vibegravity.bluecruise.domain.customer.SlotSyncResult
import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCustomerSongSyncRepositoryTest {

    @Test
    fun `login sync keeps manual slot active while refreshing server cache`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            audioFilePath = "file:///manual-song-1.mp3",
            audioFilePath2 = null,
            song1Source = SongSlotSource.MANUAL,
            song2Source = SongSlotSource.SERVER
        )
        val repository = DefaultCustomerSongSyncRepository(
            downloader = FakeDownloader(),
            fileStore = FakeFileStore(),
            settingsRepository = settingsRepository
        )

        val outcome = repository.sync(
            session = AuthSession(accessToken = "token-123", userId = "user-456"),
            trigger = CustomerSongSyncTrigger.LOGIN
        )

        assertEquals("file:///manual-song-1.mp3", settingsRepository.audioFilePathFlow.first())
        assertEquals("file:///server-hello.mp3", settingsRepository.serverAudioFilePath1Flow.first())
        assertEquals("file:///server-goodbye.mp3", settingsRepository.audioFilePath2Flow.first())
        assertTrue(outcome.hello is SlotSyncResult.UpdatedCacheOnly)
        assertTrue(outcome.goodbye is SlotSyncResult.UpdatedActive)
    }

    @Test
    fun `manual sync overwrites manual slots and flips sources to server`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            audioFilePath = "file:///manual-song-1.mp3",
            audioFilePath2 = "file:///manual-song-2.mp3",
            song1Source = SongSlotSource.MANUAL,
            song2Source = SongSlotSource.MANUAL
        )
        val repository = DefaultCustomerSongSyncRepository(
            downloader = FakeDownloader(),
            fileStore = FakeFileStore(),
            settingsRepository = settingsRepository
        )

        val outcome = repository.sync(
            session = AuthSession(accessToken = "token-123", userId = "user-456"),
            trigger = CustomerSongSyncTrigger.MANUAL
        )

        assertEquals("file:///server-hello.mp3", settingsRepository.audioFilePathFlow.first())
        assertEquals("file:///server-goodbye.mp3", settingsRepository.audioFilePath2Flow.first())
        assertEquals(SongSlotSource.SERVER, settingsRepository.song1SourceFlow.first())
        assertEquals(SongSlotSource.SERVER, settingsRepository.song2SourceFlow.first())
        assertTrue(outcome.hello is SlotSyncResult.UpdatedActive)
        assertTrue(outcome.goodbye is SlotSyncResult.UpdatedActive)
    }

    @Test
    fun `server sync stores a friendly replayable title for primary slot`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            audioFilePath = null,
            audioFilePath2 = null,
            song1Source = SongSlotSource.SERVER,
            song2Source = SongSlotSource.SERVER
        )
        val repository = DefaultCustomerSongSyncRepository(
            downloader = FakeDownloader(),
            fileStore = FakeFileStore(),
            settingsRepository = settingsRepository
        )

        repository.sync(
            session = AuthSession(accessToken = "token-123", userId = "user-456"),
            trigger = CustomerSongSyncTrigger.LOGIN
        )

        assertEquals("hello", settingsRepository.replayAudioTitleFlow.first())
    }

    private class FakeDownloader : CustomerSongDownloader {
        override suspend fun downloadSong(
            accessToken: String,
            userId: String,
            type: CustomerSongType
        ): CustomerSongDownloadResponse {
            return CustomerSongDownloadResponse(
                bytes = when (type) {
                    CustomerSongType.HELLO -> byteArrayOf(1, 2, 3)
                    CustomerSongType.GOODBYE -> byteArrayOf(4, 5, 6)
                },
                fileName = "${type.wireValue}.mp3",
                contentType = "audio/mpeg"
            )
        }
    }

    private class FakeFileStore : CustomerSongFileStore {
        override fun write(
            userId: String,
            type: CustomerSongType,
            bytes: ByteArray,
            fileNameHint: String?
        ): String {
            return when (type) {
                CustomerSongType.HELLO -> "file:///server-hello.mp3"
                CustomerSongType.GOODBYE -> "file:///server-goodbye.mp3"
            }
        }

        override fun clearAll() = Unit
    }

    private class FakeSettingsRepository(
        audioFilePath: String?,
        audioFilePath2: String?,
        song1Source: SongSlotSource,
        song2Source: SongSlotSource
    ) : SettingsRepository {
        override val targetMacFlow: Flow<String?> = MutableStateFlow("AA:BB:CC:DD:EE:FF")
        override val autoPlayEnabledFlow: Flow<Boolean> = MutableStateFlow(true)
        override val maxVolumeEnabledFlow: Flow<Boolean> = MutableStateFlow(false)
        override val connectionStartDelaySecondsFlow: Flow<Int> = MutableStateFlow(0)
        override val keepAppAliveFlow: Flow<Boolean> = MutableStateFlow(false)
        override val audioFilePathFlow = MutableStateFlow(audioFilePath)
        override val audioFilePath2Flow = MutableStateFlow(audioFilePath2)
        override val floatingBubbleEnabledFlow: Flow<Boolean> = MutableStateFlow(false)
        override val routingTierFlow: Flow<Int> = MutableStateFlow(1)
        override val autoStartDismissedFlow: Flow<Boolean> = MutableStateFlow(false)
        override val skipAutoPlayWhenAaConnectingFlow: Flow<Boolean> = MutableStateFlow(false)
        override val autoPlayOnAndroidAutoFlow: Flow<Boolean> = MutableStateFlow(true)
        override val aftermarketAndroidAutoTargetFlow: Flow<Boolean> = MutableStateFlow(false)
        override val simulateAndroidAutoFlow: Flow<Boolean> = MutableStateFlow(false)
        override val replayAudioUriFlow = MutableStateFlow<String?>(null)
        override val replayAudioTitleFlow = MutableStateFlow<String?>(null)
        override val replayTargetMacFlow = MutableStateFlow<String?>(null)
        override val replayAaEnabledForTargetFlow: Flow<Boolean> = MutableStateFlow(false)
        override val replayResumeEligibleFlow: Flow<Boolean> = MutableStateFlow(false)
        override val replayPreparedAtElapsedMsFlow: Flow<Long?> = MutableStateFlow(null)
        override val serverAudioFilePath1Flow = MutableStateFlow<String?>(null)
        override val serverAudioFilePath2Flow = MutableStateFlow<String?>(null)
        override val serverAudioTitle1Flow = MutableStateFlow<String?>(null)
        override val serverAudioTitle2Flow = MutableStateFlow<String?>(null)
        override val song1SourceFlow = MutableStateFlow(song1Source)
        override val song2SourceFlow = MutableStateFlow(song2Source)
        override val lastServerSyncUserIdFlow = MutableStateFlow<String?>(null)
        override val lastServerSyncAtEpochMsFlow = MutableStateFlow<Long?>(null)

        override suspend fun saveTargetMac(mac: String) = Unit
        override suspend fun setAutoPlayEnabled(enabled: Boolean) = Unit
        override suspend fun setMaxVolumeEnabled(enabled: Boolean) = Unit
        override suspend fun setConnectionStartDelaySeconds(seconds: Int) = Unit
        override suspend fun setKeepAppAlive(enabled: Boolean) = Unit
        override suspend fun saveAudioFilePath(path: String?) {
            audioFilePathFlow.value = path
        }

        override suspend fun saveAudioFilePath2(path: String?) {
            audioFilePath2Flow.value = path
        }

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
        }

        override suspend fun clearReplayableItem() {
            replayAudioUriFlow.value = null
            replayAudioTitleFlow.value = null
            replayTargetMacFlow.value = null
        }

        override suspend fun setReplayResumeEligible(eligible: Boolean) = Unit
        override suspend fun setReplayPreparedAtElapsedMs(elapsedMs: Long?) = Unit
        override suspend fun saveServerAudioSlot(
            slot: Int,
            path: String?,
            title: String?
        ) {
            when (slot) {
                1 -> {
                    serverAudioFilePath1Flow.value = path
                    serverAudioTitle1Flow.value = title
                }
                2 -> {
                    serverAudioFilePath2Flow.value = path
                    serverAudioTitle2Flow.value = title
                }
            }
        }

        override suspend fun setSongSlotSource(slot: Int, source: SongSlotSource) {
            when (slot) {
                1 -> song1SourceFlow.value = source
                2 -> song2SourceFlow.value = source
            }
        }

        override suspend fun setLastServerSync(userId: String, atEpochMs: Long) {
            lastServerSyncUserIdFlow.value = userId
            lastServerSyncAtEpochMsFlow.value = atEpochMs
        }

        override suspend fun clearUserScopedData() {
            audioFilePathFlow.value = null
            audioFilePath2Flow.value = null
            serverAudioFilePath1Flow.value = null
            serverAudioFilePath2Flow.value = null
            serverAudioTitle1Flow.value = null
            serverAudioTitle2Flow.value = null
            song1SourceFlow.value = SongSlotSource.SERVER
            song2SourceFlow.value = SongSlotSource.SERVER
            lastServerSyncUserIdFlow.value = null
            lastServerSyncAtEpochMsFlow.value = null
            clearReplayableItem()
        }
    }
}
