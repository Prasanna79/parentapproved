package tv.parentapproved.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tv.parentapproved.app.ui.screens.ConnectScreen
import tv.parentapproved.app.ui.screens.HomeScreen
import tv.parentapproved.app.ui.screens.LockScreen
import tv.parentapproved.app.ui.screens.PlaybackScreen
import tv.parentapproved.app.ui.screens.SettingsScreen

object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val PLAYBACK = "playback/{videoId}/{playlistId}/{startIndex}"
    const val SETTINGS = "settings"
    const val LOCK = "lock/{reason}"

    fun playback(videoId: String, playlistId: String, startIndex: Int) = "playback/$videoId/$playlistId/$startIndex"
    fun lock(reason: String) = "lock/${reason.lowercase()}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val onLocked: (String) -> Unit = { reason ->
        navController.navigate(Routes.lock(reason)) {
            popUpTo(Routes.HOME) { inclusive = false }
        }
    }

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
                onLocked = onLocked,
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
                onLocked = onLocked,
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

        composable(
            Routes.LOCK,
            arguments = listOf(
                navArgument("reason") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val reason = backStackEntry.arguments?.getString("reason") ?: "manual_lock"
            LockScreen(
                reason = reason,
                onUnlocked = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}
