package com.example.notifysync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // 1. Check if this device is the Sender
        val sharedPref = context.getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val isSender = sharedPref.getBoolean("isSender", false)
        val dbUrl = sharedPref.getString("firebaseUrl", "")

        // If this is the receiver device, do nothing and exit
        if (!isSender) return

        // 2. Check if the phone is ringing
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                if (incomingNumber != null) {
                    Log.d("NotifySync", "Incoming call from: $incomingNumber")

                    // Trigger the silent SMS
                    sendSmsInBackground(context, incomingNumber)

                    // Trigger the Firebase alert to Device B
                    sendFirebaseAlert(context, dbUrl, incomingNumber)
                }
            }
        }
    }

    private fun sendSmsInBackground(context: Context, number: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = context.getSystemService(SmsManager::class.java)
                val message = "I am currently unavailable on calls. Please send me a message on WhatsApp."

                // Send SMS silently while the phone keeps ringing
                smsManager.sendTextMessage(number, null, message, null, null)
                Log.d("NotifySync", "Auto-reply SMS sent to $number")
            } catch (e: Exception) {
                Log.e("NotifySync", "SMS failed: ${e.message}")
            }
        } else {
            Log.e("NotifySync", "SEND_SMS permission not granted by user.")
        }
    }

    private fun sendFirebaseAlert(context: Context, dbUrl: String?, number: String) {
        if (dbUrl.isNullOrEmpty()) return

        val database = FirebaseDatabase.getInstance(dbUrl).reference

        // Create a custom notification payload for the call
        val notificationData = mapOf(
            "appName" to "Phone (Auto-Replied)",
            "title" to "Incoming Call",
            "text" to "Call from $number. Auto-reply SMS sent.",
            "timestamp" to System.currentTimeMillis()
        )

        database.child("latest_notification").setValue(notificationData)
    }
}