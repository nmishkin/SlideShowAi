package com.example.slideshowai.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    mediaItems: List<File>,
    quietHoursStart: String,
    quietHoursEnd: String,
    onBack: () -> Unit,
    onGetLocation: suspend (File) -> String?,
    onPhotoShown: (File) -> Unit
) {
    // Shuffle the list so photos are shown in random order
    val shuffledItems = remember(mediaItems) { mediaItems.shuffled() }
    var currentIndex by remember(mediaItems) { mutableIntStateOf(0) }
    var isQuietHour by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Check for Quiet Hours
    LaunchedEffect(quietHoursStart, quietHoursEnd) {
        while (true) {
            val now = java.time.LocalTime.now()
            try {
                val start = java.time.LocalTime.parse(quietHoursStart)
                val end = java.time.LocalTime.parse(quietHoursEnd)
                
                isQuietHour = if (start.isBefore(end)) {
                    now.isAfter(start) && now.isBefore(end)
                } else {
                    // Spans midnight (e.g. 22:00 to 07:00)
                    now.isAfter(start) || now.isBefore(end)
                }
            } catch (e: Exception) {
                // Invalid time format, ignore
                isQuietHour = false
            }
            delay(60000) // Check every minute
        }
    }

    DisposableEffect(isQuietHour) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            if (isQuietHour) {
                // Allow sleep
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    (context as? android.app.Activity)?.setShowWhenLocked(false)
                    (context as? android.app.Activity)?.setTurnScreenOn(false)
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                    @Suppress("DEPRECATION")
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }
            } else {
                // Keep screen on and Wake up
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    (context as? android.app.Activity)?.setShowWhenLocked(true)
                    (context as? android.app.Activity)?.setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                    @Suppress("DEPRECATION")
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }

                // Force wake up using PowerManager
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SlideShowAi:WakeUp"
                )
                wakeLock.acquire(3000) // Hold for 3 seconds to ensure screen turns on
            }
        }
        onDispose {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    (context as? android.app.Activity)?.setShowWhenLocked(false)
                    (context as? android.app.Activity)?.setTurnScreenOn(false)
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                    @Suppress("DEPRECATION")
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }
            }
        }
    }

    LaunchedEffect(shuffledItems, isQuietHour) {
        while (true) {
            delay(5000) // 5 seconds per slide
            if (!isQuietHour && shuffledItems.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % shuffledItems.size
            }
        }
    }
    
    if (isQuietHour) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onBack)
        ) {
            Text(
                text = "Zzz...",
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        val threshold = 50f
                        if (totalDrag > threshold) {
                            // Swipe Right -> Previous
                            currentIndex = if (currentIndex - 1 < 0) shuffledItems.size - 1 else currentIndex - 1
                        } else if (totalDrag < -threshold) {
                            // Swipe Left -> Next
                            currentIndex = (currentIndex + 1) % shuffledItems.size
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                )
            }
            .clickable(onClick = onBack)
    ) {
        if (shuffledItems.isNotEmpty()) {
            // Safety check for index
            val safeIndex = currentIndex.coerceIn(shuffledItems.indices)
            val currentFile = shuffledItems[safeIndex]
            Log.d("SlideshowScreen", "Displaying file: ${currentFile.name}")

            Image(
                painter = rememberAsyncImagePainter(currentFile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Display Metadata (Year & Location)
            val context = androidx.compose.ui.platform.LocalContext.current
            var location by remember { mutableStateOf<String?>(null) }
            val year = remember(currentFile) { getPhotoYear(currentFile) }
            
            LaunchedEffect(currentFile) {
                location = onGetLocation(currentFile)
                onPhotoShown(currentFile)
            }

            if (year != null || location != null) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (year != null) {
                        Text(
                            text = year,
                            color = Color.White.copy(alpha = 0.9f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (location != null) {
                        Text(
                            text = location!!,
                            color = Color.White.copy(alpha = 0.7f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }


        } else {
            Text(
                text = "No photos found",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun getPhotoYear(file: File): String? {
    return try {
        val exif = androidx.exifinterface.media.ExifInterface(file)
        val date = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
        
        // Format is usually "yyyy:MM:dd HH:mm:ss"
        date?.split(":", " ")?.firstOrNull()
    } catch (e: Exception) {
        null
    }
}
