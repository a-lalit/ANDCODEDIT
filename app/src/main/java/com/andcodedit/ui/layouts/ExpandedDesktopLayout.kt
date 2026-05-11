package com.andcodedit.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andcodedit.git.GitService
import com.andcodedit.ui.components.ResizableHorizontalPane
import com.andcodedit.ui.components.ResizableVerticalPane

/**
 * VS Code / Android Studio style desktop layout for large screens / Android 16 Desktop Mode / DeX.
 *
 * Uses ResizableHorizontalPane for sidebar and ResizableVerticalPane for terminal.
 * Fully self-contained demo with internal state. Replace placeholders with real ViewModels + Editor + Terminal.
 */
@Composable
fun ExpandedDesktopLayout(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    // --- State ---
    var selectedActivity by remember { mutableStateOf(0) } // 0=Files, 1=Search, 2=Git, 3=Extensions
    var sidebarWidth by remember { mutableStateOf(280.dp) }
    var isTerminalVisible by remember { mutableStateOf(true) }
    var terminalHeight by remember { mutableStateOf(260.dp) }

    // Editor state (demo)
    val openFiles = remember { mutableStateListOf("MainActivity.kt", "TerminalScreen.kt", "HomeScreen.kt", "AppNavigation.kt") }
    var selectedFileIndex by remember { mutableStateOf(0) }
    val currentFile = openFiles.getOrNull(selectedFileIndex) ?: "Untitled.kt"

    // Fake file tree data
    val fileTree = listOf(
        "app/src/main/java/com/andcodedit/",
        "├── MainActivity.kt",
        "├── navigation/AppNavigation.kt",
        "├── ui/",
        "│   ├── screens/",
        "│   │   ├── HomeScreen.kt",
        "│   │   ├── EditorScreen.kt",
        "│   │   ├── TerminalScreen.kt",
        "│   │   ├── DexModeScreen.kt",
        "│   │   ├── TerminalPlaceholderScreen.kt",
        "│   ├── theme/Theme.kt",
        "├── terminal/",
        "│   ├── TerminalGrid.kt",
        "├── TerminalSession.kt",
        "├── TerminalView.kt",
        "├── viewmodel/AppStateViewModel.kt (planned)",
        "├── dex/DexParserService.kt",
        "├── git/GitService.kt",
        "└── ui/layouts/ExpandedDesktopLayout.kt"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Title Bar (desktop window bar simulation)
            DesktopTitleBar(
                currentFile = currentFile,
                onClose = onClose,
                onMinimize = { /* no-op in compose */ },
                onMaximize = { /* toggle fullscreen if wanted */ },
                onOpenSettings = onOpenSettings
            )

            // Main content row: ActivityBar + Resizable Sidebar + Main Editor Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // === Activity Bar (leftmost, narrow, icon only) ===
                ActivityBar(
                    selectedIndex = selectedActivity,
                    onActivitySelected = { selectedActivity = it }
                )

                // === Primary Sidebar (Files / Search / Git / Outline) ===
                ResizableHorizontalPane(
                    initialLeftWidth = sidebarWidth,
                    minLeftWidth = 200.dp,
                    maxLeftWidth = 520.dp,
                    leftContent = { paneMod ->
                        PrimarySidebar(
                            modifier = paneMod,
                            selectedActivity = selectedActivity,
                            fileTree = fileTree,
                            onFileClick = { fileName ->
                                // Open file in editor tabs (demo)
                                if (!openFiles.contains(fileName)) {
                                    openFiles.add(fileName)
                                }
                                selectedFileIndex = openFiles.indexOf(fileName)
                            }
                        )
                    },
                    rightContent = { paneMod ->
                        // === Main content area: Editor + resizable Terminal ===
                        if (isTerminalVisible) {
                            ResizableVerticalPane(
                                modifier = paneMod,
                                initialBottomHeight = terminalHeight,
                                minBottomHeight = 140.dp,
                                maxBottomHeight = 480.dp,
                                topContent = { editorMod ->
                                    EditorArea(
                                        modifier = editorMod,
                                        openFiles = openFiles,
                                        selectedIndex = selectedFileIndex,
                                        currentFile = currentFile,
                                        onTabSelected = { selectedFileIndex = it },
                                        onTabClosed = { index ->
                                            if (openFiles.size > 1) {
                                                openFiles.removeAt(index)
                                                if (selectedFileIndex >= openFiles.size) {
                                                    selectedFileIndex = openFiles.lastIndex
                                                }
                                            }
                                        },
                                        onSplitEditor = { /* TODO: implement split editor groups in Phase 4 */ }
                                    )
                                },
                                bottomContent = { termMod ->
                                    TerminalPanel(
                                        modifier = termMod,
                                        onClose = { isTerminalVisible = false }
                                    )
                                },
                                onBottomHeightChange = { terminalHeight = it }
                            )
                        } else {
                            // Terminal hidden - editor takes all space + reopen bar
                            Column(paneMod) {
                                EditorArea(
                                    modifier = Modifier.weight(1f),
                                    openFiles = openFiles,
                                    selectedIndex = selectedFileIndex,
                                    currentFile = currentFile,
                                    onTabSelected = { selectedFileIndex = it },
                                    onTabClosed = { index ->
                                        if (openFiles.size > 1) {
                                            openFiles.removeAt(index)
                                            if (selectedFileIndex >= openFiles.size) {
                                                selectedFileIndex = openFiles.lastIndex
                                            }
                                        }
                                    },
                                    onSplitEditor = { /* TODO: implement split editor groups in Phase 4 */ }
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { isTerminalVisible = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = "Show Terminal", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Show Terminal (Ctrl+`)", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                )
            }

            // Status bar at very bottom (common in IDEs)
            DesktopStatusBar(currentFile = currentFile)
        }
    }
}

/* ==================== Sub-composables ==================== */

@Composable
private fun DesktopTitleBar(
    currentFile: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon + name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Code,
                contentDescription = "ANDCODEDIT",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ANDCODEDIT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                "  —  Desktop Mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        // Current file in title
        Text(
            currentFile,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.weight(1f))

        // Window controls (mock)
        Row {
            IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Minimize", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CropSquare, contentDescription = "Maximize", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            }
        }

        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun ActivityBar(
    selectedIndex: Int,
    onActivitySelected: (Int) -> Unit
) {
    val activities = listOf(
        Icons.Default.Description to "Files",
        Icons.Default.Search to "Search",
        Icons.Default.AccountTree to "Git",
        Icons.Default.Extension to "Extensions"
    )

    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        activities.forEachIndexed { index, (icon, label) ->
            val isSelected = index == selectedIndex
            IconButton(
                onClick = { onActivitySelected(index) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom icons
        IconButton(onClick = { /* TODO: Accounts / Debug */ }) {
            Icon(Icons.Default.Person, contentDescription = "Account", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimarySidebar(
    modifier: Modifier = Modifier,
    selectedActivity: Int,
    fileTree: List<String>,
    onFileClick: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // Sidebar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedActivity) {
                    0 -> "EXPLORER"
                    1 -> "SEARCH"
                    2 -> "SOURCE CONTROL"
                    else -> "EXTENSIONS"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* refresh or more */ }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        when (selectedActivity) {
            0 -> {
                // File Explorer
                Text(
                    "ANDCODEDIT",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                LazyColumn {
                    items(fileTree) { line ->
                        val isFile = line.contains(".kt") || line.contains(".java") || line.contains(".smali")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isFile) { onFileClick(line.trimStart('│', ' ', '└', '├')) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFile) Icons.Default.Description else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            1 -> {
                // Search placeholder
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Search in files...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(16.dp))
                Text("Search results will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            2 -> {
                // Git / Source Control - Full JGit integration
                val gitService = remember { GitService() }
                var repoPath by remember { mutableStateOf("/sdcard/ANDCODEDIT") }
                var commitMessage by remember { mutableStateOf("Update from ANDCODEDIT editor") }
                var remoteUrl by remember { mutableStateOf("https://github.com/a-lalit/ANDCODEDIT.git") }
                var username by remember { mutableStateOf("your-username") }
                var password by remember { mutableStateOf("your-token-here") }
                var gitStatus by remember { mutableStateOf("No repo loaded") }

                Column {
                    Text("Source Control (JGit)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = repoPath,
                        onValueChange = { repoPath = it },
                        label = { Text("Repo Path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (gitService.openOrInitRepo(repoPath)) {
                                gitStatus = gitService.statusMessage
                            }
                        }) {
                            Text("Open/Init Repo")
                        }
                        Button(onClick = {
                            gitStatus = gitService.getStatus()
                        }) {
                            Text("Refresh Status")
                        }
                    }

                    if (gitService.isRepoLoaded) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            label = { Text("Commit Message") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = {
                            if (gitService.commitAll(commitMessage)) {
                                gitStatus = gitService.statusMessage
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Commit All Changes")
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = remoteUrl,
                            onValueChange = { remoteUrl = it },
                            label = { Text("Remote URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Token/Password") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = {
                            if (gitService.pushToRemote(remoteUrl, username, password)) {
                                gitStatus = gitService.statusMessage
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Push to Remote")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(gitStatus, style = MaterialTheme.typography.bodyMedium)
                            if (gitService.isRepoLoaded) {
                                Text("Repo ready for commit/push", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            else -> {
                Text("Extensions marketplace coming in Phase 4", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EditorArea(
    modifier: Modifier = Modifier,
    openFiles: List<String>,
    selectedIndex: Int,
    currentFile: String,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onSplitEditor: () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            openFiles.forEachIndexed { index, file ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                file,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onTabClosed(index) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                )
            }
        }

        // Editor content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E)) // Dark editor background like VS Code
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "// $currentFile",
                    color = Color(0xFF6A9955),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))

                // Fake editor content
                val fakeCode = when {
                    currentFile.contains("Terminal") -> listOf(
                        "package com.andcodedit.ui.screens",
                        "",
                        "import androidx.compose.runtime.*",
                        "import com.andcodedit.terminal.*",
                        "",
                        "@Composable",
                        "fun TerminalScreen(navController: NavController) {",
                        "    val grid = remember { TerminalGrid() }",
                        "    val session = remember { TerminalSessionFactory.create(...) }",
                        "    // Full PTY + VT100 rendering here",
                        "    TerminalView(grid = grid, modifier = Modifier.fillMaxSize())",
                        "}"
                    )
                    currentFile.contains("MainActivity") -> listOf(
                        "class MainActivity : ComponentActivity() {",
                        "    override fun onCreate(savedInstanceState: Bundle?) {",
                        "        super.onCreate(savedInstanceState)",
                        "        setContent {",
                        "            ANDCODEDITTheme {",
                        "                val windowSize = calculateWindowSizeClass(this)",
                        "                if (windowSize.widthSizeClass == WindowWidthSizeClass.Expanded) {",
                        "                    ExpandedDesktopLayout()",
                        "                } else {",
                        "                    AppNavigation(...) }",
                        "            }",
                        "        }",
                        "    }",
                        "}"
                    )
                    else -> listOf(
                        "Composable",
                        "fun ${currentFile.removeSuffix(".kt")}() {",
                        "    // TODO: Real editor integration (Rosemoe / custom Compose editor)",
                        "    Text(\"Professional code editor will render here\")",
                        "    // Syntax highlighting, minimap, multi-cursor, folding, etc.",
                        "}"
                    )
                }

                fakeCode.forEach { line ->
                    Text(
                        text = line,
                        color = Color(0xFFD4D4D4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Floating actions
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onSplitEditor, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.VerticalSplit, contentDescription = "Split Editor", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Split", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TerminalPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(4.dp)
    ) {
        // Terminal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFF252526)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "  TERMINAL  —  bash  (PTY)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFCCCCCC),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close terminal", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Here we would embed the real TerminalView
        // For demo we show a nice placeholder that matches the existing TerminalView style
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column {
                Text(
                    "ANDCODEDIT Demo Terminal (input handling active)",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Text("$ ls -la", color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("total 42", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("drwxr-xr-x  3 user  staff   96 May 10 11:58 .", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("drwxr-xr-x  8 user  staff  256 May  9 19:40 ..", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("-rw-r--r--  1 user  staff  2.4K May 10 06:13 README.md", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Type commands here. Real PTY + full VT100/ANSI parsing connected to Android Linux shell.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Note about integration
            Text(
                "TODO: Replace with TerminalView(grid = session.grid, modifier = Modifier.fillMaxSize())",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
private fun DesktopStatusBar(currentFile: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Ln 42, Col 18  |  UTF-8  |  Kotlin  |  $currentFile",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Android 16 Desktop Mode  •  120 FPS  •  Phase 3",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
