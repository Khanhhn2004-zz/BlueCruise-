package com.vibegravity.bluecruise.receiver

import androidx.annotation.VisibleForTesting

internal enum class AndroidAutoHandoffState {
    IDLE,
    WAITING_FOR_ANDROID_AUTO,
    AA_READY_CONFIRMING,
    AA_READY_STARTING,
    COMPLETED,
    CANCELLED
}

internal data class AndroidAutoSessionHandle(
    val sessionId: Long,
    val targetMac: String,
    val isFreshSession: Boolean
)

internal data class AndroidAutoHandoffSessionSnapshot(
    val sessionId: Long?,
    val targetMac: String?,
    val isAndroidAutoTargetDevice: Boolean,
    val state: AndroidAutoHandoffState,
    val timedOut: Boolean,
    val pendingStopVerificationAtElapsedMs: Long?
)

internal class AndroidAutoHandoffSessionStore private constructor() {
    private val lock = Any()
    private var nextSessionId = 1L
    private var currentSessionId: Long? = null
    private var currentTargetMac: String? = null
    private var currentIsAndroidAutoTargetDevice = false
    private var currentState = AndroidAutoHandoffState.IDLE
    private var currentTimedOut = false
    private var pendingStopVerificationAtElapsedMs: Long? = null

    fun beginOrReuseSession(
        targetMac: String,
        isAndroidAutoTargetDevice: Boolean
    ): AndroidAutoSessionHandle = synchronized(lock) {
        val existingSessionId = currentSessionId
        if (existingSessionId != null && currentTargetMac == targetMac) {
            currentIsAndroidAutoTargetDevice =
                currentIsAndroidAutoTargetDevice || isAndroidAutoTargetDevice
            return@synchronized AndroidAutoSessionHandle(
                sessionId = existingSessionId,
                targetMac = targetMac,
                isFreshSession = false
            )
        }

        if (existingSessionId != null && currentTargetMac != targetMac) {
            clearCurrentSession()
        }

        val newSessionId = nextSessionId++
        currentSessionId = newSessionId
        currentTargetMac = targetMac
        currentIsAndroidAutoTargetDevice = isAndroidAutoTargetDevice
        currentState = AndroidAutoHandoffState.IDLE
        currentTimedOut = false
        pendingStopVerificationAtElapsedMs = null

        AndroidAutoSessionHandle(
            sessionId = newSessionId,
            targetMac = targetMac,
            isFreshSession = true
        )
    }

    fun transitionTo(sessionId: Long, state: AndroidAutoHandoffState) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        currentState = state
    }

    fun markCancelled(sessionId: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        currentState = AndroidAutoHandoffState.CANCELLED
        currentTimedOut = false
    }

    fun markTimedOut(sessionId: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        currentState = AndroidAutoHandoffState.CANCELLED
        currentTimedOut = true
    }

    fun markDisconnected(targetMac: String) = synchronized(lock) {
        if (currentTargetMac != targetMac) return
        clearCurrentSession()
    }

    fun finishCancellation(sessionId: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        if (currentState == AndroidAutoHandoffState.CANCELLED) {
            currentState = AndroidAutoHandoffState.IDLE
        }
    }

    fun markBluetoothRestarted() = synchronized(lock) {
        clearCurrentSession()
    }

    fun markMacReplacement(newTargetMac: String) = synchronized(lock) {
        if (currentSessionId == null) return
        if (currentTargetMac != newTargetMac) {
            clearCurrentSession()
        }
    }

    fun markCompleted(sessionId: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        currentState = AndroidAutoHandoffState.COMPLETED
    }

    fun armStopVerification(sessionId: Long, dueAtElapsedMs: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        pendingStopVerificationAtElapsedMs = dueAtElapsedMs
    }

    fun clearStopVerification(sessionId: Long) = synchronized(lock) {
        if (!isCurrentSession(sessionId)) return
        pendingStopVerificationAtElapsedMs = null
    }

    fun snapshot(): AndroidAutoHandoffSessionSnapshot = synchronized(lock) {
        AndroidAutoHandoffSessionSnapshot(
            sessionId = currentSessionId,
            targetMac = currentTargetMac,
            isAndroidAutoTargetDevice = currentIsAndroidAutoTargetDevice,
            state = currentState,
            timedOut = currentTimedOut,
            pendingStopVerificationAtElapsedMs = pendingStopVerificationAtElapsedMs
        )
    }

    private fun isCurrentSession(sessionId: Long): Boolean {
        return currentSessionId == sessionId
    }

    private fun clearCurrentSession() {
        currentSessionId = null
        currentTargetMac = null
        currentIsAndroidAutoTargetDevice = false
        currentState = AndroidAutoHandoffState.IDLE
        currentTimedOut = false
        pendingStopVerificationAtElapsedMs = null
    }

    companion object {
        @Volatile
        private var sharedStore: AndroidAutoHandoffSessionStore? = null

        fun shared(): AndroidAutoHandoffSessionStore {
            return sharedStore ?: synchronized(this) {
                sharedStore ?: AndroidAutoHandoffSessionStore().also { sharedStore = it }
            }
        }

        @VisibleForTesting
        fun resetForTest() {
            synchronized(this) {
                sharedStore = AndroidAutoHandoffSessionStore()
            }
        }
    }
}
