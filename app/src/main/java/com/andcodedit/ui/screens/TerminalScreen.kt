package com.andcodedit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.andcodedit.terminal.TerminalGrid
import com.andcodedit.terminal.TerminalSession
import com.andcodedit.viewmodel.AppStateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavHostController, appStateViewModel: AppStateViewModel) {
    val sessions by appStateViewModel.terminalSessions.collectAsState()
    val grids by appStateViewModel.terminalGrids.collectAsState()
    val currentTab by appStateViewModel.currentTerminalTab.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { appStateViewModel.createNewTerminalTab() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Tab")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
        ) {
            if (sessions.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = currentTab.coerceIn(0, maxOf(0, sessions.size - 1)),
                    containerColor = Color(0xFF2D2D2D),
                    contentColor = Color.White
                ) {
                    sessions.forEachIndexed { index, _ ->
                        Tab(
                            selected = index == currentTab,
                            onClick = { appStateViewModel.switchTerminalTab(index) },
                            text = { Text("sh ${index + 1}") }
                        )
                    }
                }
            }

            val currentGrid = grids.getOrNull(currentTab)
            val currentSession = sessions.getOrNull(currentTab)

            if (currentGrid != null) {
                TerminalView(
                    grid = currentGrid,
                    session = currentSession,
                    scope = scope
                )
            }
        }
    }
}

@Composable
fun TerminalView(
    grid: TerminalGrid,
    session: TerminalSession?,
    scope: CoroutineScope
) {
    val outputText by grid.outputFlow.collectAsState()
    val listState = rememberLazyListState()
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }

    val lines = remember(outputText) { outputText.lines() }

    // Auto-scroll to bottom on new output.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", color = Color(0xFF4EC9B0), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            BasicTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                modifier = Modifier.weight(1f),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val cmd = inputValue.text
                        scope.launch {
                            session?.writeInput((cmd + "\n").toByteArray())
                        }
                        inputValue = TextFieldValue("")
                    }
                )
            )
        }
    }
}
