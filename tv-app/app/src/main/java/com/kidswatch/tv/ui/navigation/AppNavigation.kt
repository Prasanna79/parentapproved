package com.kidswatch.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kidswatch.tv.ui.screens.ConnectScreen
import com.kidswatch.tv.ui.screens.HomeScreen
import com.kidswatch.tv.ui.screens.PlaybackScreen
import com.kidswatch.tv.ui.screens.SettingsScreen

object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val PLAYBACK = "playback/{videoId}/{playlistId}/{startIndex}"
    const val SETTINGS = "settings"

    fun playback(videoId: String, playlistId: String, startIndex: Int) = "playback/$videoId/$playlistId/$startIndex"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.CONNECT) {
            ConnectScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onPlayVideo = { videoId, playlistId, videoIndex ->
                    navController.navigate(Routes.playback(videoId, playlistId, videoIndex))
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onConnect = {
                    navController.navigate(Routes.CONNECT)
                },
            )
        }

        composable(
            Routes.PLAYBACK,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("startIndex") { type = NavType.IntType },
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            PlaybackScreen(
                videoId = videoId,
                playlistId = playlistId,
                startIndex = startIndex,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRefresh = {
                    navController.popBackStack()
                },
            )
        }
    }
}
