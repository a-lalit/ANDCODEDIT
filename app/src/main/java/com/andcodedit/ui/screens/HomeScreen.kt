package com.andcodedit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.andcodedit.viewmodel.AppStateViewModel

private data class HomeFeature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accent: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, appStateViewModel: AppStateViewModel) {
    val features = listOf(
        HomeFeature("Editor", "Sora-powered code editing", Icons.Filled.Code, "editor", Color(0xFF4EC9B0)),
        HomeFeature("Terminal", "Real interactive shell", Icons.Filled.Terminal, "terminal", Color(0xFF569CD6)),
        HomeFeature("DEX Mode", "Bytecode & Smali tools", Icons.Filled.Memory, "dex", Color(0xFFC586C0)),
        HomeFeature("AI Agents", "Context-aware assistant", Icons.Filled.AutoAwesome, "ai", Color(0xFFDCDCAA)),
        HomeFeature("Settings", "Preferences & backup", Icons.Filled.Settings, "settings", Color(0xFFCE9178)),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ANDCODEDIT", fontWeight = FontWeight.Bold)
                        Text(
                            "Code. Debug. Reverse. Ship.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "Workspaces",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(features) { feature ->
                    FeatureCard(feature) { navController.navigate(feature.route) }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: HomeFeature, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(feature.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, contentDescription = feature.title, tint = feature.accent)
            }
            Column {
                Text(
                    feature.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    feature.subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
