package com.vibegravity.bluecruise.ui

import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain
import com.vibegravity.bluecruise.domain.customer.SongSlotSource

data class BluetoothUiState(
    val isBluetoothEnabled: Boolean = true,
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val targetMacAddress: String? = null,
    val autoPlayEnabled: Boolean = true,
    val connectionStartDelaySeconds: Int = 0,
    val keepAppAliveEnabled: Boolean = false,
    val autoPlayOnAndroidAutoEnabled: Boolean = false,
    val isAftermarketAndroidAutoTargetEnabled: Boolean = false,
    val simulateAndroidAutoEnabled: Boolean = false,
    val audioFilePath: String? = null,
    val audioFilePath2: String? = null,
    val song1Source: SongSlotSource = SongSlotSource.SERVER,
    val song2Source: SongSlotSource = SongSlotSource.SERVER,
    val floatingBubbleEnabled: Boolean = false,
    val isFloatingBubblePermissionPending: Boolean = false,
    val routingTier: Int = 1,
    val isPlaying: Boolean = false,
    val isPlaybackPending: Boolean = false,
    val isServerSyncing: Boolean = false,
    val serverSyncMessage: String? = null,
    val isLoggingOut: Boolean = false,
    val navigateToLogin: Boolean = false,
    // Android Auto states
    val isAndroidAutoConnected: Boolean = false,
    val isWaitingForAndroidAutoConfirmation: Boolean = false,
    val showAndroidAutoWarning: Boolean = false
)

