package com.example.slideshowai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    statusMessage: String,
    onConfigChange: (String, String, String, String) -> Unit,
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FTP Configuration", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = serverHost,
                onValueChange = { onConfigChange(it, serverPath, serverUsername, serverPassword) },
                label = { Text("Host (e.g. 192.168.1.5)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPath,
                onValueChange = { onConfigChange(serverHost, it, serverUsername, serverPassword) },
                label = { Text("Path (e.g. /photos)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverUsername,
                onValueChange = { onConfigChange(serverHost, serverPath, it, serverPassword) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverPassword,
                onValueChange = { onConfigChange(serverHost, serverPath, serverUsername, it) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
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
            
            Spacer(modifier = Modifier.weight(1f))
            
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
