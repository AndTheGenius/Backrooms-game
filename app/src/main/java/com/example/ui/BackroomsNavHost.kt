package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.CodexScreen
import com.example.ui.screens.DebriefScreen
import com.example.ui.screens.PlayScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TitleScreen

object BackroomsRoutes {
    const val ROUTE_TITLE = "title"
    const val ROUTE_PLAY = "play"
    const val ROUTE_DEBRIEF = "debrief"
    const val ROUTE_ARCHIVE = "archive"
    const val ROUTE_CODEX = "codex"
    const val ROUTE_SETTINGS = "settings"
}

@Composable
fun BackroomsNavHost(viewModel: BackroomsViewModel) {
    val navController = rememberNavController()
    val archiveState by viewModel.archiveState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()
    val debriefRun by viewModel.debriefRun.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = BackroomsRoutes.ROUTE_TITLE
    ) {
        composable(BackroomsRoutes.ROUTE_TITLE) {
            TitleScreen(
                archiveState = archiveState,
                onPlayNewTape = {
                    viewModel.startNewTape()
                    navController.navigate(BackroomsRoutes.ROUTE_PLAY) {
                        launchSingleTop = true
                    }
                },
                onOpenArchive = {
                    navController.navigate(BackroomsRoutes.ROUTE_ARCHIVE) {
                        launchSingleTop = true
                    }
                },
                onOpenCodex = {
                    navController.navigate(BackroomsRoutes.ROUTE_CODEX) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(BackroomsRoutes.ROUTE_SETTINGS) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(BackroomsRoutes.ROUTE_PLAY) {
            PlayScreen(
                hudState = hudState,
                settings = settings,
                frameBitmap = viewModel.frameBitmap,
                onMoveInput = viewModel::setMoveInput,
                onLookDeltaPx = viewModel::addLookDeltaPx,
                onSprintChanged = viewModel::setSprinting,
                onToggleFlashlight = viewModel::toggleFlashlight,
                onUseAlmondWater = viewModel::useAlmondWater,
                onPause = viewModel::pauseGame,
                onResume = viewModel::resumeGame,
                onEjectTape = viewModel::ejectTapeEarly,
                onUpdateSettings = viewModel::updateSettings,
                onGameOver = {
                    navController.navigate(BackroomsRoutes.ROUTE_DEBRIEF) {
                        popUpTo(BackroomsRoutes.ROUTE_PLAY) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(BackroomsRoutes.ROUTE_DEBRIEF) {
            DebriefScreen(
                hudState = hudState,
                savedRun = debriefRun,
                onRecordNewTape = {
                    viewModel.startNewTape()
                    navController.navigate(BackroomsRoutes.ROUTE_PLAY) {
                        popUpTo(BackroomsRoutes.ROUTE_DEBRIEF) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onReturnToDeck = {
                    navController.popBackStack(BackroomsRoutes.ROUTE_TITLE, inclusive = false)
                }
            )
        }

        composable(BackroomsRoutes.ROUTE_ARCHIVE) {
            ArchiveScreen(
                archiveState = archiveState,
                onBack = { navController.popBackStack() },
                onClearArchive = viewModel::clearArchive
            )
        }

        composable(BackroomsRoutes.ROUTE_CODEX) {
            CodexScreen(
                archiveState = archiveState,
                onBack = { navController.popBackStack() }
            )
        }

        composable(BackroomsRoutes.ROUTE_SETTINGS) {
            SettingsScreen(
                settings = settings,
                onUpdateSettings = viewModel::updateSettings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
