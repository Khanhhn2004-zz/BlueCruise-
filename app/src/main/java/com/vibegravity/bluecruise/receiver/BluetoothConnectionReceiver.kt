package com.vibegravity.bluecruise.receiver

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.vibegravity.bluecruise.domain.BluetoothConnectionAction
import com.vibegravity.bluecruise.domain.BluetoothConnectionHandler
import com.vibegravity.bluecruise.domain.BluetoothEventType
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.service.AndroidAutoRetryPolicy
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import com.vibegravity.bluecruise.service.KeepAliveService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps pending playback-start state outside receiver instances so separate broadcasts can
 * cancel or dedupe the same delayed start reliably.
 */
internal class PlaybackStartDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long
) {
    private val lock = Any()
    private var pendingStartJob: Job? = null
    private var pendingDeviceMac: String? = null

    fun schedule(
        deviceMac: String?,
        skipIfSamePending: Boolean = false,
        onDebounceElapsed: () -> Unit
    ): Boolean {
        synchronized(lock) {
            if (skipIfSamePending && pendingDeviceMac == deviceMac && pendingStartJob != null) {
                return false
            }

            pendingStartJob?.cancel()
            pendingDeviceMac = deviceMac
            pendingStartJob = scope.launch {
                delay(delayMs)
                val shouldRun = synchronized(lock) {
                    val matchesPendingDevice = pendingDeviceMac == deviceMac
                    pendingStartJob = null
                    matchesPendingDevice
                }
                if (shouldRun) {
                    onDebounceElapsed()
                }
            }
            return true
        }
    }

    fun cancelPending(deviceMac: String?): Boolean {
        synchronized(lock) {
            if (deviceMac != pendingDeviceMac) return false

            val hadPendingStart = pendingStartJob != null
            pendingStartJob?.cancel()
            pendingStartJob = null
            pendingDeviceMac = null
            return hadPendingStart
        }
    }
}

internal class PlaybackStopDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long
) {
    private val lock = Any()
    private var pendingStopJob: Job? = null
    private var pendingDeviceMac: String? = null

    fun schedule(deviceMac: String?, onDebounceElapsed: () -> Unit) {
        synchronized(lock) {
            pendingStopJob?.cancel()
            pendingDeviceMac = deviceMac
            pendingStopJob = scope.launch {
                delay(delayMs)
                val shouldRun = synchronized(lock) {
                    val matches = pendingDeviceMac == deviceMac
                    pendingStopJob = null
                    matches
                }
                if (shouldRun) onDebounceElapsed()
            }
        }
    }

    fun cancelPending(deviceMac: String?): Boolean {
        synchronized(lock) {
            if (deviceMac != pendingDeviceMac) return false
            val hadPending = pendingStopJob != null
            pendingStopJob?.cancel()
            pendingStopJob = null
            pendingDeviceMac = null
            return hadPending
        }
    }
}

