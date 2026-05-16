package com.vibegravity.bluecruise.service

internal data class FloatingBubbleViewport(
    val width: Int,
    val height: Int,
    val bubbleWidth: Int,
    val bubbleHeight: Int,
    val edgeInset: Int,
    val topInset: Int,
    val bottomInset: Int
)

internal data class FloatingBubblePoint(
    val x: Int,
    val y: Int
)

internal data class FloatingBubbleRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun contains(x: Int, y: Int): Boolean {
        return x in left..right && y in top..bottom
    }
}

internal data class FloatingBubbleDropResult(
    val position: FloatingBubblePoint,
    val shouldDismiss: Boolean
)

internal data class FloatingBubbleDismissAppearance(
    val backgroundColor: Int,
    val strokeColor: Int,
    val slashColor: Int,
    val slashThicknessDp: Int
)

internal data class FloatingBubbleButtonVisual(
    val icon: Icon,
    val tone: Tone
) {
    internal enum class Icon {
        GREETING,
        GOODBYE,
        STOP
    }

    internal enum class Tone {
        COOL,
        WARM
    }
}

internal data class FloatingBubblePlaybackUiState(
    val activeSlot: Int? = null,
    val isPlaybackActive: Boolean = false,
    val isPlaybackTransitionPending: Boolean = false
)

internal data class FloatingBubbleTapResult(
    val command: FloatingBubblePlaybackCommand
)

internal fun mapPlaybackRuntimeStateToBubbleUiState(
    state: PlaybackRuntimeState
): FloatingBubblePlaybackUiState {
    return FloatingBubblePlaybackUiState(
        activeSlot = state.activeSlot,
        isPlaybackActive = state.isPlaying,
        isPlaybackTransitionPending = state.isTransitionPending
    )
}

internal fun resolveFloatingBubbleTap(
    tappedSlot: Int,
    state: FloatingBubblePlaybackUiState
): FloatingBubbleTapResult {
    return if (state.activeSlot == tappedSlot && (state.isPlaybackActive || state.isPlaybackTransitionPending)) {
        FloatingBubbleTapResult(
            command = FloatingBubblePlaybackCommand.Stop
        )
    } else {
        FloatingBubbleTapResult(
            command = FloatingBubblePlaybackCommand.Start(tappedSlot)
        )
    }
}

internal fun clampFloatingBubblePosition(
    position: FloatingBubblePoint,
    viewport: FloatingBubbleViewport
): FloatingBubblePoint {
    val minX = viewport.edgeInset
    val maxX = (viewport.width - viewport.bubbleWidth - viewport.edgeInset).coerceAtLeast(minX)
    val minY = viewport.topInset
    val maxY = (viewport.height - viewport.bubbleHeight - viewport.bottomInset).coerceAtLeast(minY)
    return FloatingBubblePoint(
        x = position.x.coerceIn(minX, maxX),
        y = position.y.coerceIn(minY, maxY)
    )
}

internal fun snapFloatingBubbleToNearestEdge(
    position: FloatingBubblePoint,
    viewport: FloatingBubbleViewport
): FloatingBubblePoint {
    val clamped = clampFloatingBubblePosition(position, viewport)
    val leftEdge = viewport.edgeInset
    val rightEdge = (viewport.width - viewport.bubbleWidth - viewport.edgeInset)
        .coerceAtLeast(leftEdge)
    val snappedX = if ((clamped.x - leftEdge) <= (rightEdge - clamped.x)) {
        leftEdge
    } else {
        rightEdge
    }
    return clamped.copy(x = snappedX)
}

internal fun resolveFloatingBubbleDrop(
    position: FloatingBubblePoint,
    viewport: FloatingBubbleViewport,
    dismissTarget: FloatingBubbleRect
): FloatingBubbleDropResult {
    val clamped = clampFloatingBubblePosition(position, viewport)
    val centerX = clamped.x + viewport.bubbleWidth / 2
    val centerY = clamped.y + viewport.bubbleHeight / 2
    if (dismissTarget.contains(centerX, centerY)) {
        return FloatingBubbleDropResult(
            position = clamped,
            shouldDismiss = true
        )
    }
    return FloatingBubbleDropResult(
        position = snapFloatingBubbleToNearestEdge(clamped, viewport),
        shouldDismiss = false
    )
}

internal fun resolveFloatingBubbleDismissTargetRect(
    viewport: FloatingBubbleViewport,
    diameter: Int,
    bottomMargin: Int
): FloatingBubbleRect {
    val left = (viewport.width - diameter) / 2
    val top = viewport.height - diameter - bottomMargin
    return FloatingBubbleRect(
        left = left,
        top = top,
        right = left + diameter,
        bottom = top + diameter
    )
}

internal fun resolveFloatingBubbleDismissAppearance(isHovering: Boolean): FloatingBubbleDismissAppearance {
    return FloatingBubbleDismissAppearance(
        backgroundColor = if (isHovering) 0xFFFF6B6B.toInt() else 0xFFFF4D4F.toInt(),
        strokeColor = 0x66FFFFFF,
        slashColor = 0xFFFFFFFF.toInt(),
        slashThicknessDp = 4
    )
}

internal fun resolveFloatingBubbleDismissCrossRotations(): List<Float> {
    return listOf(-45f, 45f)
}

internal fun resolveFloatingBubbleButtonVisual(
    slot: Int,
    isActive: Boolean
): FloatingBubbleButtonVisual {
    if (isActive) {
        return FloatingBubbleButtonVisual(
            icon = FloatingBubbleButtonVisual.Icon.STOP,
            tone = when (slot) {
                AutoPlayMusicService.AUDIO_SLOT_SECONDARY -> FloatingBubbleButtonVisual.Tone.WARM
                else -> FloatingBubbleButtonVisual.Tone.COOL
            }
        )
    }
    return when (slot) {
        AutoPlayMusicService.AUDIO_SLOT_SECONDARY -> FloatingBubbleButtonVisual(
            icon = FloatingBubbleButtonVisual.Icon.GOODBYE,
            tone = FloatingBubbleButtonVisual.Tone.WARM
        )
        else -> FloatingBubbleButtonVisual(
            icon = FloatingBubbleButtonVisual.Icon.GREETING,
            tone = FloatingBubbleButtonVisual.Tone.COOL
        )
    }
}
