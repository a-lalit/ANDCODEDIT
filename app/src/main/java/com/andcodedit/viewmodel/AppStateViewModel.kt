package com.andcodedit.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andcodedit.ai.AiAgentService
import com.andcodedit.git.GitService
import com.andcodedit.terminal.TerminalGrid
import com.andcodedit.terminal.TerminalSession
import com.andcodedit.terminal.TerminalSessionFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AppStateViewModel - Central state management for ANDCODEDIT.
 * Handles shared state for editor tabs, terminal sessions, Git, AI, desktop layout persistence.
 * Uses SavedStateHandle for config change survival.
 * Full working implementation.
 */
class AppStateViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionFactory: (rows: Int, cols: Int, shell: String, onOutput: (ByteArray) -> Unit) -> TerminalSession =
        { rows, cols, shell, onOutput -> TerminalSessionFactory.create(rows, cols, shell, onOutput) }
) : ViewModel() {

    // Editor tabs state (persisted)
    private val _openEditorTabs = MutableStateFlow(
        savedStateHandle.get<List<String>>("openEditorTabs") ?: listOf("MainActivity.kt", "DexParserService.kt")
    )
    val openEditorTabs: StateFlow<List<String>> = _openEditorTabs.asStateFlow()

    private val _currentEditorTab = MutableStateFlow(
        savedStateHandle.get<Int>("currentEditorTab") ?: 0
    )
    val currentEditorTab: StateFlow<Int> = _currentEditorTab.asStateFlow()

    // Terminal sessions (multiple tabs)
    private val _terminalSessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val terminalSessions: StateFlow<List<TerminalSession>> = _terminalSessions.asStateFlow()

    private val _terminalGrids = MutableStateFlow<List<TerminalGrid>>(emptyList())
    val terminalGrids: StateFlow<List<TerminalGrid>> = _terminalGrids.asStateFlow()

    private val _currentTerminalTab = MutableStateFlow(0)
    val currentTerminalTab: StateFlow<Int> = _currentTerminalTab.asStateFlow()

    // Git state
    val gitService = GitService()

    // AI state
    val aiService = AiAgentService()

    // Desktop layout persistence (panel sizes, visibility)
    private val _sidebarWidth = MutableStateFlow(savedStateHandle.get<Float>("sidebarWidth") ?: 280f)
    val sidebarWidth: StateFlow<Float> = _sidebarWidth.asStateFlow()

    private val _isTerminalVisible = MutableStateFlow(savedStateHandle.get<Boolean>("isTerminalVisible") ?: true)
    val isTerminalVisible: StateFlow<Boolean> = _isTerminalVisible.asStateFlow()

    private val _terminalHeight = MutableStateFlow(savedStateHandle.get<Float>("terminalHeight") ?: 260f)
    val terminalHeight: StateFlow<Float> = _terminalHeight.asStateFlow()

    // Terminal preferences (surfaced in SettingsScreen)
    private val _terminalFontSize = MutableStateFlow(savedStateHandle.get<Float>("terminalFontSize") ?: 14f)
    val terminalFontSize: StateFlow<Float> = _terminalFontSize.asStateFlow()

    private val _terminalShell = MutableStateFlow(savedStateHandle.get<String>("terminalShell") ?: "/system/bin/sh")
    val terminalShell: StateFlow<String> = _terminalShell.asStateFlow()

    init {
        // Initialize default terminal tab
        if (_terminalSessions.value.isEmpty()) {
            createNewTerminalTab()
        }

        // Observe and save state
        viewModelScope.launch {
            _openEditorTabs.collect { savedStateHandle["openEditorTabs"] = it }
        }
        viewModelScope.launch {
            _currentEditorTab.collect { savedStateHandle["currentEditorTab"] = it }
        }
        viewModelScope.launch {
            _sidebarWidth.collect { savedStateHandle["sidebarWidth"] = it }
        }
        viewModelScope.launch {
            _isTerminalVisible.collect { savedStateHandle["isTerminalVisible"] = it }
        }
        viewModelScope.launch {
            _terminalHeight.collect { savedStateHandle["terminalHeight"] = it }
        }
        viewModelScope.launch {
            _terminalFontSize.collect { savedStateHandle["terminalFontSize"] = it }
        }
        viewModelScope.launch {
            _terminalShell.collect { savedStateHandle["terminalShell"] = it }
        }
    }

    fun updateTerminalFontSize(size: Float) {
        _terminalFontSize.value = size.coerceIn(8f, 32f)
    }

    fun updateTerminalShell(shell: String) {
        _terminalShell.value = shell
    }

    // Editor functions
    fun addEditorTab(fileName: String) {
        if (!_openEditorTabs.value.contains(fileName)) {
            _openEditorTabs.value = _openEditorTabs.value + fileName
        }
        _currentEditorTab.value = _openEditorTabs.value.indexOf(fileName)
    }

    fun closeEditorTab(index: Int) {
        if (_openEditorTabs.value.size > 1) {
            val newList = _openEditorTabs.value.toMutableList()
            newList.removeAt(index)
            _openEditorTabs.value = newList
            if (_currentEditorTab.value >= newList.size) {
                _currentEditorTab.value = newList.lastIndex
            }
        }
    }

    fun switchEditorTab(index: Int) {
        if (index in _openEditorTabs.value.indices) {
            _currentEditorTab.value = index
        }
    }

    // Terminal functions (multiple tabs + splits ready)
    fun createNewTerminalTab() {
        val newGrid = TerminalGrid(initialRows = 30, initialCols = 100)
        val newSession = sessionFactory(30, 100, _terminalShell.value) { data ->
            newGrid.appendOutput(data)
        }
        _terminalGrids.value = _terminalGrids.value + newGrid
        _terminalSessions.value = _terminalSessions.value + newSession
        _currentTerminalTab.value = _terminalSessions.value.lastIndex
    }

    fun switchTerminalTab(index: Int) {
        if (index in _terminalSessions.value.indices) {
            _currentTerminalTab.value = index
        }
    }

    fun closeTerminalTab(index: Int) {
        if (_terminalSessions.value.size > 1) {
            val newSessions = _terminalSessions.value.toMutableList()
            val newGrids = _terminalGrids.value.toMutableList()
            newSessions.removeAt(index).close()
            newGrids.removeAt(index)
            _terminalSessions.value = newSessions
            _terminalGrids.value = newGrids
            if (_currentTerminalTab.value >= newSessions.size) {
                _currentTerminalTab.value = newSessions.lastIndex
            }
        }
    }

    // Desktop layout
    fun updateSidebarWidth(width: Float) {
        _sidebarWidth.value = width.coerceIn(180f, 600f)
    }

    fun toggleTerminalVisibility() {
        _isTerminalVisible.value = !_isTerminalVisible.value
    }

    fun updateTerminalHeight(height: Float) {
        _terminalHeight.value = height.coerceIn(120f, 500f)
    }

    // AI integration
    fun queryAI(query: String, terminalSession: TerminalSession? = null): String {
        return aiService.processAgentQuery(query, terminalSession)
    }

    override fun onCleared() {
        super.onCleared()
        _terminalSessions.value.forEach { it.close() }
        gitService.close()
    }
}