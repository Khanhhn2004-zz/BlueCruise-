package com.vibegravity.bluecruise.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackRuntimeState(
    val activeSlot: Int? = null,
    val lastRequestedSlot: Int? = null,
    val isPlaying: Boolean = false,
    val isTransitionPending: Boolean = false
)

@Singleton
class PlaybackRuntimeStateStore @Inject constructor() {

    private val mutableState = MutableStateFlow(PlaybackRuntimeState())
    val state: StateFlow<PlaybackRuntimeState> = mutableState.asStateFlow()

    fun markStartPending(slot: Int) {
        mutableState.value = PlaybackRuntimeState(
            activeSlot = slot,
            lastRequestedSlot = slot,
            isPlaying = false,
            isTransitionPending = true
        )
    }

    fun markPlaying(slot: Int) {
        mutableState.value = PlaybackRuntimeState(
            activeSlot = slot,
            lastRequestedSlot = slot,
            isPlaying = true,
            isTransitionPending = false
        )
    }

    fun markIdle() {
        mutableState.value = mutableState.value.copy(
            activeSlot = null,
            isPlaying = false,
            isTransitionPending = false
        )
    }

    fun reset() {
        mutableState.value = PlaybackRuntimeState()
    }
}
