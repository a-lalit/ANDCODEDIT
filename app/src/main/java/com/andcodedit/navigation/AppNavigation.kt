package com.andcodedit.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.andcodedit.ui.screens.DexModeScreen
import com.andcodedit.ui.screens.EditorScreen
import com.andcodedit.ui.screens.HomeScreen
import com.andcodedit.ui.screens.AIChatScreen
import com.andcodedit.viewmodel.AppStateViewModel

@Composable
fun AppNavigation(navController: NavHostController, appStateViewModel: AppStateViewModel) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(navController = navController, appStateViewModel = appStateViewModel)
        }
        composable("editor") {
            EditorScreen(navController = navController, appStateViewModel = appStateViewModel)
        }
        composable("terminal") {
            com.andcodedit.ui.screens.TerminalScreen(navController = navController, appStateViewModel = appStateViewModel)
        }
        composable("dex") {
            DexModeScreen(navController = navController)
        }
        composable("ai") {
            AIChatScreen(navController = navController, appStateViewModel = appStateViewModel)
        }
        composable("settings") {
            com.andcodedit.ui.screens.SettingsScreen(
                navController = navController,
                appStateViewModel = appStateViewModel
            )
        }
        composable("monaco") {
            com.andcodedit.ui.screens.MonacoEditorScreen(
                navController = navController,
                appStateViewModel = appStateViewModel
            )
        }
        composable("runner") {
            com.andcodedit.ui.screens.RunnerScreen(
                navController = navController,
                appStateViewModel = appStateViewModel
            )
        }
        composable("toolchains") {
            com.andcodedit.ui.screens.ToolchainScreen(
                navController = navController,
                appStateViewModel = appStateViewModel
            )
        }
    }
}