package com.andcodedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.andcodedit.navigation.AppNavigation
import com.andcodedit.ui.layouts.ExpandedDesktopLayout
import com.andcodedit.ui.theme.ANDCODEDITTheme
import com.andcodedit.viewmodel.AppStateViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ANDCODEDITTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    val navController = rememberNavController()
                    val appStateViewModel: AppStateViewModel = viewModel()

                    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                        // Android 16 Desktop / large screen / DeX / external display
                        ExpandedDesktopLayout(
                            appStateViewModel = appStateViewModel,
                            onClose = { finish() }
                        )
                    } else {
                        // Mobile / compact / medium screens
                        AppNavigation(
                            navController = navController,
                            appStateViewModel = appStateViewModel
                        )
                    }
                }
            }
        }
    }
}
