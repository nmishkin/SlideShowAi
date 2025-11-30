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
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // 5 seconds per slide
            if (mediaItems.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % mediaItems.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onBack)
    ) {
        if (mediaItems.isNotEmpty()) {
            val currentFile = mediaItems[currentIndex]
            Image(
                painter = rememberAsyncImagePainter(currentFile),
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
