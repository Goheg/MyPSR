package com.example.mypsr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mypsr.data.local.UserPreferences
import com.example.mypsr.ui.home.HomeScreen
import com.example.mypsr.ui.home.HomeViewModel
import com.example.mypsr.ui.onboarding.OnboardingScreen
import com.example.mypsr.ui.review.ReviewScreen
import com.example.mypsr.ui.review.ReviewViewModel
import com.example.mypsr.ui.settings.SettingsScreen
import com.example.mypsr.ui.settings.SettingsViewModel
import com.example.mypsr.ui.theme.MyPSRTheme
import com.example.mypsr.ui.viewer.ReportViewerScreen

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val reviewViewModel: ReviewViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data
        } else null

        setContent {
            val userPrefs = UserPreferences.getInstance(LocalContext.current)
            val appTheme by userPrefs.themeFlow.collectAsState()

            MyPSRTheme(appTheme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        reviewViewModel = reviewViewModel,
                        settingsViewModel = settingsViewModel,
                        incomingUri = incomingUri
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    reviewViewModel: ReviewViewModel,
    settingsViewModel: SettingsViewModel,
    incomingUri: Uri?
) {
    val navController = rememberNavController()

    val startDestination = if (incomingUri != null) "viewer_external" else "home"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToOnboarding = { navController.navigate("onboarding") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToReview = { sessionId -> navController.navigate("review/$sessionId") },
                onNavigateToViewer = { sessionId -> navController.navigate("viewer/$sessionId") }
            )
        }

        composable("onboarding") {
            OnboardingScreen(
                onNavigateBack = { navController.popBackStack() },
                onCompleteOnboarding = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "review/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            ReviewScreen(
                sessionId = sessionId,
                viewModel = reviewViewModel,
                onNavigateBack = { navController.popBackStack() },
                onReportGenerated = { id ->
                    navController.navigate("viewer/$id") {
                        popUpTo("home")
                    }
                }
            )
        }

        composable(
            route = "viewer/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            ReportViewerScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("viewer_external") {
            ReportViewerScreen(
                fileUri = incomingUri,
                onNavigateBack = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }
    }
}
