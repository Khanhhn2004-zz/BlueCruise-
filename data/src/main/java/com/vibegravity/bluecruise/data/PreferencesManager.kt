package com.vibegravity.bluecruise.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluecruise_prefs")

@Singleton
class PreferencesManager @Inject constructor(private val context: Context) : SettingsRepository {

    companion object {
        val TARGET_MAC = stringPreferencesKey("target_mac")
        val AUTO_PLAY_ENABLED = booleanPreferencesKey("auto_play_enabled")
        val MAX_VOLUME_ENABLED = booleanPreferencesKey("max_volume_enabled")
        val CONNECTION_START_DELAY_SECONDS = intPreferencesKey("connection_start_delay_seconds")
        val KEEP_APP_ALIVE = booleanPreferencesKey("keep_app_alive")
        val AUDIO_FILE_PATH = stringPreferencesKey("audio_file_path")
        val AUDIO_FILE_PATH_2 = stringPreferencesKey("audio_file_path_2")
        val FLOATING_BUBBLE_ENABLED = booleanPreferencesKey("floating_bubble_enabled")
        val ROUTING_TIER = intPreferencesKey("routing_tier")
        val AUTO_START_DISMISSED = booleanPreferencesKey("auto_start_dismissed")
        // New: Skip auto-play when Android Auto is newly connected (before user confirms)
        val SKIP_AUTO_PLAY_WHEN_AA_CONNECTING = booleanPreferencesKey("skip_auto_play_when_aa_connecting")
        // New: Auto-play when Android Auto is connected
        val AUTO_PLAY_ON_ANDROID_AUTO = booleanPreferencesKey("auto_play_on_android_auto")
        val AFTERMARKET_ANDROID_AUTO_TARGET_MACS = stringSetPreferencesKey("aftermarket_android_auto_target_macs")
        // Debug: Simulate Android Auto connection (for testing)
        val SIMULATE_ANDROID_AUTO = booleanPreferencesKey("simulate_android_auto")
        val REPLAY_AUDIO_URI = stringPreferencesKey("replay_audio_uri")
        val REPLAY_AUDIO_TITLE = stringPreferencesKey("replay_audio_title")
        val REPLAY_TARGET_MAC = stringPreferencesKey("replay_target_mac")
        val REPLAY_AA_ENABLED_FOR_TARGET = booleanPreferencesKey("replay_aa_enabled_for_target")
        val REPLAY_RESUME_ELIGIBLE = booleanPreferencesKey("replay_resume_eligible")
        val REPLAY_PREPARED_AT_ELAPSED_MS = longPreferencesKey("replay_prepared_at_elapsed_ms")
        val SERVER_AUDIO_FILE_PATH_1 = stringPreferencesKey("server_audio_file_path_1")
        val SERVER_AUDIO_FILE_PATH_2 = stringPreferencesKey("server_audio_file_path_2")
        val SERVER_AUDIO_TITLE_1 = stringPreferencesKey("server_audio_title_1")
        val SERVER_AUDIO_TITLE_2 = stringPreferencesKey("server_audio_title_2")
        val SONG_1_SOURCE = stringPreferencesKey("song_1_source")
        val SONG_2_SOURCE = stringPreferencesKey("song_2_source")
        val LAST_SERVER_SYNC_USER_ID = stringPreferencesKey("last_server_sync_user_id")
        val LAST_SERVER_SYNC_AT_EPOCH_MS = longPreferencesKey("last_server_sync_at_epoch_ms")
    }

