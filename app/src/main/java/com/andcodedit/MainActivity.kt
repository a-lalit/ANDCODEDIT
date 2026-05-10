package com.andcodedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.andcodedit.navigation.AppNavigation
import com.andcodedit.ui.layouts.ExpandedDesktopLayout
import com.andcodedit.ui.theme.ANDCODEDITTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ANDCODEDITTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    val navController = rememberNavController()

                    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                        // Android 16 Desktop / Large screen / DeX / External display
                        ExpandedDesktopLayout(
                            onClose = { finish() },
                            onOpenSettings = { /* TODO: open settings screen */ }
                        )
                    } else {
                        // Mobile / Compact / Medium screens
                        AppNavigation(navController = navController)
                    }
                }
            }
        }
    }
}