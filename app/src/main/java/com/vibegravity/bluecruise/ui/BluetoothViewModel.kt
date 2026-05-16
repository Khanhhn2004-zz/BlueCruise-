package com.vibegravity.bluecruise.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.vibegravity.bluecruise.di.IoDispatcher
import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.customer.SlotSyncResult
import com.vibegravity.bluecruise.domain.customer.SongSlotSource
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import timber.log.Timber

internal const val MANUAL_PLAYBACK_PENDING_TIMEOUT_MS = 8_000L

class ManualPlaybackServiceController @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) {
    fun stopPlayback() {
        applicationContext.startService(
            Intent(AutoPlayMusicService.ACTION_STOP_PLAYBACK).apply {
                setClassName(
                    applicationContext.packageName,
                    AutoPlayMusicService::class.java.name
                )
            }
        )
    }
}

@Suppress("TooManyFunctions")
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothAdapterRepo: IBluetoothAdapterRepo,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val customerSongSyncRepository: CustomerSongSyncRepository,
    private val manualPlaybackServiceController: ManualPlaybackServiceController,
    private val mediaControllerProvider: MediaControllerProvider?,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var pendingPlaybackTimeoutJob: Job? = null
    private var mediaControllerInitializationAttempt = 0
    private var isMediaControllerInitializing = false

    private fun isInteractionLocked(): Boolean {
        return _uiState.value.isLoggingOut || _uiState.value.navigateToLogin
    }

    init {
        loadPreferences()
        observeBluetoothState()
        ensureMediaControllerInitialized()
    }

    private fun observeBluetoothState() {
        viewModelScope.launch {
            bluetoothAdapterRepo.bluetoothEnabledFlow().collect { enabled ->
                _uiState.value = _uiState.value.copy(
                    isBluetoothEnabled = enabled,
                    pairedDevices = if (enabled) _uiState.value.pairedDevices else emptyList()
                )
                if (enabled) refreshPairedDevices()
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch(ioDispatcher) {
            val targetMac = settingsRepository.targetMacFlow.firstOrNull()
            val autoPlay = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true
            val connectionStartDelaySeconds =
                (settingsRepository.connectionStartDelaySecondsFlow.firstOrNull() ?: 0).coerceIn(0, 10)
            val keepAlive = settingsRepository.keepAppAliveFlow.firstOrNull() ?: false
            val autoPlayOnAndroidAuto = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
            val isAftermarketAndroidAutoTarget =
                settingsRepository.aftermarketAndroidAutoTargetFlow.firstOrNull() ?: false
            val simulateAA = settingsRepository.simulateAndroidAutoFlow.firstOrNull() ?: false
            val audioPath = settingsRepository.audioFilePathFlow.firstOrNull()
            val audioPath2 = settingsRepository.audioFilePath2Flow.firstOrNull()
            val song1Source = settingsRepository.song1SourceFlow.firstOrNull() ?: SongSlotSource.SERVER
            val song2Source = settingsRepository.song2SourceFlow.firstOrNull() ?: SongSlotSource.SERVER
            val floatingBubbleEnabled = settingsRepository.floatingBubbleEnabledFlow.firstOrNull() ?: false
            val routingTier = settingsRepository.routingTierFlow.firstOrNull() ?: 1
            _uiState.update {
                it.copy(
                    targetMacAddress = targetMac,
                    autoPlayEnabled = autoPlay,
                    connectionStartDelaySeconds = connectionStartDelaySeconds,
                    keepAppAliveEnabled = keepAlive,
                    autoPlayOnAndroidAutoEnabled = autoPlayOnAndroidAuto,
                    isAftermarketAndroidAutoTargetEnabled = isAftermarketAndroidAutoTarget,
                    simulateAndroidAutoEnabled = simulateAA,
                    audioFilePath = audioPath,
                    audioFilePath2 = audioPath2,
                    song1Source = song1Source,
                    song2Source = song2Source,
                    floatingBubbleEnabled = floatingBubbleEnabled,
                    routingTier = routingTier,
                    isWaitingForAndroidAutoConfirmation = false,
                    showAndroidAutoWarning = false
                )
            }
        }
    }

    private fun observePlaybackState(controller: MediaController) {
        syncPlaybackUiState(controller.isPlaying)
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackUiState(isPlaying)
            }
        })
    }

    private fun ensureMediaControllerInitialized() {
        val provider = mediaControllerProvider ?: return
        if (mediaController != null || isMediaControllerInitializing) return

        val attempt = ++mediaControllerInitializationAttempt
        isMediaControllerInitializing = true
        provider.initialize { controller ->
            if (attempt != mediaControllerInitializationAttempt) {
                controller?.release()
                return@initialize
            }
            isMediaControllerInitializing = false
            mediaController = controller
            if (controller == null) {
                failPendingPlayback("Failed to initialize media controller")
                return@initialize
            }

            observePlaybackState(controller)
            if (_uiState.value.isPlaybackPending && !controller.isPlaying) {
                requestPlayback(controller)
            }
        }
    }

    private fun syncPlaybackUiState(isPlaying: Boolean) {
        if (isPlaying) cancelPendingPlaybackTimeout()
        _uiState.update { currentState ->
            currentState.copy(
                isPlaying = isPlaying,
                isPlaybackPending = currentState.isPlaybackPending && !isPlaying
            )
        }
    }

    private fun startPlaybackPending() {
        _uiState.update { it.copy(isPlaybackPending = true) }
        schedulePendingPlaybackTimeout()
    }

    private fun clearPlaybackPending() {
        cancelPendingPlaybackTimeout()
        _uiState.update { currentState ->
            if (currentState.isPlaybackPending) {
                currentState.copy(isPlaybackPending = false)
            } else {
                currentState
            }
        }
    }

    private fun schedulePendingPlaybackTimeout() {
        cancelPendingPlaybackTimeout()
        pendingPlaybackTimeoutJob = viewModelScope.launch {
            delay(MANUAL_PLAYBACK_PENDING_TIMEOUT_MS)
            val currentState = _uiState.value
            if (currentState.isPlaybackPending && !currentState.isPlaying) {
                val timeoutMessage = if (mediaController == null && isMediaControllerInitializing) {
                    "Manual playback timed out waiting for MediaController initialization"
                } else {
                    "Manual playback timed out waiting for isPlaying=true"
                }
                reconcileFailedManualPlayback(timeoutMessage)
            }
        }
    }

    private fun cancelPendingPlaybackTimeout() {
        pendingPlaybackTimeoutJob?.cancel()
        pendingPlaybackTimeoutJob = null
    }

    private fun requestPlayback(controller: MediaController) {
        if (controller.isPlaying) {
            syncPlaybackUiState(true)
            return
        }

        runCatching { controller.play() }
            .onFailure { exception ->
                failPendingPlayback("Failed to start manual playback", exception)
            }
    }

    private fun reconcileFailedManualPlayback(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.w(throwable, message)
        } else {
            Timber.w(message)
        }
        cancelPendingPlaybackTimeout()
        stopManualPlaybackService()
        resetMediaController()
        _uiState.update { currentState ->
            if (currentState.isPlaybackPending || currentState.isPlaying) {
                currentState.copy(
                    isPlaying = false,
                    isPlaybackPending = false
                )
            } else {
                currentState
            }
        }
    }

    private fun failPendingPlayback(message: String, throwable: Throwable? = null) {
        if (_uiState.value.isPlaybackPending || _uiState.value.isPlaying) {
            reconcileFailedManualPlayback(message, throwable)
            return
        }
        if (throwable != null) {
            Timber.w(throwable, message)
        } else {
            Timber.w(message)
        }
    }

    private fun stopManualPlaybackService() {
        runCatching {
            manualPlaybackServiceController.stopPlayback()
        }.onFailure { exception ->
            Timber.w(exception, "Failed to stop AutoPlayMusicService during manual playback cleanup")
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (isInteractionLocked()) return

        viewModelScope.launch(ioDispatcher) {
            val devices = if (bluetoothAdapterRepo.isBluetoothEnabled()) {
                bluetoothAdapterRepo.getPairedDevices()
            } else {
                emptyList()
            }
            _uiState.update { it.copy(pairedDevices = devices) }
        }
    }

    fun setTargetDevice(macAddress: String) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.saveTargetMac(macAddress)
            val isAftermarketAndroidAutoTarget =
                settingsRepository.aftermarketAndroidAutoTargetFlow.firstOrNull() ?: false
            _uiState.value = _uiState.value.copy(
                targetMacAddress = macAddress,
                isAftermarketAndroidAutoTargetEnabled = isAftermarketAndroidAutoTarget
            )
        }
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setAutoPlayEnabled(enabled)
            _uiState.value = _uiState.value.copy(autoPlayEnabled = enabled)
        }
    }

    fun setConnectionStartDelaySeconds(seconds: Int) {
        if (isInteractionLocked()) return

        val clampedSeconds = seconds.coerceIn(0, 10)
        viewModelScope.launch {
            settingsRepository.setConnectionStartDelaySeconds(clampedSeconds)
            _uiState.value = _uiState.value.copy(connectionStartDelaySeconds = clampedSeconds)
        }
    }

    fun setKeepAppAliveEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setKeepAppAlive(enabled)
            _uiState.value = _uiState.value.copy(keepAppAliveEnabled = enabled)
        }
    }

    fun setAutoPlayOnAndroidAutoEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setAutoPlayOnAndroidAuto(enabled)
            _uiState.value = _uiState.value.copy(autoPlayOnAndroidAutoEnabled = enabled)
        }
    }

    fun setAftermarketAndroidAutoTargetEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setAftermarketAndroidAutoTarget(enabled)
            _uiState.value = _uiState.value.copy(isAftermarketAndroidAutoTargetEnabled = enabled)
        }
    }

    fun setSimulateAndroidAutoEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setSimulateAndroidAuto(enabled)
            _uiState.value = _uiState.value.copy(simulateAndroidAutoEnabled = enabled)
        }
    }

    fun setAudioFilePath(path: String?) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.saveAudioFilePath(path)
            if (path == null) {
                settingsRepository.clearReplayableItem()
            } else {
                settingsRepository.setSongSlotSource(AUDIO_SLOT_PRIMARY, SongSlotSource.MANUAL)
                val targetMac = settingsRepository.targetMacFlow.firstOrNull()
                val autoPlayOnAndroidAuto = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
                settingsRepository.saveReplayableItem(
                    audioUri = path,
                    displayTitle = null,
                    targetMac = targetMac,
                    aaEnabledForTarget = autoPlayOnAndroidAuto
                )
            }
            _uiState.value = _uiState.value.copy(
                audioFilePath = path,
                song1Source = if (path == null) _uiState.value.song1Source else SongSlotSource.MANUAL
            )
        }
    }

    fun setAudioFilePath2(path: String?) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.saveAudioFilePath2(path)
            if (path != null) {
                settingsRepository.setSongSlotSource(AUDIO_SLOT_SECONDARY, SongSlotSource.MANUAL)
            }
            _uiState.value = _uiState.value.copy(
                audioFilePath2 = path,
                song2Source = if (path == null) _uiState.value.song2Source else SongSlotSource.MANUAL
            )
        }
    }

    fun syncServerSongs() {
        if (_uiState.value.isServerSyncing || isInteractionLocked()) return

        viewModelScope.launch(ioDispatcher) {
            val session = authSessionRepository.sessionFlow.firstOrNull() ?: AuthSession()
            if (session.accessToken.isBlank() || session.userId.isBlank()) {
                _uiState.update {
                    it.copy(serverSyncMessage = "Please log in to sync server songs")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isServerSyncing = true,
                    serverSyncMessage = null
                )
            }

            val outcome = customerSongSyncRepository.sync(
                session = session,
                trigger = CustomerSongSyncTrigger.MANUAL
            )
            val audioPath = settingsRepository.audioFilePathFlow.firstOrNull()
            val audioPath2 = settingsRepository.audioFilePath2Flow.firstOrNull()
            val song1Source = settingsRepository.song1SourceFlow.firstOrNull() ?: SongSlotSource.SERVER
            val song2Source = settingsRepository.song2SourceFlow.firstOrNull() ?: SongSlotSource.SERVER

            _uiState.update {
                it.copy(
                    audioFilePath = audioPath,
                    audioFilePath2 = audioPath2,
                    song1Source = song1Source,
                    song2Source = song2Source,
                    isServerSyncing = false,
                    serverSyncMessage = manualSyncMessageFor(outcome.hello, outcome.goodbye)
                )
            }
        }
    }

    fun consumeServerSyncMessage() {
        _uiState.update { currentState ->
            if (currentState.serverSyncMessage == null) {
                currentState
            } else {
                currentState.copy(serverSyncMessage = null)
            }
        }
    }

    fun onLogoutRequested() {
        if (_uiState.value.isLoggingOut) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoggingOut = true,
                    isPlaying = false,
                    isPlaybackPending = false,
                    serverSyncMessage = null
                )
            }
            cancelPendingPlaybackTimeout()
            stopManualPlaybackService()
            resetMediaController()

            runCatching {
                withContext(ioDispatcher) {
                    authRepository.logout()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        navigateToLogin = true
                    )
                }
            }.onFailure { exception ->
                Timber.w(exception, "Logout cleanup failed")
                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        serverSyncMessage = "Logout failed"
                    )
                }
            }
        }
    }

    fun consumeLogoutNavigation() {
        _uiState.update { currentState ->
            if (currentState.navigateToLogin) {
                currentState.copy(navigateToLogin = false)
            } else {
                currentState
            }
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setFloatingBubbleEnabled(enabled)
            _uiState.value = _uiState.value.copy(
                floatingBubbleEnabled = enabled,
                isFloatingBubblePermissionPending = false
            )
        }
    }

    fun markFloatingBubblePermissionPending() {
        if (isInteractionLocked()) return

        _uiState.update { currentState ->
            if (currentState.isFloatingBubblePermissionPending) {
                currentState
            } else {
                currentState.copy(isFloatingBubblePermissionPending = true)
            }
        }
    }

    fun clearFloatingBubblePermissionPending() {
        _uiState.update { currentState ->
            if (!currentState.isFloatingBubblePermissionPending) {
                currentState
            } else {
                currentState.copy(isFloatingBubblePermissionPending = false)
            }
        }
    }

    fun consumeFloatingBubblePermissionGrant(hasPermission: Boolean): Boolean {
        if (isInteractionLocked()) return false
        if (!hasPermission) return false

        var shouldEnable = false
        _uiState.update { currentState ->
            if (currentState.isFloatingBubblePermissionPending) {
                shouldEnable = true
                currentState.copy(isFloatingBubblePermissionPending = false)
            } else {
                currentState
            }
        }
        return shouldEnable
    }

    fun setRoutingTier(tier: Int) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.saveRoutingTier(tier)
            _uiState.value = _uiState.value.copy(routingTier = tier)
        }
    }

    fun setSkipAutoPlayWhenAaConnecting(enabled: Boolean) {
        if (isInteractionLocked()) return

        viewModelScope.launch {
            settingsRepository.setSkipAutoPlayWhenAaConnecting(enabled)
            _uiState.update { it.copy() } // Trigger UI update
        }
    }

    fun dismissAndroidAutoWarning() {
        _uiState.update {
            it.copy(showAndroidAutoWarning = false)
        }
    }

    fun onManualPlaybackStartRequested() {
        if (isInteractionLocked()) return

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val shouldGateForAndroidAuto =
                settingsRepository.skipAutoPlayWhenAaConnectingFlow.firstOrNull() ?: false
            if (shouldGateForAndroidAuto) {
                val connectedDevices = bluetoothAdapterRepo.getConnectedA2dpAddresses()
                var isAndroidAutoConnecting = false

                for (deviceMac in connectedDevices) {
                    if (bluetoothAdapterRepo.isAndroidAutoDevice(deviceMac)) {
                        isAndroidAutoConnecting = true
                        break
                    }
                }

                if (isAndroidAutoConnecting) {
                    Timber.d("Android Auto is connecting, showing warning to user")
                    _uiState.update {
                        it.copy(
                            showAndroidAutoWarning = true,
                            isWaitingForAndroidAutoConfirmation = true
                        )
                    }
                    waitForAndroidAutoConfirmation()
                    return@launch
                }
            }

            startPlaybackPending()
            mediaController?.let(::requestPlayback) ?: ensureMediaControllerInitialized()
        }
    }

    /**
     * Wait for Android Auto confirmation or timeout (10 seconds).
     * If AA confirms (process detected), allow playback.
     * If timeout, show message to user.
     */
    private fun waitForAndroidAutoConfirmation() {
        viewModelScope.launch {
            var retryCount = 0
            val maxRetries = 20 // 10 seconds (500ms * 20)
            
            while (retryCount < maxRetries && _uiState.value.isWaitingForAndroidAutoConfirmation) {
                delay(500)
                
                val connectedDevices = bluetoothAdapterRepo.getConnectedA2dpAddresses()
                var aaConfirmed = false
                
                for (deviceMac in connectedDevices) {
                    if (bluetoothAdapterRepo.isAndroidAutoDevice(deviceMac)) {
                        aaConfirmed = true
                        break
                    }
                }
                
                if (aaConfirmed) {
                    Timber.d("Android Auto confirmed, starting playback")
                    _uiState.update {
                        it.copy(
                            isAndroidAutoConnected = true,
                            isWaitingForAndroidAutoConfirmation = false,
                            showAndroidAutoWarning = false
                        )
                    }
                    // Now start playback
                    startPlaybackPending()
                    mediaController?.let(::requestPlayback) ?: ensureMediaControllerInitialized()
                    return@launch
                }
                
                retryCount++
            }
            
            // Timeout - AA didn't confirm
            Timber.d("Android Auto confirmation timeout")
            _uiState.update {
                it.copy(
                    isWaitingForAndroidAutoConfirmation = false,
                    showAndroidAutoWarning = false
                )
            }
        }
    }

    fun onManualPlaybackStopRequested() {
        clearPlaybackPending()
        resetMediaController()
        _uiState.update { currentState ->
            currentState.copy(
                isPlaying = false,
                isPlaybackPending = false
            )
        }
    }

    fun togglePlayback() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                clearPlaybackPending()
                controller.pause()
            } else {
                startPlaybackPending()
                requestPlayback(controller)
            }
        }
    }

    private fun resetMediaController() {
        mediaControllerInitializationAttempt += 1
        isMediaControllerInitializing = false
        mediaController?.release()
        mediaController = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelPendingPlaybackTimeout()
        resetMediaController()
    }

    private fun manualSyncMessageFor(
        hello: SlotSyncResult,
        goodbye: SlotSyncResult
    ): String {
        val helloFailed = hello is SlotSyncResult.Failed
        val goodbyeFailed = goodbye is SlotSyncResult.Failed
        return when {
            helloFailed && goodbyeFailed -> "Server sync failed"
            helloFailed || goodbyeFailed -> "Server sync partially completed"
            else -> "Server songs updated"
        }
    }

    private companion object {
        const val AUDIO_SLOT_PRIMARY = 1
        const val AUDIO_SLOT_SECONDARY = 2
    }
}
