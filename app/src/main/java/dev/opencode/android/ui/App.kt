package dev.opencode.android.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.opencode.android.OpenCodeApp
import dev.opencode.android.server.OpenCodeServerManager
import dev.opencode.android.ui.screens.ChatScreen
import dev.opencode.android.ui.screens.ConnectScreen
import dev.opencode.android.ui.screens.SessionsScreen
import dev.opencode.android.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val CONNECT = "connect"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
}

@Composable
fun OpenCodeRoot() {
    val app = LocalContext.current.applicationContext as OpenCodeApp
    val connection by app.connection.collectAsStateWithLifecycle()
    val serverStatus by app.server.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (app.embeddedPrefs.enabled) {
        when (serverStatus) {
            is OpenCodeServerManager.Status.Running -> MainNav(startAtSessions = true)
            is OpenCodeServerManager.Status.Failed -> EmbeddedFailed(
                message = (serverStatus as OpenCodeServerManager.Status.Failed).message,
                onRetry = { app.server.restart() },
                onSwitchToRemote = {
                    scope.launch {
                        app.settings.saveEmbedded(app.embeddedPrefs.copy(enabled = false))
                    }
                },
            )
            is OpenCodeServerManager.Status.Starting,
            is OpenCodeServerManager.Status.Stopped,
            -> LoadingScreen("Gömülü sunucu başlatılıyor…")
        }
    } else {
        if (connection.isValid) {
            MainNav(startAtSessions = true)
        } else {
            MainNav(startAtSessions = false)
        }
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

@Composable
private fun EmbeddedFailed(
    message: String,
    onRetry: () -> Unit,
    onSwitchToRemote: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val clipboard = LocalClipboardManager.current
        Text(
            text = "Gömülü sunucu açılamadı",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text("Tekrar dene")
        }
        TextButton(onClick = onSwitchToRemote) {
            Text("Uzak sunucu kullan")
        }
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(message))
            },
        ) {
            Text("Hatayı kopyala")
        }
    }
}

@Composable
private fun LoadingScreen(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}