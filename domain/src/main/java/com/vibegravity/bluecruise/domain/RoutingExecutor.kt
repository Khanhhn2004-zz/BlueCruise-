package com.vibegravity.bluecruise.domain

/**
 * Abstraction for executing audio routing "exploits" (tier 1–3) to nudge car head units to Bluetooth.
 * Implemented by Android-specific code in :app; [mediaSessionToken] is opaque to domain (e.g. MediaSessionCompat in app).
 */
interface RoutingExecutor {
    suspend fun executeRoutingExploit(tier: Int, mediaSessionToken: Any?)
}