    override val targetMacFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TARGET_MAC]
    }

    override val autoPlayEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_PLAY_ENABLED] ?: true
    }

    override val maxVolumeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MAX_VOLUME_ENABLED] ?: false
    }

    override val connectionStartDelaySecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        (preferences[CONNECTION_START_DELAY_SECONDS] ?: 0).coerceIn(0, 10)
    }

    override val keepAppAliveFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEEP_APP_ALIVE] ?: false
    }

    override val audioFilePathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUDIO_FILE_PATH]
    }

    override val audioFilePath2Flow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUDIO_FILE_PATH_2]
    }

    override val floatingBubbleEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_BUBBLE_ENABLED] ?: false
    }

    override val routingTierFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ROUTING_TIER] ?: 1
    }

    override val autoStartDismissedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_START_DISMISSED] ?: false
    }

    // New: Skip auto-play when AA is connecting (not yet confirmed by user)
    override val skipAutoPlayWhenAaConnectingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SKIP_AUTO_PLAY_WHEN_AA_CONNECTING] ?: false
    }

    // New: Auto-play when Android Auto is connected
    override val autoPlayOnAndroidAutoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_PLAY_ON_ANDROID_AUTO] ?: false
    }

    override val aftermarketAndroidAutoTargetFlow: Flow<Boolean> = combine(
        targetMacFlow,
        context.dataStore.data.map { preferences ->
            preferences[AFTERMARKET_ANDROID_AUTO_TARGET_MACS] ?: emptySet()
        }
    ) { targetMac, aftermarketTargetMacs ->
        val normalizedTargetMac = targetMac?.takeIf { it.isNotBlank() }?.let(::normalizeMac)
        normalizedTargetMac != null && aftermarketTargetMacs.contains(normalizedTargetMac)
    }

    // Debug: Simulate Android Auto connection (for testing)
    override val simulateAndroidAutoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SIMULATE_ANDROID_AUTO] ?: false
    }

    override val replayAudioUriFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_AUDIO_URI]
    }

    override val replayAudioTitleFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_AUDIO_TITLE]
    }

    override val replayTargetMacFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_TARGET_MAC]
    }

    override val replayAaEnabledForTargetFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_AA_ENABLED_FOR_TARGET] ?: false
    }

    override val replayResumeEligibleFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_RESUME_ELIGIBLE] ?: false
    }

    override val replayPreparedAtElapsedMsFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_PREPARED_AT_ELAPSED_MS]
    }

    override val serverAudioFilePath1Flow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_AUDIO_FILE_PATH_1]
    }

    override val serverAudioFilePath2Flow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_AUDIO_FILE_PATH_2]
    }

    override val serverAudioTitle1Flow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_AUDIO_TITLE_1]
    }

    override val serverAudioTitle2Flow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_AUDIO_TITLE_2]
    }

    override val song1SourceFlow: Flow<SongSlotSource> = context.dataStore.data.map { preferences ->
        preferences[SONG_1_SOURCE]?.let(::parseSongSlotSource)
            ?: if (preferences[AUDIO_FILE_PATH].isNullOrBlank()) {
                SongSlotSource.SERVER
            } else {
                SongSlotSource.MANUAL
            }
    }

    override val song2SourceFlow: Flow<SongSlotSource> = context.dataStore.data.map { preferences ->
        preferences[SONG_2_SOURCE]?.let(::parseSongSlotSource)
            ?: if (preferences[AUDIO_FILE_PATH_2].isNullOrBlank()) {
                SongSlotSource.SERVER
            } else {
                SongSlotSource.MANUAL
            }
    }

    override val lastServerSyncUserIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SERVER_SYNC_USER_ID]
    }

    override val lastServerSyncAtEpochMsFlow: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SERVER_SYNC_AT_EPOCH_MS]
    }

    override suspend fun saveTargetMac(mac: String) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_MAC] = mac
        }
    }

    override suspend fun setAutoPlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PLAY_ENABLED] = enabled
        }
    }

    override suspend fun setMaxVolumeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MAX_VOLUME_ENABLED] = enabled
        }
    }

    override suspend fun setConnectionStartDelaySeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONNECTION_START_DELAY_SECONDS] = seconds.coerceIn(0, 10)
        }
    }

    override suspend fun setKeepAppAlive(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEEP_APP_ALIVE] = enabled
        }
    }

    override suspend fun saveAudioFilePath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(AUDIO_FILE_PATH)
            } else {
                preferences[AUDIO_FILE_PATH] = path
            }
        }
    }

    override suspend fun saveAudioFilePath2(path: String?) {
        context.dataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(AUDIO_FILE_PATH_2)
            } else {
                preferences[AUDIO_FILE_PATH_2] = path
            }
        }
    }

    override suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_BUBBLE_ENABLED] = enabled
        }
    }

    override suspend fun saveRoutingTier(tier: Int) {
        context.dataStore.edit { preferences ->
            preferences[ROUTING_TIER] = tier
        }
    }

    override suspend fun setAutoStartDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_DISMISSED] = dismissed
        }
    }

    override suspend fun setSkipAutoPlayWhenAaConnecting(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SKIP_AUTO_PLAY_WHEN_AA_CONNECTING] = enabled
        }
    }

    override suspend fun setAutoPlayOnAndroidAuto(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PLAY_ON_ANDROID_AUTO] = enabled
        }
    }

    override suspend fun setAftermarketAndroidAutoTarget(enabled: Boolean) {
        val targetMac = targetMacFlow.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        val normalizedTargetMac = normalizeMac(targetMac)
        context.dataStore.edit { preferences ->
            val updatedTargetMacs =
                (preferences[AFTERMARKET_ANDROID_AUTO_TARGET_MACS] ?: emptySet()).toMutableSet()
            if (enabled) {
                updatedTargetMacs.add(normalizedTargetMac)
            } else {
                updatedTargetMacs.remove(normalizedTargetMac)
            }

            if (updatedTargetMacs.isEmpty()) {
                preferences.remove(AFTERMARKET_ANDROID_AUTO_TARGET_MACS)
            } else {
                preferences[AFTERMARKET_ANDROID_AUTO_TARGET_MACS] = updatedTargetMacs
            }
        }
    }

    override suspend fun setSimulateAndroidAuto(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SIMULATE_ANDROID_AUTO] = enabled
        }
    }

    override suspend fun saveReplayableItem(
        audioUri: String,
        displayTitle: String?,
        targetMac: String?,
        aaEnabledForTarget: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[REPLAY_AUDIO_URI] = audioUri
            if (displayTitle == null) {
                preferences.remove(REPLAY_AUDIO_TITLE)
            } else {
                preferences[REPLAY_AUDIO_TITLE] = displayTitle
            }
            if (targetMac == null) {
                preferences.remove(REPLAY_TARGET_MAC)
            } else {
                preferences[REPLAY_TARGET_MAC] = targetMac
            }
            preferences[REPLAY_AA_ENABLED_FOR_TARGET] = aaEnabledForTarget
            preferences[REPLAY_RESUME_ELIGIBLE] = true
        }
    }

    override suspend fun clearReplayableItem() {
        context.dataStore.edit { preferences ->
            preferences.remove(REPLAY_AUDIO_URI)
            preferences.remove(REPLAY_AUDIO_TITLE)
            preferences.remove(REPLAY_TARGET_MAC)
            preferences[REPLAY_AA_ENABLED_FOR_TARGET] = false
            preferences[REPLAY_RESUME_ELIGIBLE] = false
            preferences.remove(REPLAY_PREPARED_AT_ELAPSED_MS)
        }
    }

    override suspend fun setReplayResumeEligible(eligible: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REPLAY_RESUME_ELIGIBLE] = eligible
        }
    }

    override suspend fun setReplayPreparedAtElapsedMs(elapsedMs: Long?) {
        context.dataStore.edit { preferences ->
            if (elapsedMs == null) {
                preferences.remove(REPLAY_PREPARED_AT_ELAPSED_MS)
            } else {
                preferences[REPLAY_PREPARED_AT_ELAPSED_MS] = elapsedMs
            }
        }
    }

    override suspend fun saveServerAudioSlot(slot: Int, path: String?, title: String?) {
        context.dataStore.edit { preferences ->
            when (slot) {
                2 -> {
                    if (path == null) {
                        preferences.remove(SERVER_AUDIO_FILE_PATH_2)
                    } else {
                        preferences[SERVER_AUDIO_FILE_PATH_2] = path
                    }
                    if (title == null) {
                        preferences.remove(SERVER_AUDIO_TITLE_2)
                    } else {
                        preferences[SERVER_AUDIO_TITLE_2] = title
                    }
                }

                else -> {
                    if (path == null) {
                        preferences.remove(SERVER_AUDIO_FILE_PATH_1)
                    } else {
                        preferences[SERVER_AUDIO_FILE_PATH_1] = path
                    }
                    if (title == null) {
                        preferences.remove(SERVER_AUDIO_TITLE_1)
                    } else {
                        preferences[SERVER_AUDIO_TITLE_1] = title
                    }
                }
            }
        }
    }

    override suspend fun setSongSlotSource(slot: Int, source: SongSlotSource) {
        context.dataStore.edit { preferences ->
            when (slot) {
                2 -> preferences[SONG_2_SOURCE] = source.name
                else -> preferences[SONG_1_SOURCE] = source.name
            }
        }
    }

    override suspend fun setLastServerSync(userId: String, atEpochMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SERVER_SYNC_USER_ID] = userId
            preferences[LAST_SERVER_SYNC_AT_EPOCH_MS] = atEpochMs
        }
    }

    override suspend fun clearUserScopedData() {
        context.dataStore.edit { preferences ->
            preferences.remove(TARGET_MAC)
            preferences.remove(AUTO_PLAY_ENABLED)
            preferences.remove(MAX_VOLUME_ENABLED)
            preferences.remove(CONNECTION_START_DELAY_SECONDS)
            preferences.remove(KEEP_APP_ALIVE)
            preferences.remove(AUDIO_FILE_PATH)
            preferences.remove(AUDIO_FILE_PATH_2)
            preferences.remove(FLOATING_BUBBLE_ENABLED)
            preferences.remove(ROUTING_TIER)
            preferences.remove(AUTO_START_DISMISSED)
            preferences.remove(SKIP_AUTO_PLAY_WHEN_AA_CONNECTING)
            preferences.remove(AUTO_PLAY_ON_ANDROID_AUTO)
            preferences.remove(AFTERMARKET_ANDROID_AUTO_TARGET_MACS)
            preferences.remove(SIMULATE_ANDROID_AUTO)
            preferences.remove(REPLAY_AUDIO_URI)
            preferences.remove(REPLAY_AUDIO_TITLE)
            preferences.remove(REPLAY_TARGET_MAC)
            preferences.remove(REPLAY_AA_ENABLED_FOR_TARGET)
            preferences.remove(REPLAY_RESUME_ELIGIBLE)
            preferences.remove(REPLAY_PREPARED_AT_ELAPSED_MS)
            preferences.remove(SERVER_AUDIO_FILE_PATH_1)
            preferences.remove(SERVER_AUDIO_FILE_PATH_2)
            preferences.remove(SERVER_AUDIO_TITLE_1)
            preferences.remove(SERVER_AUDIO_TITLE_2)
            preferences.remove(SONG_1_SOURCE)
            preferences.remove(SONG_2_SOURCE)
            preferences.remove(LAST_SERVER_SYNC_USER_ID)
            preferences.remove(LAST_SERVER_SYNC_AT_EPOCH_MS)
        }
    }

    private fun normalizeMac(mac: String): String = mac.trim().uppercase()

    private fun parseSongSlotSource(rawValue: String): SongSlotSource {
        return runCatching { SongSlotSource.valueOf(rawValue) }.getOrDefault(SongSlotSource.SERVER)
    }
}

