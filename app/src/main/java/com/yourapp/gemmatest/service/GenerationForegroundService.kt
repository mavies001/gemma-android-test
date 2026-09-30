package com.yourapp.gemmatest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GenerationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: DEFAULT_MESSAGE
        startForeground(NOTIFICATION_ID, buildNotification(message))
        return START_NOT_STICKY
    }

    private fun buildNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Irachat")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "irachat_generation_channel"
        const val NOTIFICATION_ID = 1001
        private const val EXTRA_MESSAGE = "message"
        private const val DEFAULT_MESSAGE = "Generating a response…"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID, "Irachat activity", NotificationManager.IMPORTANCE_LOW
                    )
                    channel.description = "Shown while Irachat is working in the background"
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun start(context: Context, message: String = DEFAULT_MESSAGE) {
            ensureChannel(context)
            val intent = Intent(context, GenerationForegroundService::class.java)
            intent.putExtra(EXTRA_MESSAGE, message)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GenerationForegroundService::class.java))
        }
    }
}