@AndroidEntryPoint
class BluetoothConnectionReceiver internal constructor(
    private val receiverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val startPlaybackDelayMs: Long = START_PLAYBACK_DELAY_MS,
    private val stateOnFallbackDelayMs: Long = STATE_ON_FALLBACK_DELAY_MS,
    private val readinessPollIntervalMs: Long = READINESS_POLL_INTERVAL_MS,
    private val stopVerificationDelayMs: Long = STOP_VERIFICATION_DELAY_MS,
    private val maxPollCount: Int = MAX_POLL_COUNT,
    private val totalPollTimeoutMs: Long = TOTAL_POLL_TIMEOUT_MS,
    private val maxPollIntervalMs: Long = MAX_POLL_INTERVAL_MS,
    private val startDebouncer: PlaybackStartDebouncer = sharedStartDebouncer,
    private val stopDebouncer: PlaybackStopDebouncer = sharedStopDebouncer,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) : BroadcastReceiver() {

    @Inject
    lateinit var bluetoothConnectionHandler: BluetoothConnectionHandler

    @Inject
    lateinit var bluetoothAdapterRepo: IBluetoothAdapterRepo

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val handoffStore = AndroidAutoHandoffSessionStore.shared()
    private val androidAutoRetryPolicy = AndroidAutoRetryPolicy()

    /** Override phone volume to 60% when user has enabled "Âm lu?ng khi k?t n?i". */
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action ?: return
        Timber.d("Received action: %s", intentAction)

        if (intentAction == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            launchAsyncSafely {
                // Kiểm tra Bluetooth state trước khi restore
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
                KeepAliveServiceRestorer.restoreIfEnabled(appContext, settingsRepository, isBluetoothEnabled)
            }
            return
        }

        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        }
        val deviceMac = device?.address ?: intent.getStringExtra(EXTRA_DEVICE_MAC_FALLBACK)
        val appContext = context

        if (intentAction == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
            launchAsyncSafely {
                handleBluetoothStateChanged(appContext, state)
            }
            return
        }

        when (intentAction) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val targetMac = deviceMac ?: return
                launchAsyncSafely {
                    handleTargetAutoPlayTrigger(appContext, targetMac, source = "acl_connected")
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val targetMac = deviceMac ?: return
                launchAsyncSafely {
                    handleDisconnect(appContext, targetMac)
                }
            }
        }
    }

    private fun launchAsyncSafely(block: suspend () -> Unit) {
        val pendingResult = try {
            goAsync()
        } catch (_: IllegalStateException) {
            null
        }

        receiverScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal suspend fun handleBluetoothStateChanged(appContext: Context, state: Int) {
        if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_ON) {
            handoffStore.markBluetoothRestarted()
        }
        if (state != BluetoothAdapter.STATE_ON) {
            return
        }
        handleStateOnFallback(appContext)
    }

    internal suspend fun handleStateOnFallback(appContext: Context) {
        delay(stateOnFallbackDelayMs)
        val addresses = bluetoothAdapterRepo.getConnectedA2dpAddresses()
        Timber.d("STATE_ON fallback: connected A2DP devices=%s", addresses)
        for (mac in addresses) {
            handleTargetAutoPlayTrigger(appContext, mac, source = "state_on_fallback")
        }
    }

    internal suspend fun handleTargetAutoPlayTrigger(appContext: Context, targetMac: String, source: String) {
        val connectionAction = bluetoothConnectionHandler.handle(BluetoothEventType.CONNECTED, targetMac)
        if (connectionAction !is BluetoothConnectionAction.StartPlayback) {
            Timber.d("Ignoring %s trigger for non-target MAC %s", source, targetMac)
            return
        }

        val autoPlayEnabled = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true
        val configuredTargetMac = settingsRepository.targetMacFlow.firstOrNull()
        val matchesConfiguredTarget = matchesConfiguredTargetMac(configuredTargetMac, targetMac)
        if (!autoPlayEnabled || !matchesConfiguredTarget) {
            Timber.d(
                "%s trigger blocked by settings: autoPlayEnabled=%s targetMac=%s eventMac=%s",
                source,
                autoPlayEnabled,
                configuredTargetMac,
                targetMac
            )
            return
        }

        val existingSnapshot = handoffStore.snapshot()
        val cachedIsAndroidAutoTargetDevice =
            if (existingSnapshot.sessionId != null && existingSnapshot.targetMac == targetMac) {
                existingSnapshot.isAndroidAutoTargetDevice
            } else {
                bluetoothAdapterRepo.isAndroidAutoDevice(targetMac)
            }
        val handle = handoffStore.beginOrReuseSession(targetMac, cachedIsAndroidAutoTargetDevice)
        if (!handle.isFreshSession) {
            val currentSnapshot = handoffStore.snapshot()
            if (
                currentSnapshot.targetMac == targetMac &&
                currentSnapshot.state == AndroidAutoHandoffState.COMPLETED &&
                currentSnapshot.pendingStopVerificationAtElapsedMs != null
            ) {
                stopDebouncer.cancelPending(targetMac)
                handoffStore.clearStopVerification(handle.sessionId)
                Timber.d("Cleared pending stop verification on reconnect for %s", targetMac)
            }
            Timber.d(
                "Ignoring duplicate trigger for %s. state=%s timedOut=%s sessionId=%s",
                targetMac,
                currentSnapshot.state,
                currentSnapshot.timedOut,
                handle.sessionId
            )
            return
        }

        val autoPlayOnAndroidAuto = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
        if (autoPlayOnAndroidAuto) {
            val aftermarketAndroidAutoTarget =
                settingsRepository.aftermarketAndroidAutoTargetFlow.firstOrNull() ?: false
            val readinessSnapshot = sampleReadinessSnapshotOrNull(appContext, handle.sessionId) ?: return
            if (aftermarketAndroidAutoTarget) {
                val firstFallbackDelayMs = androidAutoRetryPolicy.delayBeforeAttempt(attempt = 1)
                Timber.d(
                    "%s trigger holding aftermarket AA target %s for %dms before AA fallback",
                    source,
                    targetMac,
                    firstFallbackDelayMs
                )
                receiverScope.launch {
                    startAndroidAutoPreparationWait(
                        appContext = appContext,
                        sessionId = handle.sessionId,
                        targetMac = targetMac,
                        aftermarketAndroidAutoTarget = true
                    )
                }
                return
            }
            if (AndroidAutoReadinessProbe.isCandidate(readinessSnapshot)) {
                Timber.d("%s trigger entering Android Auto wait for %s", source, targetMac)
                logReadinessTransition(handle.sessionId, targetMac, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, readinessSnapshot)
                handoffStore.transitionTo(handle.sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
                startAndroidAutoWait(appContext, handle.sessionId, targetMac)
                return
            }
            if (readinessSnapshot.isAndroidAutoTargetDevice) {
                val firstFallbackDelayMs = androidAutoRetryPolicy.delayBeforeAttempt(attempt = 1)
                Timber.d(
                    "%s trigger holding suspected AA target %s for %dms before Bluetooth fallback",
                    source,
                    targetMac,
                    firstFallbackDelayMs
                )
                receiverScope.launch {
                    startAndroidAutoPreparationWait(appContext, handle.sessionId, targetMac)
                }
                return
            }
        }

        stopDebouncer.cancelPending(targetMac)
        startDebouncer.schedule(deviceMac = targetMac) {
            receiverScope.launch {
                handleBluetoothDebounceElapsed(appContext, handle.sessionId, targetMac)
            }
        }
        Timber.d("%s trigger scheduled Bluetooth debounce for %s after %dms", source, targetMac, startPlaybackDelayMs)
    }

    private suspend fun handleBluetoothDebounceElapsed(appContext: Context, sessionId: Long, targetMac: String) {
        val currentSnapshot = handoffStore.snapshot()
        if (currentSnapshot.sessionId != sessionId || currentSnapshot.targetMac != targetMac) {
            Timber.d("Skipping stale Bluetooth debounce for %s session=%s", targetMac, sessionId)
            return
        }

        val autoPlayOnAndroidAuto = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
        if (autoPlayOnAndroidAuto) {
            val readinessSnapshot = sampleReadinessSnapshotOrNull(appContext, sessionId) ?: return
            if (AndroidAutoReadinessProbe.isCandidate(readinessSnapshot)) {
                Timber.d("Bluetooth debounce recheck switched %s into Android Auto wait", targetMac)
                logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, readinessSnapshot)
                handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
                startAndroidAutoWait(appContext, sessionId, targetMac)
                return
            }
        }

        dispatchPlaybackStart(appContext, sessionId, targetMac, viaAndroidAuto = false)
    }

    private suspend fun startAndroidAutoPreparationWait(
        appContext: Context,
        sessionId: Long,
        targetMac: String,
        aftermarketAndroidAutoTarget: Boolean = false
    ) {
        val sessionStartedAtElapsedMs = elapsedRealtimeMs()
        var firstPartialSignalElapsedMs: Long? = null

        while (true) {
            val currentSnapshot = handoffStore.snapshot()
            if (currentSnapshot.sessionId != sessionId || currentSnapshot.targetMac != targetMac) {
                Timber.d("Ending Android Auto preparation wait for stale session=%s target=%s", sessionId, targetMac)
                return
            }

            val readinessSnapshot = sampleReadinessSnapshotOrNull(appContext, sessionId) ?: return
            if (AndroidAutoReadinessProbe.isCandidate(readinessSnapshot)) {
                if (!aftermarketAndroidAutoTarget) {
                    Timber.d("Preparation wait detected AA signals for %s", targetMac)
                    logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, readinessSnapshot)
                    handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
                    startAndroidAutoWait(appContext, sessionId, targetMac)
                    return
                }

                if (AndroidAutoReadinessProbe.isReady(readinessSnapshot)) {
                    Timber.d("Aftermarket preparation wait detected fully ready AA signals for %s", targetMac)
                    logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, readinessSnapshot)
                    handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
                    startAndroidAutoWait(appContext, sessionId, targetMac)
                    return
                }

                if (firstPartialSignalElapsedMs == null) {
                    firstPartialSignalElapsedMs = elapsedRealtimeMs()
                    Timber.d(
                        "Aftermarket preparation wait detected stable partial AA signals for %s and armed early fallback",
                        targetMac
                    )
                }
            } else if (firstPartialSignalElapsedMs != null) {
                Timber.d(
                    "Aftermarket preparation wait lost partial AA signals for %s and restored max fallback window",
                    targetMac
                )
                firstPartialSignalElapsedMs = null
            }

            val deadlineElapsedMs =
                if (aftermarketAndroidAutoTarget) {
                    androidAutoRetryPolicy.preparationFallbackDeadlineElapsedMs(
                        sessionStartedAtElapsedMs = sessionStartedAtElapsedMs,
                        attempt = 1,
                        firstPartialSignalElapsedMs = firstPartialSignalElapsedMs
                    )
                } else {
                    sessionStartedAtElapsedMs + androidAutoRetryPolicy.delayBeforeAttempt(attempt = 1)
                }
            if (elapsedRealtimeMs() >= deadlineElapsedMs) {
                break
            }

            delay(readinessPollIntervalMs)
        }

        Timber.d(
            "Android Auto preparation window expired for %s, falling back to %s start",
            targetMac,
            if (aftermarketAndroidAutoTarget) "aftermarket AA" else "Bluetooth"
        )
        dispatchPlaybackStart(
            appContext,
            sessionId,
            targetMac,
            viaAndroidAuto = aftermarketAndroidAutoTarget
        )
    }

    private suspend fun startAndroidAutoWait(appContext: Context, sessionId: Long, targetMac: String) {
        var readyStreak = 0
        var pollCount = 0
        val waitStartElapsedMs = elapsedRealtimeMs()

        while (pollCount < maxPollCount) {
            // Kiểm tra timeout tổng
            val elapsedSinceWaitMs = elapsedRealtimeMs() - waitStartElapsedMs
            if (elapsedSinceWaitMs >= totalPollTimeoutMs) {
                Timber.d("AA polling timeout after ${pollCount} polls (${elapsedSinceWaitMs}ms), fallback to BT")
                return handleBluetoothPollingTimeout(appContext, sessionId, targetMac)
            }

            // Kiểm tra session còn valid không
            val currentSnapshot = handoffStore.snapshot()
            if (currentSnapshot.sessionId != sessionId || currentSnapshot.targetMac != targetMac) {
                Timber.d("Ending Android Auto wait for stale session=%s target=%s", sessionId, targetMac)
                return
            }

            pollCount++
            coroutineContext.ensureActive()
            
            val lastSnapshot = sampleReadinessSnapshotOrNull(appContext, sessionId) ?: return
            if (AndroidAutoReadinessProbe.isReady(lastSnapshot)) {
                readyStreak += 1
                if (readyStreak == 1) {
                    logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.AA_READY_CONFIRMING, lastSnapshot)
                    handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.AA_READY_CONFIRMING)
                } else {
                    logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.AA_READY_STARTING, lastSnapshot)
                    handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.AA_READY_STARTING)
                    dispatchPlaybackStart(appContext, sessionId, targetMac, viaAndroidAuto = true)
                    return
                }
            } else {
                readyStreak = 0
                logReadinessTransition(sessionId, targetMac, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO, lastSnapshot)
                handoffStore.transitionTo(sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
            }

            // Giữ nguyên logic delay như cũ - theo thời gian đã trôi qua
            val pollDelayMs = if (elapsedSinceWaitMs < READINESS_POLL_FAST_WINDOW_MS) {
                readinessPollIntervalMs
            } else {
                READINESS_POLL_SLOW_INTERVAL_MS
            }
            
            Timber.d("AA polling ${pollCount}/${maxPollCount}, delay=${pollDelayMs}ms, elapsed=${elapsedSinceWaitMs}ms")
            delay(pollDelayMs)
        }

        // Hết số lần poll, fallback
        Timber.d("AA polling exhausted after ${pollCount} polls, fallback to BT")
        return handleBluetoothPollingTimeout(appContext, sessionId, targetMac)
    }
    
    private suspend fun handleBluetoothPollingTimeout(
        appContext: Context,
        sessionId: Long,
        targetMac: String
    ) {
        Timber.d("Polling timeout for $targetMac, falling back to direct Bluetooth playback")
        dispatchPlaybackStart(appContext, sessionId, targetMac, viaAndroidAuto = false)
    }

    private suspend fun dispatchPlaybackStart(
        appContext: Context,
        sessionId: Long,
        targetMac: String,
        viaAndroidAuto: Boolean
    ) {
        val currentSnapshot = handoffStore.snapshot()
        if (currentSnapshot.sessionId != sessionId || currentSnapshot.targetMac != targetMac) {
            Timber.d("Skipping stale playback start for %s session=%s", targetMac, sessionId)
            return
        }

        val configuredTargetMac = settingsRepository.targetMacFlow.firstOrNull()
        val autoPlayEnabled = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true
        val autoPlayOnAndroidAuto = settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
        val matchesConfiguredTarget = matchesConfiguredTargetMac(configuredTargetMac, targetMac)
        val canStartPlayback = autoPlayEnabled &&
            matchesConfiguredTarget &&
            (!viaAndroidAuto || autoPlayOnAndroidAuto)
        if (!canStartPlayback) {
            Timber.d(
                "Cancelling playback start for %s. viaAndroidAuto=%s autoPlayEnabled=%s configuredTarget=%s autoPlayOnAA=%s",
                targetMac,
                viaAndroidAuto,
                autoPlayEnabled,
                configuredTargetMac,
                autoPlayOnAndroidAuto
            )
            handoffStore.markCancelled(sessionId)
            handoffStore.finishCancellation(sessionId)
            return
        }

        if (!viaAndroidAuto) {
            val connectionStartDelaySeconds =
                (settingsRepository.connectionStartDelaySecondsFlow.firstOrNull() ?: 0).coerceIn(0, 10)
            if (connectionStartDelaySeconds > 0) {
                Timber.d(
                    "Applying connection start delay of %ds for %s before Bluetooth-path playback",
                    connectionStartDelaySeconds,
                    targetMac
                )
                delay(connectionStartDelaySeconds * 1000L)

                val delayedSnapshot = handoffStore.snapshot()
                if (delayedSnapshot.sessionId != sessionId || delayedSnapshot.targetMac != targetMac) {
                    Timber.d("Skipping stale delayed playback start for %s session=%s", targetMac, sessionId)
                    return
                }

                val delayedConfiguredTargetMac = settingsRepository.targetMacFlow.firstOrNull()
                val delayedAutoPlayEnabled = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true
                val delayedAutoPlayOnAndroidAuto =
                    settingsRepository.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
                val delayedMatchesConfiguredTarget =
                    matchesConfiguredTargetMac(delayedConfiguredTargetMac, targetMac)
                val delayedCanStartPlayback = delayedAutoPlayEnabled &&
                    delayedMatchesConfiguredTarget &&
                    (!viaAndroidAuto || delayedAutoPlayOnAndroidAuto)
                if (!delayedCanStartPlayback) {
                    Timber.d(
                        "Cancelling delayed playback start for %s. viaAndroidAuto=%s autoPlayEnabled=%s configuredTarget=%s autoPlayOnAA=%s",
                        targetMac,
                        viaAndroidAuto,
                        delayedAutoPlayEnabled,
                        delayedConfiguredTargetMac,
                        delayedAutoPlayOnAndroidAuto
                    )
                    handoffStore.markCancelled(sessionId)
                    handoffStore.finishCancellation(sessionId)
                    return
                }
            }
        }
        stopDebouncer.cancelPending(targetMac)
        val serviceIntent = Intent(appContext, AutoPlayMusicService::class.java).apply {
            action = AutoPlayMusicService.ACTION_START_PLAYBACK
        }
        ContextCompat.startForegroundService(appContext, serviceIntent)
        if (viaAndroidAuto) {
            handoffStore.markCompleted(sessionId)
            handoffStore.armStopVerification(sessionId, elapsedRealtimeMs() + stopVerificationDelayMs)
        } else {
            handoffStore.clearStopVerification(sessionId)
        }
        Timber.d("Started playback for %s viaAndroidAuto=%s", targetMac, viaAndroidAuto)
    }

    internal suspend fun handleDisconnect(appContext: Context, targetMac: String) {
        val connectionAction = bluetoothConnectionHandler.handle(BluetoothEventType.DISCONNECTED, targetMac)
        if (connectionAction !is BluetoothConnectionAction.StopPlayback) {
            Timber.d("Ignoring disconnect for non-target MAC %s", targetMac)
            return
        }

        if (startDebouncer.cancelPending(targetMac)) {
            Timber.d("Cancelled pending start for %s after disconnect", targetMac)
        }

        val snapshot = handoffStore.snapshot()
        val isWaitingForAndroidAuto =
            snapshot.targetMac == targetMac &&
                snapshot.state in setOf(
                    AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO,
                    AndroidAutoHandoffState.AA_READY_CONFIRMING,
                    AndroidAutoHandoffState.AA_READY_STARTING
                )
        if (isWaitingForAndroidAuto) {
            handoffStore.markDisconnected(targetMac)
            stopDebouncer.cancelPending(targetMac)
            return
        }

        val viaAndroidAuto = snapshot.targetMac == targetMac && snapshot.state == AndroidAutoHandoffState.COMPLETED
        if (!viaAndroidAuto) {
            if (snapshot.targetMac == targetMac) {
                handoffStore.markDisconnected(targetMac)
            }
            stopDebouncer.cancelPending(targetMac)
            stopPlayback(appContext)
            return
        }

        val sessionId = snapshot.sessionId ?: return
        val readinessSnapshot = sampleReadinessSnapshotOrNull(appContext, sessionId) ?: return
        if (!AndroidAutoReadinessProbe.isCandidate(readinessSnapshot)) {
            handoffStore.clearStopVerification(sessionId)
            stopPlayback(appContext)
            handoffStore.markDisconnected(targetMac)
            return
        }

        scheduleStopVerification(appContext, sessionId, targetMac)
    }

    private fun scheduleStopVerification(appContext: Context, sessionId: Long, targetMac: String) {
        val dueAtElapsedMs = elapsedRealtimeMs() + stopVerificationDelayMs
        handoffStore.armStopVerification(sessionId, dueAtElapsedMs)
        stopDebouncer.cancelPending(targetMac)
        stopDebouncer.schedule(targetMac) {
            receiverScope.launch {
                handleStopVerification(appContext, sessionId, targetMac, dueAtElapsedMs)
            }
        }
        Timber.d("Scheduled stop verification for %s at %d", targetMac, dueAtElapsedMs)
    }

    private suspend fun handleStopVerification(
        appContext: Context,
        sessionId: Long,
        targetMac: String,
        dueAtElapsedMs: Long
    ) {
        val snapshot = handoffStore.snapshot()
        if (snapshot.sessionId != sessionId || snapshot.targetMac != targetMac) {
            Timber.d("Ignoring stale stop verification for %s session=%s", targetMac, sessionId)
            return
        }
        if (snapshot.pendingStopVerificationAtElapsedMs != dueAtElapsedMs) {
            Timber.d("Ignoring outdated stop verification for %s dueAt=%d currentDue=%s", targetMac, dueAtElapsedMs, snapshot.pendingStopVerificationAtElapsedMs)
            return
        }

        if (isTargetStillConnected(targetMac)) {
            handoffStore.clearStopVerification(sessionId)
            Timber.d("Ignoring stop verification for %s because target is connected again", targetMac)
            return
        }

        val readinessSnapshot = sampleReadinessSnapshotOrNull(appContext, sessionId) ?: return
        if (hasAnyFastReadinessSignals(readinessSnapshot)) {
            scheduleStopVerification(appContext, sessionId, targetMac)
            return
        }

        handoffStore.clearStopVerification(sessionId)
        stopPlayback(appContext)
        handoffStore.markDisconnected(targetMac)
    }

    private fun stopPlayback(appContext: Context) {
        val serviceIntent = Intent(appContext, AutoPlayMusicService::class.java).apply {
            action = AutoPlayMusicService.ACTION_STOP_PLAYBACK
        }
        appContext.startService(serviceIntent)
    }

    internal fun sampleReadinessSnapshotOrNull(
        appContext: Context,
        sessionId: Long
    ): AndroidAutoReadinessSnapshot? {
        val storeSnapshot = handoffStore.snapshot()
        if (storeSnapshot.sessionId != sessionId) {
            return null
        }
        val probe = AndroidAutoReadinessProbe(appContext, bluetoothAdapterRepo)
        val fastSignals = probe.sampleFastSignals()
        val latestStoreSnapshot = handoffStore.snapshot()
        if (latestStoreSnapshot.sessionId != sessionId) {
            return null
        }
        return AndroidAutoReadinessProbe.combine(
            isAndroidAutoTargetDevice = latestStoreSnapshot.isAndroidAutoTargetDevice,
            fastSignals = fastSignals
        )
    }

    private suspend fun isTargetStillConnected(targetMac: String): Boolean {
        return bluetoothAdapterRepo.getConnectedA2dpAddresses().contains(targetMac)
    }

    private fun hasAnyFastReadinessSignals(snapshot: AndroidAutoReadinessSnapshot): Boolean {
        return snapshot.hasGearheadProcess || snapshot.isCarMode || snapshot.hasRemoteSubmixOutput
    }

    private fun matchesConfiguredTargetMac(configuredTargetMac: String?, eventMac: String?): Boolean {
        if (configuredTargetMac.isNullOrBlank() || eventMac.isNullOrBlank()) {
            return false
        }
        return configuredTargetMac.equals(eventMac, ignoreCase = true)
    }

    /**
     * Battery-aware polling interval calculation.
     * Giảm tần suất polling khi pin thấp để tiết kiệm pin.
     */
    private fun getAdaptivePollInterval(context: Context, baseIntervalMs: Long): Long {
        return try {
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
            
            // Kiểm tra có đang sạc không
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == android.os.BatteryManager.BATTERY_STATUS_FULL

            when {
                // Pin < 20% và không sạc: polling rất chậm
                batteryPct < 20 && !isCharging -> (baseIntervalMs * 4).coerceAtMost(10_000L)
                // Pin 20-50% và không sạc: polling chậm vừa
                batteryPct < 50 && !isCharging -> (baseIntervalMs * 2).coerceAtMost(5_000L)
                // Pin > 50% hoặc đang sạc: polling bình thường
                else -> baseIntervalMs
            }
        } catch (e: Exception) {
            // Nếu lỗi, dùng interval mặc định
            Timber.w(e, "Failed to get battery level, using default interval")
            baseIntervalMs
        }
    }

    private fun logReadinessTransition(
        sessionId: Long,
        targetMac: String,
        newState: AndroidAutoHandoffState,
        snapshot: AndroidAutoReadinessSnapshot
    ) {
        val currentSnapshot = handoffStore.snapshot()
        if (currentSnapshot.sessionId != sessionId || currentSnapshot.targetMac != targetMac) {
            return
        }
        if (currentSnapshot.state == newState) {
            return
        }
        Timber.d(
            "AA readiness state=%s sessionId=%s targetMac=%s gearhead=%s carMode=%s remoteSubmix=%s isTargetDevice=%s",
            newState,
            sessionId,
            targetMac,
            snapshot.hasGearheadProcess,
            snapshot.isCarMode,
            snapshot.hasRemoteSubmixOutput,
            snapshot.isAndroidAutoTargetDevice
        )
    }

    private fun isCarMode(context: Context): Boolean {
        return try {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR
        } catch (exception: Exception) {
            Timber.d(exception, "Failed to read car mode, assume false")
            false
        }
    }

    companion object {
        private const val START_PLAYBACK_DELAY_MS = 500L
        private const val STATE_ON_FALLBACK_DELAY_MS = 1500L
        private const val READINESS_POLL_INTERVAL_MS = 500L
        private const val READINESS_POLL_FAST_WINDOW_MS = 15_000L
        private const val READINESS_POLL_SLOW_INTERVAL_MS = 2_000L
        private const val STOP_VERIFICATION_DELAY_MS = 6_000L
        
        // Tối ưu hiệu năng: Giới hạn polling
        private const val MAX_POLL_COUNT = 20
        private const val TOTAL_POLL_TIMEOUT_MS = 30_000L  // 30 giây
        private const val MAX_POLL_INTERVAL_MS = 3_000L    // Max 3 giây
        
        internal const val EXTRA_DEVICE_MAC_FALLBACK = "com.vibegravity.bluecruise.extra.DEVICE_MAC_FALLBACK"
        
        // Tối ưu: Dùng shared scope thay vì nhiều scope riêng biệt
        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        private val sharedStartDebouncer = PlaybackStartDebouncer(
            scope = sharedScope,
            delayMs = START_PLAYBACK_DELAY_MS
        )
        private val sharedStopDebouncer = PlaybackStopDebouncer(
            scope = sharedScope,
            delayMs = STOP_VERIFICATION_DELAY_MS
        )
        
        // Cleanup khi không cần thiết
        fun cancelAllPendingOperations() {
            sharedScope.coroutineContext.cancelChildren()
            Timber.d("Cancelled all pending coroutines in shared scope")
        }
    }
}

