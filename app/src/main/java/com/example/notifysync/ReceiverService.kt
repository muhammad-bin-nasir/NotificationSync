package com.example.notifysync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ReceiverService : Service() {

    private val channelId = "NotifySyncChannel"
    private var isFirstLoad = true

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        listenToFirebase()
    }

    private fun listenToFirebase() {
        val sharedPref = getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val dbUrl = sharedPref.getString("firebaseUrl", "")

        if (dbUrl.isNullOrEmpty()) {
            Log.e("NotifySync", "Cannot start Receiver: Firebase URL is empty")
            return
        }

        // Use the dynamic URL
        val database = FirebaseDatabase.getInstance(dbUrl).reference
        val notificationRef = database.child("latest_notification")

        notificationRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFirstLoad) {
                    isFirstLoad = false
                    return
                }

                val title = snapshot.child("title").getValue(String::class.java)
                val text = snapshot.child("text").getValue(String::class.java)
                val appName = snapshot.child("appName").getValue(String::class.java)

                if (title != null && text != null) {
                    Log.d("NotifySync", "Received from Firebase: $title | $text")
                    showLocalNotification(title, text, appName)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("NotifySync", "Firebase listen failed: ${error.message}")
            }
        })
    }

    private fun showLocalNotification(title: String, text: String, appName: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Forwarded: $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Synced Notifications"
            val descriptionText = "Notifications received from the sender device"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}