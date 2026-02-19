package com.example.notifysync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiverService : Service() {

    private val channelId = "NotifySyncChannel"
    private val foregroundNotificationId = 1001
    private var isFirstLoad = true

    private val updateHandler = Handler(Looper.getMainLooper())
    private val heartbeatInterval = 10 * 60 * 1000L // 10 minutes

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            updateForegroundNotification()
            updateHandler.postDelayed(this, heartbeatInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        updateHandler.postDelayed(heartbeatRunnable, heartbeatInterval)
        listenToFirebase()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(heartbeatRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun getDynamicNotification(): Notification {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NotifySync Receiver")
            .setContentText("Active & Listening. Last check: $currentTime")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundService() {
        val notification = getDynamicNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(foregroundNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(foregroundNotificationId, notification)
        }
    }

    private fun updateForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(foregroundNotificationId, getDynamicNotification())
    }

    private fun listenToFirebase() {
        val sharedPref = getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val dbUrl = sharedPref.getString("firebaseUrl", "")

        if (dbUrl.isNullOrEmpty()) {
            Log.e("NotifySync", "Cannot start Receiver: Firebase URL is empty")
            return
        }

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
            val descriptionText = "Notifications from sender device"
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