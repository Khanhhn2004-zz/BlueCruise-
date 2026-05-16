package com.vibegravity.bluecruise.ui

internal data class BluetoothScreenSnapshot(
    val isBluetoothEnabled: Boolean = true,
    val showTargetCar: Boolean = false,
    val showEmptyState: Boolean = false,
    val targetDeviceName: String = "",
    val isAutoPlayEnabled: Boolean = true,
    val connectionStartDelaySeconds: Int = 0,
    val isKeepAliveEnabled: Boolean = false,
    val isAutoPlayOnAndroidAutoEnabled: Boolean = false,
    val isAftermarketAndroidAutoTargetEnabled: Boolean = false,
    val audioFilePath: String? = null,
    val audioFilePath2: String? = null,
    val isFloatingBubbleEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val isPlaybackPending: Boolean = false,
    val isServerSyncing: Boolean = false,
    val routingTier: Int = 1
)

internal data class BluetoothScreenRenderPlan(
    val nextSnapshot: BluetoothScreenSnapshot,
    val shouldSyncKeepAliveService: Boolean,
    val shouldSyncFloatingBubbleService: Boolean,
    val targetCarPayloads: List<String>,
    val shouldRefreshEmptyStateContent: Boolean
)

internal fun BluetoothScreenAdapter.toSnapshot(): BluetoothScreenSnapshot {
    return BluetoothScreenSnapshot(
        isBluetoothEnabled = isBluetoothEnabled,
        showTargetCar = showTargetCar,
        showEmptyState = showEmptyState,
        targetDeviceName = targetDeviceName,
        isAutoPlayEnabled = isAutoPlayEnabled,
        connectionStartDelaySeconds = connectionStartDelaySeconds,
        isKeepAliveEnabled = isKeepAppAliveEnabled,
        isAutoPlayOnAndroidAutoEnabled = isAutoPlayOnAndroidAutoEnabled,
        isAftermarketAndroidAutoTargetEnabled = isAftermarketAndroidAutoTargetEnabled,
        audioFilePath = audioFilePath,
        audioFilePath2 = audioFilePath2,
        isFloatingBubbleEnabled = isFloatingBubbleEnabled,
        isPlaying = isPlaying,
        isPlaybackPending = isPlaybackPending,
        isServerSyncing = isServerSyncing,
        routingTier = routingTier
    )
}

internal fun BluetoothScreenAdapter.applySnapshot(snapshot: BluetoothScreenSnapshot) {
    isBluetoothEnabled = snapshot.isBluetoothEnabled
    targetDeviceName = snapshot.targetDeviceName
    isAutoPlayEnabled = snapshot.isAutoPlayEnabled
    connectionStartDelaySeconds = snapshot.connectionStartDelaySeconds
    isKeepAppAliveEnabled = snapshot.isKeepAliveEnabled
    isAutoPlayOnAndroidAutoEnabled = snapshot.isAutoPlayOnAndroidAutoEnabled
    isAftermarketAndroidAutoTargetEnabled = snapshot.isAftermarketAndroidAutoTargetEnabled
    audioFilePath = snapshot.audioFilePath
    audioFilePath2 = snapshot.audioFilePath2
    isFloatingBubbleEnabled = snapshot.isFloatingBubbleEnabled
    isPlaying = snapshot.isPlaying
    isPlaybackPending = snapshot.isPlaybackPending
    isServerSyncing = snapshot.isServerSyncing
    routingTier = snapshot.routingTier
}

