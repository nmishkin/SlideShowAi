package com.example.slideshowai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    mediaItems: List<File>,
    onBack: () -> Unit
) {
    // Shuffle the list so photos are shown in random order
    val shuffledItems = remember(mediaItems) { mediaItems.shuffled() }
    var currentIndex by remember(mediaItems) { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(shuffledItems) {
        while (true) {
            delay(5000) // 5 seconds per slide
            if (shuffledItems.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % shuffledItems.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onBack)
    ) {
        if (shuffledItems.isNotEmpty()) {
            // Safety check for index
            val safeIndex = currentIndex.coerceIn(shuffledItems.indices)
            val currentFile = shuffledItems[safeIndex]
            Image(
                painter = rememberAsyncImagePainter(currentFile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "No photos found",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
