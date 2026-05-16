package com.vibegravity.bluecruise.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAutoRetryPolicyTest {

    @Test
    fun `retry policy uses long delays for aftermarket android auto`() {
        val policy = AndroidAutoRetryPolicy()

        assertEquals(20_000L, policy.delayBeforeAttempt(1))
        assertEquals(45_000L, policy.delayBeforeAttempt(2))
        assertEquals(90_000L, policy.delayBeforeAttempt(3))
        assertEquals(150_000L, policy.delayBeforeAttempt(4))
        assertEquals(90_000L, policy.delayBeforeAttempt(5))
    }

    @Test
    fun `preparation deadline stays on max window when no aftermarket partial signal exists`() {
        val policy = AndroidAutoRetryPolicy()

        assertEquals(
            20_000L,
            policy.preparationFallbackDeadlineElapsedMs(
                sessionStartedAtElapsedMs = 0L,
                attempt = 1,
                firstPartialSignalElapsedMs = null
            )
        )
    }

    @Test
    fun `preparation deadline shortens when aftermarket partial signal stays stable`() {
        val policy = AndroidAutoRetryPolicy()

        assertEquals(
            15_000L,
            policy.preparationFallbackDeadlineElapsedMs(
                sessionStartedAtElapsedMs = 0L,
                attempt = 1,
                firstPartialSignalElapsedMs = 10_000L
            )
        )
    }
}
