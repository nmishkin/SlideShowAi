package info.amsa.slideshowai

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
import info.amsa.slideshowai.ui.MainViewModel
import info.amsa.slideshowai.ui.SettingsScreen
import info.amsa.slideshowai.ui.SlideshowScreen
import info.amsa.slideshowai.ui.theme.SlideShowAiTheme
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide System Bars for Immersive Mode
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
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
                                quietHoursStart = viewModel.quietHoursStart,
                                quietHoursEnd = viewModel.quietHoursEnd,
                                smartShuffleDays = viewModel.smartShuffleDays.toString(),
                                photoDuration = viewModel.photoDuration,
                                statusMessage = viewModel.statusMessage,
                                syncErrorMessage = viewModel.syncErrorMessage,
                                onConfigChange = { qStart, qEnd, days, duration ->
                                    viewModel.updateServerConfig(qStart, qEnd, days, duration)
                                },
                                onClearSyncError = { viewModel.clearSyncError() },
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
                                onPhotoShown = { file -> viewModel.markPhotoAsShown(file) },
                                onOrientationChanged = { isLandscape -> viewModel.updateOrientation(isLandscape) }
                            )
                        }
                    }
                }
            }
        }
    }
}
