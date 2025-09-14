package com.example.spot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

object DebugLogger {
    
    private const val DEBUG_CHANNEL_ID = "DebugChannel"
    private const val DEBUG_NOTIFICATION_ID = 9999
    private var notificationId = DEBUG_NOTIFICATION_ID
    
    private var context: Context? = null
    private var notificationManager: NotificationManager? = null
    private var mainHandler = Handler(Looper.getMainLooper())
    private var lastToast: Toast? = null
    
    fun init(context: Context) {
        this.context = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createDebugChannel()
    }
    
    private fun createDebugChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEBUG_CHANNEL_ID,
                "Debug Logs",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows debug logs as notifications"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    // Main logging functions
    fun d(tag: String, message: String, showToast: Boolean = false, showNotif: Boolean = false) {
        Log.d(tag, message)
        if (showToast) showToast("$tag: $message")
        if (showNotif) showNotification("DEBUG", "$tag: $message")
    }
    
    fun i(tag: String, message: String, showToast: Boolean = false, showNotif: Boolean = false) {
        Log.i(tag, message)
        if (showToast) showToast("$tag: $message")
        if (showNotif) showNotification("INFO", "$tag: $message")
    }
    
    fun w(tag: String, message: String, showToast: Boolean = true, showNotif: Boolean = true) {
        Log.w(tag, message)
        if (showToast) showToast("⚠️ $tag: $message")
        if (showNotif) showNotification("WARNING", "$tag: $message")
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null, showToast: Boolean = true, showNotif: Boolean = true) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) "$message: ${throwable.message}" else message
        if (showToast) showToast("❌ $tag: $fullMessage")
        if (showNotif) showNotification("ERROR", "$tag: $fullMessage")
    }
    
    // Quick methods for common debug scenarios
    fun debugToast(tag: String, message: String) {
        d(tag, message, showToast = true)
    }
    
    fun debugNotif(tag: String, message: String) {
        d(tag, message, showNotif = true)
    }
    
    fun debugBoth(tag: String, message: String) {
        d(tag, message, showToast = true, showNotif = true)
    }
    
    // Service-specific logging (since services can't show toasts easily)
    fun serviceLog(tag: String, message: String, level: String = "INFO") {
        when (level.uppercase()) {
            "DEBUG" -> Log.d(tag, message)
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
        }
        showNotification(level, "$tag: $message")
    }
    
    private fun showToast(message: String) {
        context?.let { ctx ->
            mainHandler.post {
                lastToast?.cancel()
                lastToast = Toast.makeText(ctx, message, Toast.LENGTH_SHORT)
                lastToast?.show()
            }
        }
    }
    
    private fun showNotification(level: String, message: String) {
        context?.let { ctx ->
            val icon = when (level) {
                "ERROR" -> android.R.drawable.ic_dialog_alert
                "WARNING" -> android.R.drawable.ic_dialog_info
                else -> android.R.drawable.ic_dialog_info
            }
            
            val notification = NotificationCompat.Builder(ctx, DEBUG_CHANNEL_ID)
                .setContentTitle("Debug Log - $level")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(icon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setSound(null)
                .setVibrate(null)
                .build()
            
            notificationManager?.notify(notificationId++, notification)
            
            // Reset notification ID to prevent too many notifications
            if (notificationId > DEBUG_NOTIFICATION_ID + 50) {
                notificationId = DEBUG_NOTIFICATION_ID
            }
        }
    }
    
    fun clearNotifications() {
        notificationManager?.cancelAll()
    }
}