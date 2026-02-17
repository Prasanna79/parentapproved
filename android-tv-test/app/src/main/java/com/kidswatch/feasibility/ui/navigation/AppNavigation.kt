package com.kidswatch.feasibility.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kidswatch.feasibility.debug.DebugAction
import com.kidswatch.feasibility.debug.DebugActionBus
import com.kidswatch.feasibility.ui.screens.AccountManagerTestScreen
import com.kidswatch.feasibility.ui.screens.EmbedTestScreen
import com.kidswatch.feasibility.ui.screens.HomeScreen
import com.kidswatch.feasibility.ui.screens.NewPipeTestScreen
import com.kidswatch.feasibility.ui.screens.PlaylistTestScreen
import com.kidswatch.feasibility.ui.screens.StreamQualityTestScreen
import com.kidswatch.feasibility.ui.screens.WebViewTestScreen

object Routes {
    const val HOME = "home"
    const val ACCOUNT_TEST = "account_test"
    const val WEBVIEW_TEST = "webview_test"
    const val EMBED_TEST = "embed_test"
    const val NEWPIPE_TEST = "newpipe_test"
    const val PLAYLIST_TEST = "playlist_test"
    const val STREAM_QUALITY_TEST = "stream_quality_test"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        DebugActionBus.actions.collect { action ->
            if (action is DebugAction.Navigate) {
                navController.navigate(action.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToAccountTest = { navController.navigate(Routes.ACCOUNT_TEST) },
                onNavigateToWebViewTest = { navController.navigate(Routes.WEBVIEW_TEST) },
                onNavigateToEmbedTest = { navController.navigate(Routes.EMBED_TEST) },
                onNavigateToNewPipeTest = { navController.navigate(Routes.NEWPIPE_TEST) },
                onNavigateToPlaylistTest = { navController.navigate(Routes.PLAYLIST_TEST) },
                onNavigateToStreamQualityTest = { navController.navigate(Routes.STREAM_QUALITY_TEST) },
            )
        }
        composable(Routes.ACCOUNT_TEST) {
            AccountManagerTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WEBVIEW_TEST) {
            WebViewTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EMBED_TEST) {
            EmbedTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NEWPIPE_TEST) {
            NewPipeTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PLAYLIST_TEST) {
            PlaylistTestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STREAM_QUALITY_TEST) {
            StreamQualityTestScreen(onBack = { navController.popBackStack() })
        }
    }
}
