package com.ebookfrenzy.dawaibuddy.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ebookfrenzy.dawaibuddy.host_activities.WatchAuthDialogActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchAuthListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_login") {
            Log.d("WatchAuth", "Login request received. Waking up phone via Notification.")
            showLoginNotification(messageEvent.sourceNodeId)
        }
    }

    private fun showLoginNotification(nodeId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "watch_auth_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Watch Authentication",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts for watch login authorization"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, WatchAuthDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("WATCH_NODE_ID", nodeId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Watch Link Request")
            .setContentText("Tap to link your Wear OS watch to Dawai Buddy.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}