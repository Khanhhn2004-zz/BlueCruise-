package com.vibegravity.bluecruise.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRuntimeStateStoreTest {

    @Test
    fun `store starts idle`() = runTest {
        val store = PlaybackRuntimeStateStore()

        assertEquals(PlaybackRuntimeState(), store.state.value)
    }

    @Test
    fun `markStartPending tracks requested slot without marking playback active`() = runTest {
        val store = PlaybackRuntimeStateStore()

        store.markStartPending(AutoPlayMusicService.AUDIO_SLOT_SECONDARY)

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaying = false,
                isTransitionPending = true
            ),
            store.state.value
        )
    }

    @Test
    fun `markPlaying settles the pending slot into active playback`() = runTest {
        val store = PlaybackRuntimeStateStore()

        store.markStartPending(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)
        store.markPlaying(AutoPlayMusicService.AUDIO_SLOT_PRIMARY)

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = true,
                isTransitionPending = false
            ),
            store.state.value
        )
    }

    @Test
    fun `markIdle clears active playback but remembers the last requested slot`() = runTest {
        val store = PlaybackRuntimeStateStore()

        store.markPlaying(AutoPlayMusicService.AUDIO_SLOT_SECONDARY)
        store.markIdle()

        assertEquals(
            PlaybackRuntimeState(
                activeSlot = null,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaying = false,
                isTransitionPending = false
            ),
            store.state.value
        )
    }
}
