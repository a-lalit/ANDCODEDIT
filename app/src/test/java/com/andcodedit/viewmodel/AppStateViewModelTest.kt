package com.andcodedit.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.andcodedit.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateViewModelTest {

    private class FakeTerminalSession(
        override var rows: Int,
        override var cols: Int
    ) : TerminalSession {
        var closed = false
        val written = mutableListOf<ByteArray>()

        override fun writeInput(data: ByteArray) {
            written.add(data)
        }

        override fun resize(rows: Int, cols: Int) {
            this.rows = rows
            this.cols = cols
        }

        override fun isAlive(): Boolean = !closed

        override fun close() {
            closed = true
        }
    }

    private val createdSessions = mutableListOf<FakeTerminalSession>()

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        AppStateViewModel(handle) { rows, cols, _, _ ->
            FakeTerminalSession(rows, cols).also { createdSessions.add(it) }
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        createdSessions.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Editor tabs ----

    @Test
    fun `addEditorTab appends and selects the new tab`() {
        val vm = newViewModel()
        vm.addEditorTab("Foo.kt")
        assertTrue(vm.openEditorTabs.value.contains("Foo.kt"))
        assertEquals(vm.openEditorTabs.value.lastIndex, vm.currentEditorTab.value)
    }

    @Test
    fun `addEditorTab selects existing tab instead of duplicating`() {
        val vm = newViewModel()
        val initial = vm.openEditorTabs.value
        vm.addEditorTab(initial.first())
        assertEquals(initial, vm.openEditorTabs.value)
        assertEquals(0, vm.currentEditorTab.value)
    }

    @Test
    fun `closeEditorTab keeps selection in bounds`() {
        val vm = newViewModel()
        vm.addEditorTab("Third.kt")
        vm.closeEditorTab(vm.openEditorTabs.value.lastIndex)
        assertTrue(vm.currentEditorTab.value <= vm.openEditorTabs.value.lastIndex)
    }

    @Test
    fun `last editor tab cannot be closed`() {
        val vm = newViewModel(SavedStateHandle(mapOf("openEditorTabs" to listOf("Only.kt"))))
        vm.closeEditorTab(0)
        assertEquals(listOf("Only.kt"), vm.openEditorTabs.value)
    }

    @Test
    fun `switchEditorTab ignores out-of-range indices`() {
        val vm = newViewModel()
        val before = vm.currentEditorTab.value
        vm.switchEditorTab(999)
        assertEquals(before, vm.currentEditorTab.value)
    }

    // ---- Terminal tabs ----

    @Test
    fun `a default terminal tab is created on init`() {
        val vm = newViewModel()
        assertEquals(1, vm.terminalSessions.value.size)
        assertEquals(1, vm.terminalGrids.value.size)
        assertEquals(0, vm.currentTerminalTab.value)
    }

    @Test
    fun `createNewTerminalTab adds and selects a session`() {
        val vm = newViewModel()
        vm.createNewTerminalTab()
        assertEquals(2, vm.terminalSessions.value.size)
        assertEquals(1, vm.currentTerminalTab.value)
    }

    @Test
    fun `closeTerminalTab closes the underlying session`() {
        val vm = newViewModel()
        vm.createNewTerminalTab()
        vm.closeTerminalTab(0)
        assertTrue(createdSessions[0].closed)
        assertFalse(createdSessions[1].closed)
        assertEquals(1, vm.terminalSessions.value.size)
    }

    @Test
    fun `last terminal tab cannot be closed`() {
        val vm = newViewModel()
        vm.closeTerminalTab(0)
        assertEquals(1, vm.terminalSessions.value.size)
        assertFalse(createdSessions[0].closed)
    }

    // ---- Layout / preferences clamping ----

    @Test
    fun `sidebar width is clamped`() {
        val vm = newViewModel()
        vm.updateSidebarWidth(50f)
        assertEquals(180f, vm.sidebarWidth.value)
        vm.updateSidebarWidth(9999f)
        assertEquals(600f, vm.sidebarWidth.value)
    }

    @Test
    fun `terminal height and font size are clamped`() {
        val vm = newViewModel()
        vm.updateTerminalHeight(10f)
        assertEquals(120f, vm.terminalHeight.value)
        vm.updateTerminalFontSize(100f)
        assertEquals(32f, vm.terminalFontSize.value)
    }

    @Test
    fun `toggleTerminalVisibility flips the flag`() {
        val vm = newViewModel()
        val before = vm.isTerminalVisible.value
        vm.toggleTerminalVisibility()
        assertEquals(!before, vm.isTerminalVisible.value)
    }

    @Test
    fun `state is persisted into SavedStateHandle`() {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)
        vm.addEditorTab("Persisted.kt")
        vm.updateSidebarWidth(300f)
        assertTrue(handle.get<List<String>>("openEditorTabs")!!.contains("Persisted.kt"))
        assertEquals(300f, handle.get<Float>("sidebarWidth"))
    }
}
