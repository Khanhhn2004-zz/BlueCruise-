package com.vibegravity.bluecruise.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBubbleControllerTest {

    @Test
    fun `enable with overlay permission persists and starts service`() {
        val decision = resolveFloatingBubbleToggleDecision(enabled = true, hasOverlayPermission = true)

        assertTrue(decision.persistEnabled)
        assertTrue(decision.startService)
        assertFalse(decision.requestPermission)
        assertFalse(decision.stopService)
    }

    @Test
    fun `enable without overlay permission requests permission without persisting`() {
        val decision = resolveFloatingBubbleToggleDecision(enabled = true, hasOverlayPermission = false)

        assertFalse(decision.persistEnabled)
        assertFalse(decision.startService)
        assertTrue(decision.requestPermission)
        assertFalse(decision.stopService)
    }

    @Test
    fun `disable stops service and clears persisted setting`() {
        val decision = resolveFloatingBubbleToggleDecision(enabled = false, hasOverlayPermission = true)

        assertFalse(decision.persistEnabled)
        assertFalse(decision.startService)
        assertFalse(decision.requestPermission)
        assertTrue(decision.stopService)
    }
}
