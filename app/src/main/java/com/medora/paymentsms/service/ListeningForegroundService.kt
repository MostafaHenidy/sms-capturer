package com.medora.paymentsms.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.medora.paymentsms.R

/**
 * Visible "I am listening" indicator. The SMS receiver itself works from a
 * system broadcast; this service improves status visibility and reliability.
 */
class ListeningForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_listening_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_listening_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(getString(R.string.notification_listening_title))
        .setContentText(getString(R.string.notification_listening_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    companion object {
        private const val CHANNEL_ID = "medora_listening"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningForegroundService::class.java))
        }
    }
}
