package dev.opencode.android.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.ui.screens.ChatScreen
import dev.opencode.android.ui.screens.ConnectScreen
import dev.opencode.android.ui.screens.SessionsScreen
import dev.opencode.android.ui.screens.SettingsScreen

object Routes {
    const val CONNECT = "connect"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
}

@Composable
fun OpenCodeRoot() {
    val app = LocalContext.current.applicationContext as OpenCodeApp
    val connection by app.connection.collectAsStateWithLifecycle()

    if (connection.isValid) {
        MainNav(startAtSessions = true)
    } else {
        MainNav(startAtSessions = false)
    }
}

@Composable
private fun MainNav(startAtSessions: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (startAtSessions) Routes.SESSIONS else Routes.CONNECT,
        enterTransition = {
            fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 10 }
        },
        exitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(tween(240)) + slideInHorizontally(tween(240)) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 10 }
        },
    ) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Routes.SESSIONS) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SESSIONS) {
            SessionsScreen(
                onOpenSession = { id -> navController.navigate("chat/$id") },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("chat/{sessionId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}