package com.vibegravity.bluecruise.receiver

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vibegravity.bluecruise.data.GearheadProcessMatcher
import com.vibegravity.bluecruise.data.PreferencesManager
import com.vibegravity.bluecruise.service.KeepAliveService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Receiver to detect when Android Auto starts and restore long-lived services if needed.
 * Playback start is now owned by BluetoothConnectionReceiver.
 */
@AndroidEntryPoint
class AndroidAutoDetectionReceiver internal constructor(
    private val receiverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val restoreDelayMs: Long = 1_000L
) : BroadcastReceiver() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Timber.d("AndroidAutoDetectionReceiver received: %s, data=%s", action, intent.data)

        if (!isAndroidAutoLaunch(context, intent)) {
            return
        }

        launchAsyncSafely {
            restoreServicesIfNeeded(context)
        }
    }

    private fun launchAsyncSafely(block: suspend () -> Unit) {
        val pendingResult = try {
            goAsync()
        } catch (_: IllegalStateException) {
            null
        }

        receiverScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun isAndroidAutoLaunch(context: Context, intent: Intent): Boolean {
        val packageName = intent.data?.schemeSpecificPart
        if (packageName == ANDROID_AUTO_PACKAGE) {
            Timber.d("Detected AA via package name: %s", packageName)
            return true
        }

        if (isAndroidAutoProcessRunning(context)) {
            Timber.d("Detected AA via process check")
            return true
        }

        if (intent.action == Intent.ACTION_MAIN) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR) {
                Timber.d("Device in car mode, assuming AA may be active")
                return true
            }
        }

        return false
    }

    private fun isAndroidAutoProcessRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val runningProcesses = activityManager.runningAppProcesses ?: return false

        return runningProcesses.any { processInfo ->
            GearheadProcessMatcher.matches(processInfo.processName)
        }
    }

    internal suspend fun restoreServicesIfNeeded(context: Context) {
        delay(restoreDelayMs)

        val keepAliveEnabled = preferencesManager.keepAppAliveFlow.firstOrNull() ?: false
        if (keepAliveEnabled) {
            Timber.d("Restoring KeepAliveService after Android Auto detected")
            val serviceIntent = Intent(context, KeepAliveService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        val autoPlayOnAndroidAuto = preferencesManager.autoPlayOnAndroidAutoFlow.firstOrNull() ?: false
        Timber.d(
            "autoPlayOnAndroidAuto=%s; playback start remains owned by BluetoothConnectionReceiver",
            autoPlayOnAndroidAuto
        )
    }

    companion object {
        const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
    }
}
