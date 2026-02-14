package com.example.notifysync

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class NotificationCatcherService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val sharedPref = getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val isSender = sharedPref.getBoolean("isSender", false)

        if (!isSender || sbn == null) return

        val packageName = sbn.packageName

        // 1. Define keywords that match standard Android Phone and SMS apps
        val allowedKeywords = listOf(
            "messaging",
            "mms",
            "dialer",
            "telecom",
            "phone",
            "incallui"
        )

        // 2. Check if the app that posted the notification matches our allowed list
        val isPhoneOrSms = allowedKeywords.any { packageName.contains(it, ignoreCase = true) }

        // If it is not a phone or SMS app, stop right here and ignore it
        if (!isPhoneOrSms) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (title != null && text != null) {
            Log.d("NotifySync", "Caught: App=$packageName, Title=$title, Text=$text")

            val database = FirebaseDatabase.getInstance("https://notifysync727-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

            val notificationData = mapOf(
                "appName" to packageName,
                "title" to title,
                "text" to text,
                "timestamp" to System.currentTimeMillis()
            )

            database.child("latest_notification").setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("NotifySync", "Successfully sent to Firebase!")
                }
                .addOnFailureListener { error ->
                    Log.e("NotifySync", "Failed to send: ${error.message}")
                }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotifySync", "Listener Successfully Connected to Android OS!")
    }
}