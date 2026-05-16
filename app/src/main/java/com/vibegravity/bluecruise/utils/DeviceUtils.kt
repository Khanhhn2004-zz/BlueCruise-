package com.vibegravity.bluecruise.utils

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.vibegravity.bluecruise.R
import timber.log.Timber

/** True if device is Xiaomi/Redmi/Poco (MIUI). Use for auto-start intents. */
private fun isXiaomiLike(): Boolean {
    val m = Build.MANUFACTURER.lowercase()
    val b = Build.BRAND.lowercase()
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
        b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
}

/** Standard Android intents: battery optimization + app details. Works on any device (Android 8+). */
private fun standardBatteryAndAppDetailsIntents(context: Context): List<Intent> = listOf(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    },
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
)

/**
 * Returns true if the URI is from an online/cloud provider (e.g. Google Drive).
 * Used to reject cloud files and only allow local/downloaded files for audio.
 */
fun isOnlineOrDriveUri(uri: Uri): Boolean {
    if (uri.scheme != "content") return false
    val auth = uri.authority?.lowercase() ?: return false
    return auth.contains("docs") || auth.contains("drive") ||
        auth.contains("dropbox") || auth.contains("skydrive") ||
        auth.contains("onedrive") || auth.contains("cloud")
}

fun isBatteryOptimizationIntentSupported(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    if (manufacturer.contains("samsung")) return false // Samsung often blocks this intent

    val pm = context.packageManager
    val mainIntent = Intent().apply {
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = Uri.parse("package:${context.packageName}")
    }
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    return mainIntent.resolveActivity(pm) != null || fallbackIntent.resolveActivity(pm) != null
}

fun requestBatteryOptimization(context: Context) {
    try {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Battery optimization intent not resolved, opening app details")
        launchAppDetailsSettings(context)
    } catch (e: SecurityException) {
        Timber.w(e, "Battery optimization permission denied")
        launchAppDetailsSettings(context)
    }
}

private fun launchAppDetailsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (ex: ActivityNotFoundException) {
        Timber.w(ex, "App details fallback intent not resolved")
    } catch (ex: SecurityException) {
        Timber.w(ex, "App details permission denied")
    }
}

/**
 * Banner "T? kh?i d?ng / Pin" hi?n th? trên m?i thi?t b? Android 8+ (global).
 * OEM có intent riêng s? m? màn hình tuong ?ng; còn l?i dùng chu?n Android (battery + app details).
 */
fun isAutoStartSupported(): Boolean = true

fun isAutoStartGranted(context: Context): Boolean {
    return when {
        isXiaomiLike() -> checkXiaomiAutoStartAppOps(context)
        else -> isIgnoringBatteryOptimizations(context)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun checkXiaomiAutoStartAppOps(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val method = android.app.AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val result = method.invoke(
            appOps,
            10008, // Undocumented AppOps constant for AutoStart on MIUI
            android.os.Process.myUid(),
            context.packageName
        ) as Int
        result == android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: ReflectiveOperationException) {
        Timber.d(e, "AppOps check failed for auto-start")
        false
    }
}

/**
 * M? màn hình T? kh?i d?ng / Pin c?a OEM ho?c fallback chu?n Android.
 * N?u máy không h? tr? intent dích (vd. Xiaomi MIUI cu thi?u activity), l?n lu?t th?
 * các intent ti?p theo trong list (battery optimization, app details). Cu?i cùng luôn
 * m? App details và hi?n Toast hu?ng d?n.
 */
@SuppressLint("BatteryLife")
fun requestAutoStartPermission(context: Context) {
    val intentsToTry = buildOemAutoStartIntents(context)
    val launched = tryLaunchAutoStartIntents(context, intentsToTry)
    if (!launched) {
        launchAppDetailsSettings(context)
        Toast.makeText(context, R.string.auto_start_fallback_hint, Toast.LENGTH_LONG).show()
    }
}

private fun buildOemAutoStartIntents(context: Context): List<Intent> {
    val m = Build.MANUFACTURER.lowercase()
    val pkg = context.packageName
    return when {
        isXiaomiLike() -> listOf(
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            },
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", pkg)
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> listOf(
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            },
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            },
            Intent().apply {
                setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        m.contains("vivo") -> listOf(
            Intent().apply {
                setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        m.contains("samsung") -> listOf(
            // Prefer direct whitelist prompt first
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent("com.samsung.android.sm.ACTION_BATTERY"),
            Intent().apply {
                setClassName("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity")
            },
            Intent().apply {
                setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
            },
            Intent().apply {
                setClassName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")
            },
            Intent().apply {
                setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        m.contains("huawei") || m.contains("honor") -> listOf(
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            },
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            },
            Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        m.contains("asus") -> listOf(
            Intent().apply {
                setClassName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")
            }
        ).plus(standardBatteryAndAppDetailsIntents(context))
        else -> standardBatteryAndAppDetailsIntents(context)
    }
}

private fun tryLaunchAutoStartIntents(context: Context, intents: List<Intent>): Boolean {
    val pm = context.packageManager
    for (intent in intents) {
        try {
            @Suppress("DEPRECATION")
            if (pm.resolveActivity(intent, 0) == null) continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Timber.i("Auto-start intent launched: ${intent.action ?: intent.component}")
            return true
        } catch (e: ActivityNotFoundException) {
            Timber.d(e, "Auto-start intent not resolved, trying next: ${intent.action ?: intent.component}")
        } catch (e: SecurityException) {
            Timber.d(e, "Auto-start intent permission denied: ${intent.action ?: intent.component}")
        }
    }
    return false
}
