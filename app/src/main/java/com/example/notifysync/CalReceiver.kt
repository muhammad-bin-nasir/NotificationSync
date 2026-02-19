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

        val sharedPref = context.getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val isSender = sharedPref.getBoolean("isSender", false)
        val dbUrl = sharedPref.getString("firebaseUrl", "")

        if (!isSender) return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                if (incomingNumber != null) {
                    sendSmsInBackground(context, incomingNumber)
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

                smsManager.sendTextMessage(number, null, message, null, null)
                Log.d("NotifySync", "Auto-reply SMS sent to $number")
            } catch (e: Exception) {
                Log.e("NotifySync", "SMS failed: ${e.message}")
            }
        }
    }

    private fun sendFirebaseAlert(context: Context, dbUrl: String?, number: String) {
        if (dbUrl.isNullOrEmpty()) return

        val database = FirebaseDatabase.getInstance(dbUrl).reference

        val notificationData = mapOf(
            "appName" to "Phone (Auto-Replied)",
            "title" to "Incoming Call",
            "text" to "Call from $number. Auto-reply SMS sent.",
            "timestamp" to System.currentTimeMillis()
        )

        database.child("latest_notification").setValue(notificationData)
    }
}