package com.andcodedit.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalGridTest {

    @Test
    fun `appendOutput accumulates plain text`() {
        val grid = TerminalGrid()
        grid.appendOutput("hello ")
        grid.appendOutput("world")
        assertEquals("hello world", grid.outputFlow.value)
    }

    @Test
    fun `appendOutput accepts raw bytes`() {
        val grid = TerminalGrid()
        grid.appendOutput("héllo\n".toByteArray(Charsets.UTF_8))
        assertEquals("héllo\n", grid.outputFlow.value)
    }

    @Test
    fun `ansi colour codes are stripped`() {
        val grid = TerminalGrid()
        grid.appendOutput("\u001B[31mred\u001B[0m plain")
        assertEquals("red plain", grid.outputFlow.value)
    }

    @Test
    fun `carriage returns are stripped`() {
        val grid = TerminalGrid()
        grid.appendOutput("progress\rdone\n")
        assertFalse(grid.outputFlow.value.contains('\r'))
    }

    @Test
    fun `clear empties the buffer`() {
        val grid = TerminalGrid()
        grid.appendOutput("something")
        grid.clear()
        assertEquals("", grid.outputFlow.value)
    }

    @Test
    fun `resize updates dimensions`() {
        val grid = TerminalGrid(initialRows = 30, initialCols = 100)
        grid.resize(40, 132)
        assertEquals(40, grid.rows)
        assertEquals(132, grid.cols)
    }

    @Test
    fun `scrollback is capped`() {
        val grid = TerminalGrid(maxScrollbackLines = 10)
        repeat(50) { grid.appendOutput("line $it\n") }
        val lines = grid.outputFlow.value.lines().filter { it.isNotEmpty() }
        assertTrue("expected <= 11 retained lines, got ${lines.size}", lines.size <= 11)
        assertTrue(lines.last() == "line 49")
    }
}
