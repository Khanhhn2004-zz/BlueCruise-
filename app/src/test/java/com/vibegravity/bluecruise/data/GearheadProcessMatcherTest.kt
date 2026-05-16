package com.vibegravity.bluecruise.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GearheadProcessMatcherTest {

    @Test
    fun `matches base process and observed subprocess names but rejects near misses`() {
        assertTrue(GearheadProcessMatcher.matches("com.google.android.projection.gearhead"))
        assertTrue(GearheadProcessMatcher.matches("com.google.android.projection.gearhead:car"))
        assertTrue(GearheadProcessMatcher.matches("com.google.android.projection.gearhead:projection"))
        assertTrue(GearheadProcessMatcher.matches("com.google.android.projection.gearhead:shared"))
        assertFalse(GearheadProcessMatcher.matches("com.google.android.projection.gearhead:"))
        assertFalse(GearheadProcessMatcher.matches("com.google.android.projection.gearheadx"))
        assertFalse(GearheadProcessMatcher.matches("com.google.android.youtube"))
    }
}
