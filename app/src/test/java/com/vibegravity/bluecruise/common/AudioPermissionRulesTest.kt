package com.vibegravity.bluecruise.common

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPermissionRulesTest {

    @Test
    fun `required permissions uses READ_MEDIA_AUDIO on 33 plus`() {
        val perms = AudioPermissionRules.requiredAudioPermissions(33)
        assertEquals(listOf(Manifest.permission.READ_MEDIA_AUDIO), perms)
    }

    @Test
    fun `required permissions uses READ_EXTERNAL_STORAGE on pre 33`() {
        val perms = AudioPermissionRules.requiredAudioPermissions(32)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }
}
