package com.example.notifysync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

        val sharedPref = getSharedPreferences("AppMode", Context.MODE_PRIVATE)
        val isSenderInitial = sharedPref.getBoolean("isSender", false)
        val savedFirebaseUrl = sharedPref.getString("firebaseUrl", "") ?: ""

        val receiverIntent = Intent(this, ReceiverService::class.java)
        if (!isSenderInitial && savedFirebaseUrl.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(receiverIntent)
            } else {
                startService(receiverIntent)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppModeToggle(
                        initialMode = isSenderInitial,
                        initialUrl = savedFirebaseUrl,
                        onModeChanged = { isSender ->
                            sharedPref.edit().putBoolean("isSender", isSender).apply()

                            if (isSender) {
                                stopService(receiverIntent)
                            } else {
                                val currentUrl = sharedPref.getString("firebaseUrl", "")
                                if (!currentUrl.isNullOrEmpty()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(receiverIntent)
                                    } else {
                                        startService(receiverIntent)
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Please save a Firebase URL first!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onUrlSaved = { newUrl ->
                            sharedPref.edit().putString("firebaseUrl", newUrl).apply()
                            Toast.makeText(this@MainActivity, "Database URL Saved!", Toast.LENGTH_SHORT).show()
                        },
                        onRequestPermissions = {
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
    initialUrl: String,
    onModeChanged: (Boolean) -> Unit,
    onUrlSaved: (String) -> Unit,
    onRequestPermissions: () -> Unit
) {
    var isSender by remember { mutableStateOf(initialMode) }
    var firebaseUrl by remember { mutableStateOf(initialUrl) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, the notification will now show
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Database Setup", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = firebaseUrl,
            onValueChange = { firebaseUrl = it },
            label = { Text("Paste Firebase RTDB URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onUrlSaved(firebaseUrl) }) {
            Text("Save URL")
        }

        Spacer(modifier = Modifier.height(48.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(48.dp))

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

        if (isSender) {
            Button(onClick = onRequestPermissions) {
                Text("Grant Notification Access")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("This device will capture and send notifications.")
        } else {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) {
                Text("Force Notification Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("This device will wait to receive notifications.")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the button above to allow the foreground notification to show.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}