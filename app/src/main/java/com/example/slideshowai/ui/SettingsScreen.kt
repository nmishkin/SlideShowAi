package com.example.slideshowai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUri: String,
    statusMessage: String,
    onUriChange: (String) -> Unit,
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
            Text("Server Configuration", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = serverUri,
                onValueChange = onUriChange,
                label = { Text("FTP URI (ftp://user:pass@host/path)") },
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
