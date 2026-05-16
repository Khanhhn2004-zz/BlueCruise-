package com.vibegravity.bluecruise.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import timber.log.Timber

class PermissionsHelper(private val context: Context) {

    fun hasBluetoothPermissions(): Boolean {
        val permissions = getRequiredBluetoothPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBluetoothPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        val permissions = getRequiredBluetoothPermissions()
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            launcher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun getRequiredBluetoothPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(startActivity: (Intent) -> Unit) {
        val pkg = context.packageName
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent("com.samsung.android.sm.ACTION_BATTERY"),
            Intent().apply {
                setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
        )

        for (intent in intents) {
            try {
                startActivity(intent)
                Timber.i("Battery opt intent launched: ${intent.action ?: intent.component}")
                return
            } catch (e: ActivityNotFoundException) {
                Timber.d(e, "Battery opt intent missing: ${intent.action ?: intent.component}")
            } catch (e: SecurityException) {
                Timber.d(e, "Battery opt intent blocked: ${intent.action ?: intent.component}")
            }
        }

        Toast.makeText(
            context,
            "Không m? du?c màn hình t?i uu pin, hãy vào Cài d?t > ?ng d?ng > BlueCruise và t?t t?i uu hoá pin",
            Toast.LENGTH_LONG
        ).show()
    }
}
