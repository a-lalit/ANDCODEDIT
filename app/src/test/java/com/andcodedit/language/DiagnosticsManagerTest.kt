package com.andcodedit.language

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsManagerTest {

    @Test
    fun `parses gcc style errors and warnings`() {
        val output = """
            main.c:3:5: error: use of undeclared identifier 'foo'
            main.c:7:1: warning: unused variable 'bar'
        """.trimIndent()

        val diags = DiagnosticsManager.parseDiagnostics(output, "main.c")

        assertEquals(2, diags.size)
        assertEquals(3, diags[0].line)
        assertEquals(5, diags[0].column)
        assertEquals(DiagnosticRegion.SEVERITY_ERROR, diags[0].severity)
        assertEquals("use of undeclared identifier 'foo'", diags[0].message)
        assertEquals(DiagnosticRegion.SEVERITY_WARNING, diags[1].severity)
    }

    @Test
    fun `parses python tracebacks`() {
        val output = """
            Traceback (most recent call last):
              File "main.py", line 12, in <module>
            NameError: name 'x' is not defined
        """.trimIndent()

        val diags = DiagnosticsManager.parseDiagnostics(output, "main.py")

        assertEquals(1, diags.size)
        assertEquals(12, diags[0].line)
        assertEquals(DiagnosticRegion.SEVERITY_ERROR, diags[0].severity)
    }

    @Test
    fun `parses typescript tsc output`() {
        val output = "main.ts(4,10): error TS2304: Cannot find name 'foo'."

        val diags = DiagnosticsManager.parseDiagnostics(output, "main.ts")

        assertEquals(1, diags.size)
        assertEquals(4, diags[0].line)
        assertEquals(10, diags[0].column)
        assertEquals("Cannot find name 'foo'.", diags[0].message)
    }

    @Test
    fun `parses javac output`() {
        val output = "Main.java:9: error: cannot find symbol"

        val diags = DiagnosticsManager.parseDiagnostics(output, "Main.java")

        assertEquals(1, diags.size)
        assertEquals(9, diags[0].line)
        assertEquals("cannot find symbol", diags[0].message)
    }

    @Test
    fun `falls back to generic line-number extraction`() {
        val output = "Some tool failed: error near line 42"

        val diags = DiagnosticsManager.parseDiagnostics(output, "whatever.xyz")

        assertEquals(1, diags.size)
        assertEquals(42, diags[0].line)
    }

    @Test
    fun `deduplicates diagnostics on the same line`() {
        val output = """
            main.c:3:5: error: first
            main.c:3:9: error: second
        """.trimIndent()

        val diags = DiagnosticsManager.parseDiagnostics(output, "main.c")

        assertEquals(1, diags.size)
    }

    @Test
    fun `returns empty list for clean output`() {
        val diags = DiagnosticsManager.parseDiagnostics("All good, 0 problems", "main.c")
        assertTrue(diags.isEmpty())
    }
}
