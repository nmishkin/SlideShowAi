package com.example.slideshowai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverHost: String,
    serverPath: String,
    serverUsername: String,
    serverPassword: String,
    quietHoursStart: String,
    quietHoursEnd: String,
    statusMessage: String,
    onConfigChange: (String, String, String, String, String, String) -> Unit,
    onSyncClick: () -> Unit,
    onStartSlideshow: () -> Unit,
    photoCount: Int
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FTP Configuration", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = serverHost,
                onValueChange = { onConfigChange(it, serverPath, serverUsername, serverPassword, quietHoursStart, quietHoursEnd) },
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPath,
                onValueChange = { onConfigChange(serverHost, it, serverUsername, serverPassword, quietHoursStart, quietHoursEnd) },
                label = { Text("Path (e.g. /photos)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverUsername,
                onValueChange = { onConfigChange(serverHost, serverPath, it, serverPassword, quietHoursStart, quietHoursEnd) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPassword,
                onValueChange = { onConfigChange(serverHost, serverPath, serverUsername, it, quietHoursStart, quietHoursEnd) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Text("Quiet Hours (Screen Off)", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = quietHoursStart,
                onValueChange = { onConfigChange(serverHost, serverPath, serverUsername, serverPassword, it, quietHoursEnd) },
                label = { Text("Start Time (HH:MM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = quietHoursEnd,
                onValueChange = { onConfigChange(serverHost, serverPath, serverUsername, serverPassword, quietHoursStart, it) },
                label = { Text("End Time (HH:MM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Button(
                onClick = onSyncClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sync Now")
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Photos Available: $photoCount", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onStartSlideshow,
                modifier = Modifier.fillMaxWidth(),
                enabled = photoCount > 0
            ) {
                Text("Start Slideshow")
            }
        }
    }
}