internal fun buildBluetoothScreenRenderPlan(
    previous: BluetoothScreenSnapshot,
    state: BluetoothUiState
): BluetoothScreenRenderPlan {
    val targetDevice = state.pairedDevices.find { it.macAddress == state.targetMacAddress }
    val targetDeviceName = targetDevice?.name?.ifEmpty { "Unknown Device" }.orEmpty()
    val showTargetCar = targetDevice != null
    val showEmptyState = !state.isBluetoothEnabled || state.pairedDevices.isEmpty()

    val nextSnapshot = BluetoothScreenSnapshot(
        isBluetoothEnabled = state.isBluetoothEnabled,
        showTargetCar = showTargetCar,
        showEmptyState = showEmptyState,
        targetDeviceName = targetDeviceName,
        isAutoPlayEnabled = state.autoPlayEnabled,
        connectionStartDelaySeconds = state.connectionStartDelaySeconds,
        isKeepAliveEnabled = state.keepAppAliveEnabled,
        isAutoPlayOnAndroidAutoEnabled = state.autoPlayOnAndroidAutoEnabled,
        isAftermarketAndroidAutoTargetEnabled = state.isAftermarketAndroidAutoTargetEnabled,
        audioFilePath = state.audioFilePath,
        audioFilePath2 = state.audioFilePath2,
        isFloatingBubbleEnabled = state.floatingBubbleEnabled,
        isPlaying = state.isPlaying,
        isPlaybackPending = state.isPlaybackPending,
        isServerSyncing = state.isServerSyncing,
        routingTier = state.routingTier
    )

    val targetCarPayloads = buildList {
        if (previous.showTargetCar && showTargetCar && previous.targetDeviceName != targetDeviceName) {
            add(BluetoothScreenAdapter.PAYLOAD_DEVICE_NAME)
        }
        if (previous.isAutoPlayEnabled != state.autoPlayEnabled) {
            add(BluetoothScreenAdapter.PAYLOAD_AUTO_PLAY)
        }
        if (previous.connectionStartDelaySeconds != state.connectionStartDelaySeconds) {
            add(BluetoothScreenAdapter.PAYLOAD_CONNECTION_START_DELAY)
        }
        if (previous.isKeepAliveEnabled != state.keepAppAliveEnabled) {
            add(BluetoothScreenAdapter.PAYLOAD_KEEP_ALIVE)
        }
        if (previous.isAutoPlayOnAndroidAutoEnabled != state.autoPlayOnAndroidAutoEnabled) {
            add(BluetoothScreenAdapter.PAYLOAD_AUTO_PLAY_ON_ANDROID_AUTO)
        }
        if (
            previous.isAftermarketAndroidAutoTargetEnabled !=
            state.isAftermarketAndroidAutoTargetEnabled
        ) {
            add(BluetoothScreenAdapter.PAYLOAD_AFTERMARKET_ANDROID_AUTO_TARGET)
        }
        if (previous.audioFilePath != state.audioFilePath) {
            add(BluetoothScreenAdapter.PAYLOAD_AUDIO_PATH)
        }
        if (previous.audioFilePath2 != state.audioFilePath2) {
            add(BluetoothScreenAdapter.PAYLOAD_AUDIO_PATH_2)
        }
        if (previous.isFloatingBubbleEnabled != state.floatingBubbleEnabled) {
            add(BluetoothScreenAdapter.PAYLOAD_FLOATING_BUBBLE)
        }
        if (
            previous.isPlaying != state.isPlaying ||
            previous.isPlaybackPending != state.isPlaybackPending
        ) {
            add(BluetoothScreenAdapter.PAYLOAD_PLAYBACK_STATE)
        }
        if (previous.isServerSyncing != state.isServerSyncing) {
            add(BluetoothScreenAdapter.PAYLOAD_SERVER_SYNC_STATE)
        }
        if (previous.routingTier != state.routingTier) {
            add(BluetoothScreenAdapter.PAYLOAD_ROUTING_TIER)
        }
    }

    return BluetoothScreenRenderPlan(
        nextSnapshot = nextSnapshot,
        shouldSyncKeepAliveService = previous.isKeepAliveEnabled != state.keepAppAliveEnabled,
        shouldSyncFloatingBubbleService =
            previous.isFloatingBubbleEnabled != state.floatingBubbleEnabled,
        targetCarPayloads = targetCarPayloads,
        shouldRefreshEmptyStateContent = previous.showEmptyState &&
            showEmptyState &&
            previous.isBluetoothEnabled != state.isBluetoothEnabled
    )
}
