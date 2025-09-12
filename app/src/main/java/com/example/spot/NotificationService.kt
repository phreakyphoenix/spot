package com.example.spot

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationService", "Listener connected ✅")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Optional: handle notifications
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional
    }
}
