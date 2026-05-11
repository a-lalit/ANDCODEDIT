package com.andcodedit.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.andcodedit.terminal.TerminalGrid
import com.andcodedit.terminal.TerminalSessionFactory
import com.andcodedit.terminal.TerminalView
import com.andcodedit.viewmodel.AppStateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController, appStateViewModel: AppStateViewModel) {
    val focusRequester = remember { FocusRequester() }
    val sessions = appStateViewModel.terminalSessions.collectAsState().value
    val grids = appStateViewModel.terminalGrids.collectAsState().value
    val currentTab = appStateViewModel.currentTerminalTab.collectAsState().value

    // Ensure at least one tab
    LaunchedEffect(sessions.size) {
        if (sessions.isEmpty()) {
            appStateViewModel.createNewTerminalTab()
        }
    }

    val currentSession = sessions.getOrNull(currentTab)
    val currentGrid = grids.getOrNull(currentTab)

    var showSettings by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal - Tab ${currentTab + 1} / ${sessions.size}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = { appStateViewModel.createNewTerminalTab() }) {
                        Text("+")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Terminal Tabs
            if (sessions.size > 1) {
                ScrollableTabRow(selectedTabIndex = currentTab) {
                    sessions.forEachIndexed { index, _ ->
                        Tab(
                            selected = index == currentTab,
                            onClick = { appStateViewModel.switchTerminalTab(index) },
                            text = { Text("Tab ${index + 1}") }
                        )
                    }
                }
            }

            // Main Terminal View with mouse scroll and context menu
            if (currentGrid != null && currentSession != null) {
                TerminalView(
                    grid = currentGrid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .focusable()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    focusRequester.requestFocus()
                                },
                                onLongPress = { offset ->
                                    contextMenuPosition = offset
                                    showContextMenu = true
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // Mouse wheel scroll support
                            detectDragGestures { change, dragAmount ->
                                if (dragAmount.y != 0f) {
                                    currentGrid.scrollBy(-dragAmount.y.toInt() / 20) // Scroll speed
                                }
                            }
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val char = keyEvent.utf16CodePoint?.toChar()
                                when {
                                    keyEvent.key == Key.Enter -> {
                                        currentSession.writeInput("\r".toByteArray())
                                        true
                                    }
                                    keyEvent.key == Key.Backspace -> {
                                        currentSession.writeInput(byteArrayOf(0x7f))
                                        true
                                    }
                                    keyEvent.key == Key.Tab -> {
                                        currentSession.writeInput("\t".toByteArray())
                                        true
                                    }
                                    keyEvent.key == Key.DirectionUp -> {
                                        currentSession.writeInput("\u001b[A".toByteArray())
                                        true
                                    }
                                    keyEvent.key == Key.DirectionDown -> {
                                        currentSession.writeInput("\u001b[B".toByteArray())
                                        true
                                    }
                                    keyEvent.key == Key.DirectionRight -> {
                                        currentSession.writeInput("\u001b[C".toByteArray())
                                        true
                                    }
                                    keyEvent.key == Key.DirectionLeft -> {
                                        currentSession.writeInput("\u001b[D".toByteArray())
                                        true
                                    }
                                    char != null && !char.isISOControl() -> {
                                        currentSession.writeInput(char.toString().toByteArray())
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )

                // Context Menu
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = androidx.compose.ui.unit.IntOffset(contextMenuPosition.x.toInt(), contextMenuPosition.y.toInt())
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            // TODO: Implement copy from grid selection if supported
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = {
                            // TODO: Paste from clipboard
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear") },
                        onClick = {
                            currentGrid.clear()
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close Tab") },
                        onClick = {
                            appStateViewModel.closeTerminalTab(currentTab)
                            showContextMenu = false
                        }
                    )
                }

                // Helper input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var inputText by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type command and press Send") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    currentSession.writeInput("$inputText\r".toByteArray())
                                    inputText = ""
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        if (inputText.isNotBlank()) {
                            currentSession.writeInput("$inputText\r".toByteArray())
                            inputText = ""
                        }
                    }) {
                        Text("Send")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No terminal session. Creating new one...")
                }
            }
        }
    }

    // Settings Dialog
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Terminal Settings") },
            text = {
                Column {
                    Text("Shell: /system/bin/sh (default)")
                    Text("TERM: xterm-256color")
                    Text("Font Size: 14sp (adjust in code)")
                    // Add more settings like font size slider if needed
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close")
                }
            }
        )
    }
}