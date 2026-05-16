package com.vibegravity.bluecruise.service

import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class PlaybackSessionStore @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun saveReplayableItem(
        audioUri: String,
        displayTitle: String?,
        targetMac: String?,
        aaEnabledForTarget: Boolean
    ) {
        settingsRepository.saveReplayableItem(
            audioUri = audioUri,
            displayTitle = displayTitle,
            targetMac = targetMac,
            aaEnabledForTarget = aaEnabledForTarget
        )
    }

    suspend fun clearReplayableItem() {
        settingsRepository.clearReplayableItem()
    }

    suspend fun setReplayResumeEligible(eligible: Boolean) {
        settingsRepository.setReplayResumeEligible(eligible)
    }

    suspend fun setReplayPreparedAtElapsedMs(elapsedMs: Long?) {
        settingsRepository.setReplayPreparedAtElapsedMs(elapsedMs)
    }

    suspend fun snapshot(): PlaybackSessionSnapshot {
        val replayAudioUri = settingsRepository.replayAudioUriFlow.firstOrNull()
        val fallbackAudioUri = settingsRepository.audioFilePathFlow.firstOrNull()
        val replayTargetMac = settingsRepository.replayTargetMacFlow.firstOrNull()
        val fallbackTargetMac = settingsRepository.targetMacFlow.firstOrNull()
        val replayAaEnabled = settingsRepository.replayAaEnabledForTargetFlow.firstOrNull()
        val fallbackAaEnabled = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull()
        val replayEligible = settingsRepository.replayResumeEligibleFlow.firstOrNull()
        val autoPlayEnabled = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true

        return PlaybackSessionSnapshot(
            audioUri = replayAudioUri ?: fallbackAudioUri,
            displayTitle = settingsRepository.replayAudioTitleFlow.firstOrNull(),
            targetMac = replayTargetMac ?: fallbackTargetMac,
            aaEnabledForTarget = replayAaEnabled ?: fallbackAaEnabled ?: false,
            resumeEligible = autoPlayEnabled && (replayEligible ?: !fallbackAudioUri.isNullOrBlank()),
            preparedAtElapsedMs = settingsRepository.replayPreparedAtElapsedMsFlow.firstOrNull()
        )
    }
}
