package com.vibegravity.bluecruise.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBubbleServiceTest {

    private val viewport = FloatingBubbleViewport(
        width = 1080,
        height = 1920,
        bubbleWidth = 68,
        bubbleHeight = 120,
        edgeInset = 16,
        topInset = 24,
        bottomInset = 40
    )

    @Test
    fun `tapping active slot while playback is active resolves to stop without optimistic state change`() {
        val result = resolveFloatingBubbleTap(
            tappedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
            state = FloatingBubblePlaybackUiState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaybackActive = true,
                isPlaybackTransitionPending = false
            )
        )

        assertEquals(FloatingBubblePlaybackCommand.Stop, result.command)
    }

    @Test
    fun `switching from song two to song one emits start without optimistic state swap`() {
        val result = resolveFloatingBubbleTap(
            tappedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
            state = FloatingBubblePlaybackUiState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaybackActive = true,
                isPlaybackTransitionPending = false
            )
        )

        assertEquals(
            FloatingBubblePlaybackCommand.Start(AutoPlayMusicService.AUDIO_SLOT_PRIMARY),
            result.command
        )
    }

    @Test
    fun `runtime pending state maps directly into bubble ui state`() {
        val reduced = mapPlaybackRuntimeStateToBubbleUiState(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
                isPlaying = false,
                isTransitionPending = true
            )
        )

        assertEquals(AutoPlayMusicService.AUDIO_SLOT_PRIMARY, reduced.activeSlot)
        assertFalse(reduced.isPlaybackActive)
        assertTrue(reduced.isPlaybackTransitionPending)
    }

    @Test
    fun `runtime active state maps directly into bubble ui state`() {
        val reduced = mapPlaybackRuntimeStateToBubbleUiState(
            PlaybackRuntimeState(
                activeSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                lastRequestedSlot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
                isPlaying = true,
                isTransitionPending = false
            )
        )

        assertEquals(AutoPlayMusicService.AUDIO_SLOT_SECONDARY, reduced.activeSlot)
        assertTrue(reduced.isPlaybackActive)
        assertFalse(reduced.isPlaybackTransitionPending)
    }

    @Test
    fun `clamp keeps bubble inside viewport bounds`() {
        val clamped = clampFloatingBubblePosition(
            position = FloatingBubblePoint(x = 1040, y = 1888),
            viewport = viewport
        )

        assertEquals(996, clamped.x)
        assertEquals(1760, clamped.y)
    }

    @Test
    fun `drop snaps bubble to left edge when closer to left`() {
        val result = resolveFloatingBubbleDrop(
            position = FloatingBubblePoint(x = 180, y = 440),
            viewport = viewport,
            dismissTarget = FloatingBubbleRect(left = 430, top = 1700, right = 650, bottom = 1840)
        )

        assertFalse(result.shouldDismiss)
        assertEquals(FloatingBubblePoint(x = 16, y = 440), result.position)
    }

    @Test
    fun `drop snaps bubble to right edge when closer to right`() {
        val result = resolveFloatingBubbleDrop(
            position = FloatingBubblePoint(x = 760, y = 440),
            viewport = viewport,
            dismissTarget = FloatingBubbleRect(left = 430, top = 1700, right = 650, bottom = 1840)
        )

        assertFalse(result.shouldDismiss)
        assertEquals(FloatingBubblePoint(x = 996, y = 440), result.position)
    }

    @Test
    fun `drop over dismiss target hides floating bubble`() {
        val result = resolveFloatingBubbleDrop(
            position = FloatingBubblePoint(x = 500, y = 1675),
            viewport = viewport,
            dismissTarget = FloatingBubbleRect(left = 430, top = 1700, right = 650, bottom = 1840)
        )

        assertTrue(result.shouldDismiss)
    }

    @Test
    fun `dismiss target is centered and lifted above bottom edge`() {
        val rect = resolveFloatingBubbleDismissTargetRect(
            viewport = viewport,
            diameter = 96,
            bottomMargin = 72
        )

        assertEquals(FloatingBubbleRect(left = 492, top = 1752, right = 588, bottom = 1848), rect)
    }

    @Test
    fun `dismiss indicator uses vivid red circle with white x`() {
        val appearance = resolveFloatingBubbleDismissAppearance(isHovering = false)
        val hoveringAppearance = resolveFloatingBubbleDismissAppearance(isHovering = true)

        assertEquals(0xFFFF4D4F.toInt(), appearance.backgroundColor)
        assertEquals(0xFFFF6B6B.toInt(), hoveringAppearance.backgroundColor)
        assertEquals(0x66FFFFFF, appearance.strokeColor)
        assertEquals(0xFFFFFFFF.toInt(), appearance.slashColor)
        assertEquals(4, appearance.slashThicknessDp)
        assertEquals(listOf(-45f, 45f), resolveFloatingBubbleDismissCrossRotations())
    }

    @Test
    fun `inactive bubble buttons use greeting and goodbye semantics by slot`() {
        val greeting = resolveFloatingBubbleButtonVisual(
            slot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
            isActive = false
        )
        val goodbye = resolveFloatingBubbleButtonVisual(
            slot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
            isActive = false
        )

        assertEquals(FloatingBubbleButtonVisual.Icon.GREETING, greeting.icon)
        assertEquals(FloatingBubbleButtonVisual.Icon.GOODBYE, goodbye.icon)
        assertEquals(FloatingBubbleButtonVisual.Tone.COOL, greeting.tone)
        assertEquals(FloatingBubbleButtonVisual.Tone.WARM, goodbye.tone)
    }
}
