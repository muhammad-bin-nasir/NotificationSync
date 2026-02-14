package com.example.notifysync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This is where we save our Sender/Receiver choice
        val sharedPref = getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val isSenderInitial = sharedPref.getBoolean("isSender", false)

        // Ensure the receiver service is running if the app is opened in Receiver mode
        val receiverIntent = Intent(this, ReceiverService::class.java)
        if (!isSenderInitial) {
            startService(receiverIntent)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppModeToggle(
                        initialMode = isSenderInitial,
                        onModeChanged = { isSender ->
                            // Save the new state when the user flips the switch
                            sharedPref.edit().putBoolean("isSender", isSender).apply()

                            // Logic to start/stop the background services
                            if (isSender) {
                                // If turning ON Sender mode, stop the Receiver service
                                stopService(receiverIntent)
                            } else {
                                // If turning ON Receiver mode, start the Receiver service
                                startService(receiverIntent)
                            }
                        },
                        onRequestPermissions = {
                            // This opens the specific Android settings page to allow notification reading
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppModeToggle(
    initialMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit
) {
    // This variable remembers the switch position on the screen
    var isSender by remember { mutableStateOf(initialMode) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isSender) "Current Mode: SENDER" else "Current Mode: RECEIVER",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Switch(
            checked = isSender,
            onCheckedChange = {
                isSender = it
                onModeChanged(it)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // If in Sender mode, show the button to grant system permissions
        if (isSender) {
            Button(onClick = onRequestPermissions) {
                Text("Grant Notification Access")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("This device will capture and send notifications.")
        } else {
            Text("This device will wait to receive notifications.")
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Note: If this device is on Android 13 or higher, ensure you manually allow Notifications for this app in your Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}