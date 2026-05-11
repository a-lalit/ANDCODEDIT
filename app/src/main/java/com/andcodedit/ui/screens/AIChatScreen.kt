package com.andcodedit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.andcodedit.viewmodel.AppStateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(navController: NavController, appStateViewModel: AppStateViewModel) {
    var query by remember { mutableStateOf("") }
    var responses by remember { mutableStateOf(listOf<String>()) }
    val currentTerminalSession = appStateViewModel.terminalSessions.collectAsState().value.getOrNull(
        appStateViewModel.currentTerminalTab.collectAsState().value
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Agents Chat") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(responses) { response ->
                    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Text(response, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            Row {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about code, run terminal command, or query RAG...") }
                )
                Button(onClick = {
                    if (query.isNotBlank()) {
                        val response = appStateViewModel.queryAI(query, currentTerminalSession)
                        responses = responses + "You: $query\n\nAI: $response"
                        query = ""
                    }
                }) {
                    Text("Send")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (currentTerminalSession != null) {
                    val cmd = "ls -la"
                    val toolResult = appStateViewModel.aiService.callTerminalTool(currentTerminalSession, cmd)
                    responses = responses + "Tool call (ls -la): $toolResult"
                }
            }) {
                Text("Run Terminal Tool Example (ls)")
            }
        }
    }
}