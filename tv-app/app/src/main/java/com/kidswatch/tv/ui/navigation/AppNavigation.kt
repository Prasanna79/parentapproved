package com.kidswatch.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.ui.screens.HomeScreen
import com.kidswatch.tv.ui.screens.PairingScreen
import com.kidswatch.tv.ui.screens.PlaybackScreen
import com.kidswatch.tv.ui.screens.SettingsScreen
import com.kidswatch.tv.util.AppLogger

object Routes {
    const val PAIRING = "pairing"
    const val HOME = "home/{familyId}"
    const val PLAYBACK = "playback/{videoId}/{playlistId}/{startIndex}"
    const val SETTINGS = "settings/{familyId}"

    fun home(familyId: String) = "home/$familyId"
    fun playback(videoId: String, playlistId: String, startIndex: Int) = "playback/$videoId/$playlistId/$startIndex"
    fun settings(familyId: String) = "settings/$familyId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.PAIRING) {
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = { familyId ->
                    navController.navigate(Routes.home(familyId)) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            Routes.HOME,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId") ?: return@composable
            HomeScreen(
                familyId = familyId,
                onPlayVideo = { videoId, playlistId, videoIndex ->
                    navController.navigate(Routes.playback(videoId, playlistId, videoIndex))
                },
                onSettings = {
                    navController.navigate(Routes.settings(familyId))
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

        composable(
            Routes.SETTINGS,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId") ?: return@composable
            SettingsScreen(
                familyId = familyId,
                onBack = { navController.popBackStack() },
                onResetPairing = {
                    FirebaseManager.deleteDeviceDoc()
                    FirebaseManager.signOut()
                    AppLogger.log("Reset pairing — back to first run")
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onUnpair = {
                    FirebaseManager.updateDeviceDoc(mapOf("family_id" to null, "paired_at" to null))
                    AppLogger.log("Unpaired — back to pairing screen")
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRefresh = {
                    navController.popBackStack()
                },
            )
        }
    }
}
