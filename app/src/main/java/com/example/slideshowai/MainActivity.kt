package com.example.slideshowai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.slideshowai.ui.MainViewModel
import com.example.slideshowai.ui.SettingsScreen
import com.example.slideshowai.ui.SlideshowScreen
import com.example.slideshowai.ui.theme.SlideShowAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlideShowAiTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "settings") {
                        composable("settings") {
                            SettingsScreen(
                                serverUri = viewModel.serverUri,
                                statusMessage = viewModel.statusMessage,
                                onUriChange = { viewModel.updateServerUri(it) },
                                onSyncClick = { viewModel.startSync() },
                                onStartSlideshow = { navController.navigate("slideshow") },
                                photoCount = viewModel.localPhotos.size
                            )
                        }
                        composable("slideshow") {
                            SlideshowScreen(
                                mediaItems = viewModel.localPhotos,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
