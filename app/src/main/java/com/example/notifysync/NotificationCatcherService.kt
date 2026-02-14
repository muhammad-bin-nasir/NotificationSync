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
        val dbUrl = sharedPref.getString("firebaseUrl", "")

        // Stop if not sender, notification is null, or URL is missing
        if (!isSender || sbn == null || dbUrl.isNullOrEmpty()) return

        val packageName = sbn.packageName

        val allowedKeywords = listOf(
            "messaging",
            "mms",
            "dialer",
            "telecom",
            "phone",
            "incallui"
        )

        val isPhoneOrSms = allowedKeywords.any { packageName.contains(it, ignoreCase = true) }

        if (!isPhoneOrSms) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (title != null && text != null) {
            Log.d("NotifySync", "Caught: App=$packageName, Title=$title, Text=$text")

            // Use the dynamic URL
            val database = FirebaseDatabase.getInstance(dbUrl).reference

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