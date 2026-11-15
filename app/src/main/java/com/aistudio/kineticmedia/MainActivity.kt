package com.aistudio.kineticmedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistudio.kineticmedia.core.common.theme.KineticTheme
import com.aistudio.kineticmedia.feature.browser.BrowserScreen
import com.aistudio.kineticmedia.feature.browser.BrowserViewModel
import com.aistudio.kineticmedia.feature.downloads.DownloadsScreen
import com.aistudio.kineticmedia.feature.library.BookmarksScreen
import com.aistudio.kineticmedia.feature.library.HistoryScreen
import com.aistudio.kineticmedia.feature.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KineticTheme {
                KineticNavHost()
            }
        }
    }
}

@Composable
fun KineticNavHost() {
    val navController = rememberNavController()
    val browserViewModel: BrowserViewModel = koinViewModel()

    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                viewModel = browserViewModel,
                onOpenDownloads = { navController.navigate("downloads") },
                onOpenHistory = { navController.navigate("history") },
                onOpenBookmarks = { navController.navigate("bookmarks") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = browserViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToUrl = { url ->
                    browserViewModel.loadUrl(url)
                    navController.popBackStack()
                }
            )
        }
        composable("bookmarks") {
            BookmarksScreen(
                viewModel = browserViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToUrl = { url ->
                    browserViewModel.loadUrl(url)
                    navController.popBackStack()
                }
            )
        }
        composable("downloads") {
            DownloadsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
