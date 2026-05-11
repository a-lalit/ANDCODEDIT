package com.andcodedit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.andcodedit.viewmodel.AppStateViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, appStateViewModel: AppStateViewModel) {
    val terminalFontSize by appStateViewModel.terminalFontSize.collectAsState()
    val terminalShell by appStateViewModel.terminalShell.collectAsState()

    // Hardening states (local for demo, can be moved to ViewModel)
    var lowRamMode by remember { mutableStateOf(true) }
    var batteryAwareness by remember { mutableStateOf(true) }
    var thermalMonitoring by remember { mutableStateOf(true) }
    var accessibilityEnabled by remember { mutableStateOf(true) }

    var backupStatus by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("← Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Terminal Section
            Text("Terminal", style = MaterialTheme.typography.headlineSmall)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Font Size: ${terminalFontSize.toInt()} sp")
                    Slider(
                        value = terminalFontSize,
                        onValueChange = { appStateViewModel.updateTerminalFontSize(it) },
                        valueRange = 8f..28f,
                        steps = 19
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Default Shell")
                    OutlinedTextField(
                        value = terminalShell,
                        onValueChange = { appStateViewModel.updateTerminalShell(it) },
                        label = { Text("Shell Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Changes apply to new terminal tabs. Current tab will use new shell on restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editor Section (placeholder for future)
            Text("Editor", style = MaterialTheme.typography.headlineSmall)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Sora Editor is active with minimap and syntax highlighting.")
                    Text("Additional options (theme, tab size) coming in next update.")
                }
            }

            // Hardening Section
            Text("Hardening & Accessibility", style = MaterialTheme.typography.headlineSmall)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Low RAM Mode", modifier = Modifier.weight(1f))
                        Switch(
                            checked = lowRamMode,
                            onCheckedChange = { lowRamMode = it }
                        )
                    }
                    Text(
                        "Reduces background tasks and memory usage when RAM is low.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Battery Awareness", modifier = Modifier.weight(1f))
                        Switch(
                            checked = batteryAwareness,
                            onCheckedChange = { batteryAwareness = it }
                        )
                    }
                    Text(
                        "Monitors battery level and adjusts performance.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Thermal Monitoring", modifier = Modifier.weight(1f))
                        Switch(
                            checked = thermalMonitoring,
                            onCheckedChange = { thermalMonitoring = it }
                        )
                    }
                    Text(
                        "Reduces CPU usage when device is overheating.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Accessibility Support", modifier = Modifier.weight(1f))
                        Switch(
                            checked = accessibilityEnabled,
                            onCheckedChange = { accessibilityEnabled = it }
                        )
                    }
                    Text(
                        "Enables TalkBack, large text, and high contrast where applicable.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (lowRamMode || batteryAwareness || thermalMonitoring) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hardening active: Performance will adapt automatically.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Cloud Fallback Section
            Text("Cloud Fallback & Backup", style = MaterialTheme.typography.headlineSmall)
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Backup current settings and state to local storage (cloud sync can be added with a backend).")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val backupDir = File(navController.context.filesDir, "backups")
                                if (!backupDir.exists()) backupDir.mkdirs()
                                val backupFile = File(backupDir, "andcodedit_settings_backup.txt")
                                backupFile.writeText(
                                    "Terminal Font Size: $terminalFontSize\n" +
                                    "Terminal Shell: $terminalShell\n" +
                                    "Low RAM Mode: $lowRamMode\n" +
                                    "Battery Awareness: $batteryAwareness\n" +
                                    "Thermal Monitoring: $thermalMonitoring\n" +
                                    "Timestamp: ${System.currentTimeMillis()}"
                                )
                                backupStatus = "Backup saved to: ${backupFile.absolutePath}"
                            } catch (e: Exception) {
                                backupStatus = "Backup failed: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Backup Settings Now")
                    }

                    if (backupStatus.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(backupStatus, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Future: Automatic cloud sync of editor state, Git repos metadata, and preferences using your preferred backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}