internal object KeepAliveServiceRestorer {

    suspend fun restoreIfEnabled(
        appContext: Context,
        settingsRepository: SettingsRepository,
        isBluetoothEnabled: Boolean = false
    ) {
        // Điều kiện 1: Keep App Alive phải được bật
        if (settingsRepository.keepAppAliveFlow.firstOrNull() != true) {
            Timber.d("BOOT_COMPLETED: Keep App Alive disabled, skipping restore.")
            return
        }

        // Điều kiện 2: Phải có target device đã cấu hình
        val targetMac = settingsRepository.targetMacFlow.firstOrNull()
        if (targetMac.isNullOrBlank()) {
            Timber.d("BOOT_COMPLETED: No target device configured, skipping KeepAlive.")
            return
        }

        // Điều kiện 3: Bluetooth phải đang bật (nếu được truyền vào)
        if (!isBluetoothEnabled) {
            Timber.d("BOOT_COMPLETED: Bluetooth is not enabled, skipping KeepAlive.")
            return
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val serviceIntent = Intent(appContext, KeepAliveService::class.java)
            ContextCompat.startForegroundService(appContext, serviceIntent)
            Timber.d("BOOT_COMPLETED: restored KeepAliveService for target $targetMac.")
        } catch (exception: Exception) {
            Timber.e(exception, "BOOT_COMPLETED: failed to restore KeepAliveService")
        }
    }
}


