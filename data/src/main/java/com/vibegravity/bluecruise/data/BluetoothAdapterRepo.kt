package com.vibegravity.bluecruise.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class BluetoothAdapterRepo @Inject constructor(
    @ApplicationContext private val context: Context
) : IBluetoothAdapterRepo {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDeviceDomain> {
        if (!hasConnectPermission()) {
            Log.w("BluetoothAdapterRepo", "Missing BLUETOOTH_CONNECT permission to get paired devices")
            return emptyList()
        }

        return try {
            bluetoothAdapter?.bondedDevices
                ?.map { device ->
                    BluetoothDeviceDomain(
                        name = device.name ?: "Unknown",
                        macAddress = device.address
                    )
                }
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BluetoothAdapterRepo", "SecurityException while getting paired devices", e)
            emptyList()
        }
    }

    override fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            Log.e("BluetoothAdapterRepo", "SecurityException while checking if bluetooth is enabled", e)
            false
        }
    }

    override fun bluetoothEnabledFlow(): Flow<Boolean> = callbackFlow {
        trySend(isBluetoothEnabled())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                    trySend(state == BluetoothAdapter.STATE_ON)
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getConnectedA2dpAddresses(): List<String> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()
        return try {
            suspendCancellableCoroutine { cont ->
                val lock = Any()
                var resumed = false

                fun resumeOnce(addresses: List<String>) {
                    synchronized(lock) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(addresses)
                        }
                    }
                }

                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val a2dp = proxy as? BluetoothA2dp
                        val addresses = a2dp?.connectedDevices?.map { it.address } ?: emptyList()
                        adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        resumeOnce(addresses)
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        resumeOnce(emptyList())
                    }
                }
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothAdapterRepo", "SecurityException getting A2DP connected devices", e)
            emptyList()
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if a Bluetooth device is an Android Auto head unit.
     * Uses device-only heuristics:
     * 1. Bluetooth UUID (A2DP Sink: 0000112E-0000-1000-8000-00805F9B34FB)
     * 2. Device name patterns that look like a car head unit
     */
    @SuppressLint("MissingPermission")
    override suspend fun isAndroidAutoDevice(deviceAddress: String): Boolean {
        if (!hasConnectPermission() || bluetoothAdapter == null) {
            Log.w("BluetoothAdapterRepo", "No permission or adapter null, cannot check AA device")
            return false
        }

        return try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            var uuids = device.uuids

            var retryCount = 0
            while (uuids == null && retryCount < 3) {
                Log.d("BluetoothAdapterRepo", "UUIDs null, retrying... attempt ${retryCount + 1}")
                kotlinx.coroutines.delay(500)
                uuids = device.uuids
                retryCount++
            }

            if (uuids != null) {
                val androidAutoSinkUuid = UUID.fromString("0000112E-0000-1000-8000-00805F9B34FB")
                if (uuids.any { it.uuid == androidAutoSinkUuid }) {
                    Log.d("BluetoothAdapterRepo", "Android Auto detected via UUID for device: $deviceAddress")
                    return true
                }
            } else {
                Log.w("BluetoothAdapterRepo", "UUIDs still null after retries for $deviceAddress")
            }

            val deviceClass = device.bluetoothClass?.deviceClass
            if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
                Log.d("BluetoothAdapterRepo", "Android Auto detected via car audio class for device: $deviceAddress")
                return true
            }

            val deviceName = device.name
            if (deviceName != null) {
                val aaNamePatterns = listOf(
                    "android auto", "aauto", "androidauto",
                    "aa mirror", "aa_mirror", "auto mirror",
                    "huawei", "samsung", "toyota", "ford", "chevrolet",
                    "car multimedia", "car navigation", "head unit"
                )
                val lowercaseName = deviceName.lowercase()
                if (aaNamePatterns.any { lowercaseName.contains(it) }) {
                    if (
                        lowercaseName.contains("auto") ||
                        lowercaseName.contains("car") ||
                        lowercaseName.contains("multimedia") ||
                        lowercaseName.contains("navigation") ||
                        lowercaseName.contains("head unit") ||
                        lowercaseName.contains("toyota") ||
                        lowercaseName.contains("ford") ||
                        lowercaseName.contains("chevrolet") ||
                        lowercaseName.contains("honda") ||
                        lowercaseName.contains("bmw") ||
                        lowercaseName.contains("mercedes") ||
                        lowercaseName.contains("audi")
                    ) {
                        Log.d(
                            "BluetoothAdapterRepo",
                            "Android Auto detected via name (car head unit): $deviceName"
                        )
                        return true
                    }
                }
            }

            Log.d("BluetoothAdapterRepo", "Device $deviceAddress ($deviceName) is NOT an Android Auto device")
            false
        } catch (e: Exception) {
            Log.e("BluetoothAdapterRepo", "Error checking Android Auto device", e)
            false
        }
    }

    /**
     * Check if an Android Auto Gearhead process is running.
     * Any matching subprocess counts as active.
     */
    override fun isAndroidAutoProcessRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val runningProcesses = activityManager.runningAppProcesses ?: return false

        return runningProcesses.any { processInfo ->
            GearheadProcessMatcher.matches(processInfo.processName)
        }
    }
}
