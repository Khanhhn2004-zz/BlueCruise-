package com.vibegravity.bluecruise.service

class AndroidAutoRetryPolicy {
    private val aftermarketStablePartialSignalWindowMs = 5_000L

    fun delayBeforeAttempt(attempt: Int): Long = when (attempt) {
        1 -> 20_000L
        2 -> 45_000L
        3 -> 90_000L
        4 -> 150_000L
        else -> 90_000L
    }

    fun preparationFallbackDeadlineElapsedMs(
        sessionStartedAtElapsedMs: Long,
        attempt: Int,
        firstPartialSignalElapsedMs: Long?
    ): Long {
        val maxDeadlineElapsedMs = sessionStartedAtElapsedMs + delayBeforeAttempt(attempt)
        val earlyDeadlineElapsedMs = firstPartialSignalElapsedMs?.plus(aftermarketStablePartialSignalWindowMs)
        return if (earlyDeadlineElapsedMs == null) {
            maxDeadlineElapsedMs
        } else {
            minOf(maxDeadlineElapsedMs, earlyDeadlineElapsedMs)
        }
    }
}
