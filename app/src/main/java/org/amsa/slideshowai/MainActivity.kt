package org.amsa.slideshowai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.amsa.slideshowai.ui.MainViewModel
import org.amsa.slideshowai.ui.SettingsScreen
import org.amsa.slideshowai.ui.SlideshowScreen
import org.amsa.slideshowai.ui.theme.SlideShowAiTheme

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
                    LaunchedEffect(viewModel.isInitialized) {
                        if (viewModel.isInitialized && viewModel.localPhotos.isNotEmpty()) {
                            navController.navigate("slideshow") {
                                popUpTo("settings") { inclusive = false }
                            }
                        }
                    }

                    NavHost(navController = navController, startDestination = "settings") {
                        composable("settings") {
                            SettingsScreen(
                                serverHost = viewModel.serverHost,
                                serverPath = viewModel.serverPath,
                                serverUsername = viewModel.serverUsername,
                                serverPassword = viewModel.serverPassword,
                                quietHoursStart = viewModel.quietHoursStart,
                                quietHoursEnd = viewModel.quietHoursEnd,
                                smartShuffleDays = viewModel.smartShuffleDays.toString(),
                                photoDuration = viewModel.photoDuration,
                                statusMessage = viewModel.statusMessage,
                                onConfigChange = { host, path, user, pass, qStart, qEnd, days, duration ->
                                    viewModel.updateServerConfig(host, path, user, pass, qStart, qEnd, days, duration)
                                },
                                onSyncClick = { viewModel.startSync() },
                                onStartSlideshow = { navController.navigate("slideshow") },
                                photoCount = viewModel.localPhotos.size
                            )
                        }
                        composable("slideshow") {
                            SlideshowScreen(
                                mediaItems = viewModel.slideshowPhotos,
                                quietHoursStart = viewModel.quietHoursStart,
                                quietHoursEnd = viewModel.quietHoursEnd,
                                photoDurationMillis = viewModel.getPhotoDurationMillis(),
                                onBack = { navController.popBackStack() },
                                onGetLocation = { file -> viewModel.getLocation(file) },
                                onPhotoShown = { file -> viewModel.markPhotoAsShown(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}
