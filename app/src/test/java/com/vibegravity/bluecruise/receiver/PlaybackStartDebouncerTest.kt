package com.vibegravity.bluecruise.receiver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStartDebouncerTest {

    @Test
    fun `cancelPending prevents previously scheduled start from running`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val debouncer = PlaybackStartDebouncer(
            scope = scope,
            delayMs = 500L
        )
        var startCount = 0

        debouncer.schedule(deviceMac = "00:11:22:33:44:55") {
            startCount += 1
        }
        advanceTimeBy(250L)

        val cancelled = debouncer.cancelPending("00:11:22:33:44:55")
        advanceTimeBy(500L)
        runCurrent()

        assertTrue(cancelled)
        assertEquals(0, startCount)
    }

    @Test
    fun `schedule with skipIfSamePending ignores duplicate pending start`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val debouncer = PlaybackStartDebouncer(
            scope = scope,
            delayMs = 500L
        )
        var firstStartCount = 0
        var secondStartCount = 0

        val firstScheduled = debouncer.schedule(
            deviceMac = "00:11:22:33:44:55",
            skipIfSamePending = true
        ) {
            firstStartCount += 1
        }
        val secondScheduled = debouncer.schedule(
            deviceMac = "00:11:22:33:44:55",
            skipIfSamePending = true
        ) {
            secondStartCount += 1
        }
        advanceTimeBy(500L)
        runCurrent()

        assertTrue(firstScheduled)
        assertFalse(secondScheduled)
        assertEquals(1, firstStartCount)
        assertEquals(0, secondStartCount)
    }
}
