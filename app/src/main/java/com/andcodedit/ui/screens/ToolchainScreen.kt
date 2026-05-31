package com.andcodedit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.andcodedit.lang.LanguageRegistry
import com.andcodedit.lang.RuntimeManager
import com.andcodedit.runtime.BootstrapManager
import com.andcodedit.runtime.InstallEvent
import com.andcodedit.runtime.Toolchain
import com.andcodedit.runtime.ToolchainCatalog
import com.andcodedit.viewmodel.AppStateViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ToolchainScreen — UI for the on-device **toolchain provisioning layer**.
 *
 * Surfaces [BootstrapManager]'s view of the world (which backend will be used,
 * whether the environment is bootstrapped) and lets the user install any of the
 * [ToolchainCatalog] packages on demand. Toolchains are provisioned at runtime
 * rather than bundled into the APK — see [BootstrapManager] for the rationale.
 *
 * Each row cross-checks current availability via [RuntimeManager] (does the
 * package's first language already resolve its binaries on the PATH?) and, when
 * the user taps Install, streams the package manager output live into an
 * expandable log area with a progress indicator.
 */

/** Per-toolchain UI state held across recompositions. */
private class ToolchainUiState {
    val logs = mutableStateListOf<String>()
    var progress by mutableStateOf<Int?>(null)
    var installing by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    var job: Job? = null
    var lastResult by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolchainScreen(navController: NavController, appStateViewModel: AppStateViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bootstrap = remember { BootstrapManager(context) }

    // Snapshot of the provisioning environment; refreshable after installs.
    var envInfo by remember { mutableStateOf(bootstrap.environmentInfo()) }
    val backend = envInfo["backend"] ?: BootstrapManager.Backend.NONE.name
    val bootstrapped = envInfo["bootstrapped"]?.toBoolean() ?: false

    // One mutable UI state per toolchain, keyed by id.
    val uiStates = remember { mutableStateMapOf<String, ToolchainUiState>() }
    fun stateFor(tc: Toolchain): ToolchainUiState =
        uiStates.getOrPut(tc.id) { ToolchainUiState() }

    // Availability is a runtime property: recomputed when asked. We track a
    // simple version counter so installs can trigger a re-check of the rows.
    var availabilityVersion by remember { mutableStateOf(0) }
    val availability = remember(availabilityVersion) {
        ToolchainCatalog.all.associate { tc -> tc.id to isToolchainAvailable(tc) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toolchains") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ---- Environment status card ----
            item {
                EnvironmentCard(envInfo = envInfo, backend = backend, bootstrapped = bootstrapped)
            }

            // ---- No-backend explanation banner ----
            if (backend == BootstrapManager.Backend.NONE.name) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "No provisioning backend",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Toolchains are installed on demand, not bundled. Install the " +
                                        "Termux app (F-Droid) for a turnkey environment, or run the " +
                                        "local bootstrap to unpack a userland into " +
                                        "${envInfo["prefixDir"]}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // ---- Toolchain catalogue ----
            items(ToolchainCatalog.all, key = { it.id }) { tc ->
                val state = stateFor(tc)
                val installed = availability[tc.id] ?: false

                ToolchainRow(
                    toolchain = tc,
                    installed = installed,
                    state = state,
                    onInstall = {
                        if (state.installing) return@ToolchainRow
                        state.logs.clear()
                        state.progress = null
                        state.lastResult = null
                        state.installing = true
                        state.expanded = true
                        state.job = scope.launch {
                            bootstrap.installPackage(tc)
                                .catch { e ->
                                    state.logs.add("Install error: ${e.message}")
                                    state.installing = false
                                }
                                .collect { event ->
                                    when (event) {
                                        is InstallEvent.Log -> state.logs.add(event.line)
                                        is InstallEvent.Progress -> state.progress = event.pct
                                        is InstallEvent.Done -> {
                                            state.logs.add(
                                                if (event.success) "[ok] ${event.message}"
                                                else "[failed] ${event.message}"
                                            )
                                            state.lastResult = event.message
                                            state.installing = false
                                            // Re-evaluate availability + environment after a finish.
                                            envInfo = bootstrap.environmentInfo()
                                            availabilityVersion++
                                        }
                                    }
                                }
                            state.installing = false
                        }
                    },
                    onCancel = {
                        state.job?.cancel()
                        state.job = null
                        state.installing = false
                        state.logs.add("[cancelled]")
                    },
                    onToggleExpand = { state.expanded = !state.expanded }
                )
            }
        }
    }
}

/** Returns whether the first language a [toolchain] enables already resolves. */
private fun isToolchainAvailable(toolchain: Toolchain): Boolean {
    val lang = toolchain.languages
        .asSequence()
        .mapNotNull { LanguageRegistry.byId(it) }
        .firstOrNull() ?: return false
    return RuntimeManager.isAvailable(lang)
}

@Composable
private fun EnvironmentCard(
    envInfo: Map<String, String>,
    backend: String,
    bootstrapped: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Environment", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            InfoLine("Backend", backend)
            InfoLine("Bootstrapped", if (bootstrapped) "yes" else "no")
            InfoLine("Termux installed", envInfo["termuxInstalled"] ?: "false")
            InfoLine("Prefix", envInfo["prefixDir"] ?: "—")
            Spacer(Modifier.height(6.dp))
            Text(
                "Toolchains are provisioned on demand into app-private storage, " +
                    "not bundled in the APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolchainRow(
    toolchain: Toolchain,
    installed: Boolean,
    state: ToolchainUiState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            toolchain.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (installed) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Available",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        "Enables: ${toolchain.languages.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "pkg ${toolchain.pkgName} • ~${toolchain.sizeMb} MB",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.installing) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                } else {
                    Button(onClick = onInstall) {
                        Text(if (installed) "Reinstall" else "Install")
                    }
                }
            }

            // Progress indicator while installing.
            if (state.installing) {
                Spacer(Modifier.height(8.dp))
                val pct = state.progress
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Expandable log area.
            if (state.logs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Log (${state.logs.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            if (state.expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (state.expanded) "Collapse" else "Expand"
                        )
                    }
                }
                if (state.expanded) {
                    val logScroll = rememberScrollState()
                    LaunchedEffect(state.logs.size) {
                        logScroll.animateScrollTo(logScroll.maxValue)
                    }
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E))
                                .verticalScroll(logScroll)
                                .padding(8.dp)
                        ) {
                            state.logs.forEach { line ->
                                Text(
                                    line,
                                    color = Color(0xFFE0E0E0),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
