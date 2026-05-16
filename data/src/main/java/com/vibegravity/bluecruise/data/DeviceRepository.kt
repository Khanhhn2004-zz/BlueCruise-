package com.vibegravity.bluecruise.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceRepository {
    suspend fun saveTargetDeviceMac(macAddress: String)
    fun getTargetDeviceMac(): Flow<String?>
    suspend fun saveAutoPlayEnabled(enabled: Boolean)
    fun isAutoPlayEnabled(): Flow<Boolean>
    suspend fun saveMaxVolumeEnabled(enabled: Boolean)
    fun isMaxVolumeEnabled(): Flow<Boolean>
    suspend fun saveAudioUri(uri: String)
    fun getAudioUri(): Flow<String?>
    suspend fun saveKeepAppAliveEnabled(enabled: Boolean)
    fun isKeepAppAliveEnabled(): Flow<Boolean>
    suspend fun saveAutoStartDismissed(dismissed: Boolean)
    fun isAutoStartDismissed(): Flow<Boolean>
    suspend fun saveRoutingTier(tier: Int)
    fun getRoutingTier(): Flow<Int>
}

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceRepository {

    private val TARGET_MAC_KEY = stringPreferencesKey("target_mac_address")
    private val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play_enabled")
    private val MAX_VOLUME_KEY = booleanPreferencesKey("max_volume_enabled")
    private val AUDIO_URI_KEY = stringPreferencesKey("custom_audio_uri")
    private val KEEP_ALIVE_KEY = booleanPreferencesKey("keep_app_alive_enabled")
    private val AUTO_START_DISMISSED_KEY = booleanPreferencesKey("auto_start_dismissed")
    private val ROUTING_TIER_KEY = intPreferencesKey("routing_tier_level")

    override suspend fun saveTargetDeviceMac(macAddress: String) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_MAC_KEY] = macAddress
        }
    }

    override fun getTargetDeviceMac(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[TARGET_MAC_KEY]
        }
    }

    override suspend fun saveAutoPlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PLAY_KEY] = enabled
        }
    }

    override fun isAutoPlayEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_PLAY_KEY] ?: true
        }
    }

    override suspend fun saveMaxVolumeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MAX_VOLUME_KEY] = enabled
        }
    }

    override fun isMaxVolumeEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[MAX_VOLUME_KEY] ?: true
        }
    }

    override suspend fun saveAudioUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_URI_KEY] = uri
        }
    }

    override fun getAudioUri(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[AUDIO_URI_KEY]
        }
    }

    override suspend fun saveKeepAppAliveEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEEP_ALIVE_KEY] = enabled
        }
    }

    override fun isKeepAppAliveEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEEP_ALIVE_KEY] ?: false
        }
    }

    override suspend fun saveAutoStartDismissed(dismissed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_DISMISSED_KEY] = dismissed
        }
    }

    override fun isAutoStartDismissed(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_START_DISMISSED_KEY] ?: false
        }
    }

    override suspend fun saveRoutingTier(tier: Int) {
        context.dataStore.edit { preferences ->
            preferences[ROUTING_TIER_KEY] = tier
        }
    }

    override fun getRoutingTier(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[ROUTING_TIER_KEY] ?: 3
        }
    }
}

