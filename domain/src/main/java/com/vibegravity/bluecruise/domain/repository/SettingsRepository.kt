package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val targetMacFlow: Flow<String?>
    val autoPlayEnabledFlow: Flow<Boolean>
    val maxVolumeEnabledFlow: Flow<Boolean>
    val connectionStartDelaySecondsFlow: Flow<Int>
    val keepAppAliveFlow: Flow<Boolean>
    val audioFilePathFlow: Flow<String?>
    val audioFilePath2Flow: Flow<String?>
    val floatingBubbleEnabledFlow: Flow<Boolean>
    val routingTierFlow: Flow<Int>
    val autoStartDismissedFlow: Flow<Boolean>
    val skipAutoPlayWhenAaConnectingFlow: Flow<Boolean>
    val autoPlayOnAndroidAutoFlow: Flow<Boolean>
    val aftermarketAndroidAutoTargetFlow: Flow<Boolean>
    val simulateAndroidAutoFlow: Flow<Boolean>
    val replayAudioUriFlow: Flow<String?>
    val replayAudioTitleFlow: Flow<String?>
    val replayTargetMacFlow: Flow<String?>
    val replayAaEnabledForTargetFlow: Flow<Boolean>
    val replayResumeEligibleFlow: Flow<Boolean>
    val replayPreparedAtElapsedMsFlow: Flow<Long?>
    val serverAudioFilePath1Flow: Flow<String?>
    val serverAudioFilePath2Flow: Flow<String?>
    val serverAudioTitle1Flow: Flow<String?>
    val serverAudioTitle2Flow: Flow<String?>
    val song1SourceFlow: Flow<SongSlotSource>
    val song2SourceFlow: Flow<SongSlotSource>
    val lastServerSyncUserIdFlow: Flow<String?>
    val lastServerSyncAtEpochMsFlow: Flow<Long?>

    suspend fun saveTargetMac(mac: String)
    suspend fun setAutoPlayEnabled(enabled: Boolean)
    suspend fun setMaxVolumeEnabled(enabled: Boolean)
    suspend fun setConnectionStartDelaySeconds(seconds: Int)
    suspend fun setKeepAppAlive(enabled: Boolean)
    suspend fun saveAudioFilePath(path: String?)
    suspend fun saveAudioFilePath2(path: String?)
    suspend fun setFloatingBubbleEnabled(enabled: Boolean)
    suspend fun saveRoutingTier(tier: Int)
    suspend fun setAutoStartDismissed(dismissed: Boolean)
    suspend fun setSkipAutoPlayWhenAaConnecting(enabled: Boolean)
    suspend fun setAutoPlayOnAndroidAuto(enabled: Boolean)
    suspend fun setAftermarketAndroidAutoTarget(enabled: Boolean)
    suspend fun setSimulateAndroidAuto(enabled: Boolean)
    suspend fun saveReplayableItem(
        audioUri: String,
        displayTitle: String?,
        targetMac: String?,
        aaEnabledForTarget: Boolean
    )
    suspend fun clearReplayableItem()
    suspend fun setReplayResumeEligible(eligible: Boolean)
    suspend fun setReplayPreparedAtElapsedMs(elapsedMs: Long?)
    suspend fun saveServerAudioSlot(slot: Int, path: String?, title: String?)
    suspend fun setSongSlotSource(slot: Int, source: SongSlotSource)
    suspend fun setLastServerSync(userId: String, atEpochMs: Long)
    suspend fun clearUserScopedData()
}

