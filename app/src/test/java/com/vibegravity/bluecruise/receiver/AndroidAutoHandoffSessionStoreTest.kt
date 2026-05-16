package com.vibegravity.bluecruise.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidAutoHandoffSessionStoreTest {

    @Before
    fun resetSharedStore() {
        AndroidAutoHandoffSessionStore.resetForTest()
    }

    @Test
    fun `duplicate trigger reuses the same active session across waiting confirming starting and completed states`() {
        val activeStates = listOf(
            AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO,
            AndroidAutoHandoffState.AA_READY_CONFIRMING,
            AndroidAutoHandoffState.AA_READY_STARTING,
            AndroidAutoHandoffState.COMPLETED
        )

        activeStates.forEach { state ->
            AndroidAutoHandoffSessionStore.resetForTest()
            val store = AndroidAutoHandoffSessionStore.shared()
            val first = store.beginOrReuseSession("32:64:36:35:31:64", true)
            store.transitionTo(first.sessionId, state)

            val duplicate = store.beginOrReuseSession("32:64:36:35:31:64", false)

            assertEquals(first.sessionId, duplicate.sessionId)
            assertFalse(duplicate.isFreshSession)
            assertEquals(state, store.snapshot().state)
            assertTrue(store.snapshot().isAndroidAutoTargetDevice)
        }
    }

    @Test
    fun `timed out session stays blocked after cleanup until disconnect adapter restart or mac replacement`() {
        val store = AndroidAutoHandoffSessionStore.shared()
        val first = store.beginOrReuseSession("32:64:36:35:31:64", true)
        store.markTimedOut(first.sessionId)

        assertEquals(AndroidAutoHandoffState.CANCELLED, store.snapshot().state)
        assertTrue(store.snapshot().timedOut)

        store.finishCancellation(first.sessionId)
        assertEquals(AndroidAutoHandoffState.IDLE, store.snapshot().state)

        val duplicate = store.beginOrReuseSession("32:64:36:35:31:64", false)
        assertEquals(first.sessionId, duplicate.sessionId)
        assertFalse(duplicate.isFreshSession)

        store.markDisconnected("32:64:36:35:31:64")
        val afterDisconnect = store.beginOrReuseSession("32:64:36:35:31:64", false)
        store.armStopVerification(afterDisconnect.sessionId, dueAtElapsedMs = 6_000L)
        store.markBluetoothRestarted()
        val afterRestart = store.beginOrReuseSession("32:64:36:35:31:64", false)
        store.armStopVerification(afterRestart.sessionId, dueAtElapsedMs = 6_000L)
        store.markMacReplacement("AA:BB:CC:DD:EE:FF")
        val afterReplacement = store.beginOrReuseSession("AA:BB:CC:DD:EE:FF", false)

        assertTrue(afterDisconnect.sessionId > first.sessionId)
        assertTrue(afterRestart.sessionId > afterDisconnect.sessionId)
        assertTrue(afterReplacement.sessionId > afterRestart.sessionId)
        assertNull(store.snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `settings cancelled session stays blocked after cleanup until disconnect adapter restart or mac replacement`() {
        val store = AndroidAutoHandoffSessionStore.shared()
        val first = store.beginOrReuseSession("32:64:36:35:31:64", true)

        store.transitionTo(first.sessionId, AndroidAutoHandoffState.WAITING_FOR_ANDROID_AUTO)
        store.markCancelled(first.sessionId)
        store.finishCancellation(first.sessionId)

        assertEquals(AndroidAutoHandoffState.IDLE, store.snapshot().state)

        val duplicate = store.beginOrReuseSession("32:64:36:35:31:64", false)
        assertEquals(first.sessionId, duplicate.sessionId)
        assertFalse(duplicate.isFreshSession)

        store.markDisconnected("32:64:36:35:31:64")
        val afterDisconnect = store.beginOrReuseSession("32:64:36:35:31:64", false)
        store.armStopVerification(afterDisconnect.sessionId, dueAtElapsedMs = 6_000L)
        store.markBluetoothRestarted()
        val afterRestart = store.beginOrReuseSession("32:64:36:35:31:64", false)
        store.armStopVerification(afterRestart.sessionId, dueAtElapsedMs = 6_000L)
        store.markMacReplacement("AA:BB:CC:DD:EE:FF")
        val afterReplacement = store.beginOrReuseSession("AA:BB:CC:DD:EE:FF", false)

        assertTrue(afterDisconnect.sessionId > first.sessionId)
        assertTrue(afterRestart.sessionId > afterDisconnect.sessionId)
        assertTrue(afterReplacement.sessionId > afterRestart.sessionId)
        assertNull(store.snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `stale session mutators are ignored after a fresh boundary`() {
        val store = AndroidAutoHandoffSessionStore.shared()
        val first = store.beginOrReuseSession("32:64:36:35:31:64", true)
        store.markDisconnected("32:64:36:35:31:64")
        store.finishCancellation(first.sessionId)
        val second = store.beginOrReuseSession("32:64:36:35:31:64", false)

        store.transitionTo(first.sessionId, AndroidAutoHandoffState.COMPLETED)
        store.armStopVerification(first.sessionId, dueAtElapsedMs = 12_000L)
        store.markCompleted(first.sessionId)
        store.markCancelled(first.sessionId)
        store.markTimedOut(first.sessionId)
        store.finishCancellation(first.sessionId)

        assertEquals(second.sessionId, store.snapshot().sessionId)
        assertEquals(AndroidAutoHandoffState.IDLE, store.snapshot().state)
        assertNull(store.snapshot().pendingStopVerificationAtElapsedMs)
    }

    @Test
    fun `markCompleted and stop verification metadata are observable through snapshot`() {
        val store = AndroidAutoHandoffSessionStore.shared()
        val handle = store.beginOrReuseSession("32:64:36:35:31:64", true)

        store.transitionTo(handle.sessionId, AndroidAutoHandoffState.AA_READY_STARTING)
        store.armStopVerification(handle.sessionId, dueAtElapsedMs = 12_000L)
        store.markCompleted(handle.sessionId)

        val completed = store.snapshot()
        store.clearStopVerification(handle.sessionId)

        assertEquals(AndroidAutoHandoffState.COMPLETED, completed.state)
        assertEquals(12_000L, completed.pendingStopVerificationAtElapsedMs)
        assertNull(store.snapshot().pendingStopVerificationAtElapsedMs)
    }
}
