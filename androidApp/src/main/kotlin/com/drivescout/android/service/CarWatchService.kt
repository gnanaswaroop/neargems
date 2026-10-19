package com.drivescout.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the app process alive while another app (navigation, music)
 * is in front, so car connections still trigger suggestions and announcements.
 */
class CarWatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("DriveScout is watching")
            .setContentText("Suggestions are read out when your car connects.")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Location permission was not granted, so the location-typed service cannot start.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Car watch", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "car-watch"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.drivescout.STOP_WATCH"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CarWatchService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CarWatchService::class.java).setAction(ACTION_STOP))
        }
    }
}
