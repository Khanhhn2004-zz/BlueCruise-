package com.vibegravity.bluecruise.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import timber.log.Timber

/**
 * A lightweight Foreground Service to keep the application process alive.
 * This prevents strict OEM battery management (like MIUI) from killing
 * the BroadcastReceivers when the app is swiped away from Recents.
 */
class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "keep_alive_channel"
        const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("KeepAliveService onCreate")
        createNotificationChannel()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("KeepAliveService onStartCommand")
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("BlueCruise đang hoạt động")
            .setContentText("Đang chờ kết nối Bluetooth với xe...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

        @Suppress("TooGenericExceptionCaught")
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
        } catch (exception: Exception) {
            Timber.e(
                exception,
                "Failed to start KeepAliveService in the foreground; stopping service."
            )
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Giữ ứng dụng luôn chạy",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Chạy một tiến trình ngầm nhỏ để đảm bảo nhận diện Bluetooth " +
                    "hoạt động ổn định trên các thiết bị khắt khe."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Timber.d("KeepAliveService onDestroy")
        super.onDestroy()
    }
}
