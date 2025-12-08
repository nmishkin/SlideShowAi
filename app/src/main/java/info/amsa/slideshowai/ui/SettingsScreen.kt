package info.amsa.slideshowai.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    quietHoursStart: String,
    quietHoursEnd: String,
    smartShuffleDays: String,
    photoDuration: String,
    statusMessage: String,
    syncErrorMessage: String?,
    onConfigChange: (String, String, String, String) -> Unit,
    onClearSyncError: () -> Unit,
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
            Text("Connection Info", style = MaterialTheme.typography.titleMedium)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                 Column(modifier = Modifier.padding(16.dp)) {
                    Text("Server Running", style = MaterialTheme.typography.titleSmall)
                    Text("Port: 4000", style = MaterialTheme.typography.bodyMedium)
                    Text("Ready for Direct Push Sync", style = MaterialTheme.typography.bodyMedium)
                 }
            }

            Text("Quiet Hours (Screen Off)", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = quietHoursStart,
                onValueChange = { onConfigChange(it, quietHoursEnd, smartShuffleDays, photoDuration) },
                label = { Text("Start Time (HH:MM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = quietHoursEnd,
                onValueChange = { onConfigChange(quietHoursStart, it, smartShuffleDays, photoDuration) },
                label = { Text("End Time (HH:MM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text("Smart Shuffle", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = smartShuffleDays,
                onValueChange = { onConfigChange(quietHoursStart, quietHoursEnd, it, photoDuration) },
                label = { Text("Don't repeat within (Days)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = photoDuration,
                onValueChange = { onConfigChange(quietHoursStart, quietHoursEnd, smartShuffleDays, it) },
                label = { Text("Photo Duration (HH:MM:SS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            

            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Photos Available: $photoCount", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val buildDate = java.util.Date(info.amsa.slideshowai.BuildConfig.BUILD_TIME)
                    val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                    Text("Build: ${format.format(buildDate)}", style = MaterialTheme.typography.labelSmall)
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
                    if (syncErrorMessage != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onClearSyncError,
                    title = { Text("Sync Error") },
                    text = { Text(syncErrorMessage) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = onClearSyncError
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
