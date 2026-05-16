package com.vibegravity.bluecruise.service

data class PlaybackSessionSnapshot(
    val audioUri: String?,
    val displayTitle: String?,
    val targetMac: String?,
    val aaEnabledForTarget: Boolean,
    val resumeEligible: Boolean,
    val startPositionMs: Long = 0L,
    val preparedAtElapsedMs: Long?
)
