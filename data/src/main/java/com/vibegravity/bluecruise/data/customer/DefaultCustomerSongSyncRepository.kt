package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncOutcome
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import com.vibegravity.bluecruise.domain.customer.SlotSyncResult
import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

class DefaultCustomerSongSyncRepository @Inject constructor(
    private val downloader: CustomerSongDownloader,
    private val fileStore: CustomerSongFileStore,
    private val settingsRepository: SettingsRepository
) : CustomerSongSyncRepository {

    override suspend fun sync(
        session: AuthSession,
        trigger: CustomerSongSyncTrigger
    ): CustomerSongSyncOutcome {
        val hello = syncSlot(session, CustomerSongType.HELLO, trigger)
        val goodbye = syncSlot(session, CustomerSongType.GOODBYE, trigger)

        if (hello !is SlotSyncResult.Failed || goodbye !is SlotSyncResult.Failed) {
            settingsRepository.setLastServerSync(session.userId, System.currentTimeMillis())
        }

        return CustomerSongSyncOutcome(
            trigger = trigger,
            hello = hello,
            goodbye = goodbye
        )
    }

    private suspend fun syncSlot(
        session: AuthSession,
        type: CustomerSongType,
        trigger: CustomerSongSyncTrigger
    ): SlotSyncResult {
        val slot = type.slot
        val activeUri = currentActiveUri(slot)
        val currentSource = currentSongSlotSource(slot)

        return try {
            val response = downloader.downloadSong(
                accessToken = session.accessToken,
                userId = session.userId,
                type = type
            )
            val cachedUri = fileStore.write(
                userId = session.userId,
                type = type,
                bytes = response.bytes,
                fileNameHint = response.fileName
            )
            val displayTitle = resolveDisplayTitle(type, response.fileName)
            settingsRepository.saveServerAudioSlot(slot, cachedUri, displayTitle)

            val shouldOverwriteActiveSlot = trigger == CustomerSongSyncTrigger.MANUAL ||
                activeUri.isNullOrBlank() ||
                currentSource == SongSlotSource.SERVER

            if (shouldOverwriteActiveSlot) {
                saveActiveUri(slot, cachedUri)
                settingsRepository.setSongSlotSource(slot, SongSlotSource.SERVER)
                if (slot == 1) {
                    updateReplayableItem(cachedUri, displayTitle)
                }
                SlotSyncResult.UpdatedActive(
                    cacheUri = cachedUri,
                    activeUri = cachedUri
                )
            } else {
                SlotSyncResult.UpdatedCacheOnly(
                    cacheUri = cachedUri,
                    activeUri = activeUri
                )
            }
        } catch (throwable: Throwable) {
            SlotSyncResult.Failed(
                message = throwable.message ?: "Unable to sync ${type.wireValue}",
                activeUri = activeUri
            )
        }
    }

    private suspend fun currentActiveUri(slot: Int): String? {
        return when (slot) {
            2 -> settingsRepository.audioFilePath2Flow.firstOrNull()
            else -> settingsRepository.audioFilePathFlow.firstOrNull()
        }
    }

    private suspend fun currentSongSlotSource(slot: Int): SongSlotSource {
        return when (slot) {
            2 -> settingsRepository.song2SourceFlow.firstOrNull()
            else -> settingsRepository.song1SourceFlow.firstOrNull()
        } ?: SongSlotSource.SERVER
    }

    private suspend fun saveActiveUri(slot: Int, uri: String) {
        when (slot) {
            2 -> settingsRepository.saveAudioFilePath2(uri)
            else -> settingsRepository.saveAudioFilePath(uri)
        }
    }

    private suspend fun updateReplayableItem(activeUri: String, displayTitle: String) {
        val targetMac = settingsRepository.targetMacFlow.firstOrNull()
        val aaEnabledForTarget = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
        settingsRepository.saveReplayableItem(
            audioUri = activeUri,
            displayTitle = displayTitle,
            targetMac = targetMac,
            aaEnabledForTarget = aaEnabledForTarget
        )
    }

    private fun resolveDisplayTitle(type: CustomerSongType, fileName: String?): String {
        val normalized = fileName
            ?.substringAfterLast('/')
            ?.let { raw -> raw.substringBeforeLast('.', missingDelimiterValue = raw) }
            ?.replace('_', ' ')
            ?.trim()
            .orEmpty()
        if (normalized.isNotEmpty()) return normalized

        return type.wireValue
    }
}
