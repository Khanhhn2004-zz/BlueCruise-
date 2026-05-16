package com.vibegravity.bluecruise.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class FloatingBubbleToggleDecision(
    val persistEnabled: Boolean,
    val requestPermission: Boolean,
    val startService: Boolean,
    val stopService: Boolean
)

internal fun resolveFloatingBubbleToggleDecision(
    enabled: Boolean,
    hasOverlayPermission: Boolean
): FloatingBubbleToggleDecision {
    return when {
        !enabled -> FloatingBubbleToggleDecision(
            persistEnabled = false,
            requestPermission = false,
            startService = false,
            stopService = true
        )
        hasOverlayPermission -> FloatingBubbleToggleDecision(
            persistEnabled = true,
            requestPermission = false,
            startService = true,
            stopService = false
        )
        else -> FloatingBubbleToggleDecision(
            persistEnabled = false,
            requestPermission = true,
            startService = false,
            stopService = false
        )
    }
}

@Singleton
class FloatingBubbleController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun createOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, FloatingBubbleService::class.java))
    }

    fun stopService() {
        context.stopService(Intent(context, FloatingBubbleService::class.java))
    }
}
