package com.andcodedit.language

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.regex.Pattern

/**
 * DiagnosticsManager
 * Parses common compiler/linter output and applies error/warning highlighting
 * directly in the Sora CodeEditor.
 *
 * Supports:
 * - GCC / Clang style (file:line:col: error: message)
 * - Python (File "...", line X)
 * - TypeScript / tsc
 * - Java / javac
 * - General fallback
 */
object DiagnosticsManager {

    data class ParsedDiagnostic(
        /** 1-based line number as emitted by compilers. */
        val line: Int,
        /** 1-based column. */
        val column: Int,
        val length: Int = 1,
        val message: String,
        val severity: Short = DiagnosticRegion.SEVERITY_ERROR
    )

    /**
     * Apply diagnostics to a Sora [CodeEditor].
     *
     * Sora's [DiagnosticRegion] is character-offset based, so we convert the
     * compiler's 1-based (line, column) into absolute indices via the editor's
     * [io.github.rosemoe.sora.text.Content]. Out-of-range entries are skipped.
     */
    fun applyDiagnostics(editor: CodeEditor, diagnostics: List<ParsedDiagnostic>) {
        val content = editor.text
        val lineCount = content.lineCount
        val container = DiagnosticsContainer()

        diagnostics.forEach { diag ->
            val lineIdx = (diag.line - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val colCount = content.getColumnCount(lineIdx)
            val colIdx = (diag.column - 1).coerceIn(0, colCount)
            val start = try {
                content.getCharIndex(lineIdx, colIdx)
            } catch (e: Exception) {
                return@forEach
            }
            val total = content.length
            val end = (start + diag.length).coerceAtMost(total)
            container.addDiagnostic(
                DiagnosticRegion(
                    start,
                    end.coerceAtLeast(start + 1).coerceAtMost(total),
                    diag.severity,
                    0L,
                    DiagnosticDetail(diag.message, null, emptyList(), null)
                )
            )
        }
        editor.setDiagnostics(container)
    }

    /**
     * Parse common lint/compiler output into diagnostics.
     * This is a practical multi-format parser.
     */
    fun parseDiagnostics(output: String, fileName: String = ""): List<ParsedDiagnostic> {
        val diagnostics = mutableListOf<ParsedDiagnostic>()

        // GCC / Clang style: file:line:col: error: message
        val gccPattern = Pattern.compile(
            """(?:${Pattern.quote(fileName)}:)?(\d+):(\d+):\s*(error|warning|note):\s*(.+)""",
            Pattern.MULTILINE
        )

        // Python style: File "...", line 42
        val pythonPattern = Pattern.compile(
            """File ".*?", line (\d+)""",
            Pattern.MULTILINE
        )

        // TypeScript / tsc: file(line,col): error TSxxxx: message
        val tsPattern = Pattern.compile(
            """(?:${Pattern.quote(fileName)})?\((\d+),(\d+)\):\s*(error|warning)\s+TS\d+:\s*(.+)""",
            Pattern.MULTILINE
        )

        // Java / javac style
        val javaPattern = Pattern.compile(
            """${Pattern.quote(fileName)}:(\d+):\s*(error|warning):\s*(.+)""",
            Pattern.MULTILINE
        )

        // Try GCC style first (most common for C/C++/general)
        var matcher = gccPattern.matcher(output)
        while (matcher.find()) {
            val line = matcher.group(1)?.toIntOrNull() ?: 1
            val col = matcher.group(2)?.toIntOrNull() ?: 1
            val type = matcher.group(3)
            val message = matcher.group(4) ?: ""
            val severity = if (type == "error") DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING

            diagnostics.add(ParsedDiagnostic(line, col, 5, message, severity))
        }

        // Python
        if (diagnostics.isEmpty()) {
            matcher = pythonPattern.matcher(output)
            while (matcher.find()) {
                val line = matcher.group(1)?.toIntOrNull() ?: 1
                diagnostics.add(
                    ParsedDiagnostic(
                        line = line,
                        column = 1,
                        length = 10,
                        message = "Python syntax/runtime error (see terminal for details)",
                        severity = DiagnosticRegion.SEVERITY_ERROR
                    )
                )
            }
        }

        // TypeScript
        if (diagnostics.isEmpty()) {
            matcher = tsPattern.matcher(output)
            while (matcher.find()) {
                val line = matcher.group(1)?.toIntOrNull() ?: 1
                val col = matcher.group(2)?.toIntOrNull() ?: 1
                val message = matcher.group(4) ?: ""
                diagnostics.add(ParsedDiagnostic(line, col, 8, message, DiagnosticRegion.SEVERITY_ERROR))
            }
        }

        // Java
        if (diagnostics.isEmpty()) {
            matcher = javaPattern.matcher(output)
            while (matcher.find()) {
                val line = matcher.group(1)?.toIntOrNull() ?: 1
                val message = matcher.group(3) ?: ""
                diagnostics.add(ParsedDiagnostic(line, 1, 10, message, DiagnosticRegion.SEVERITY_ERROR))
            }
        }

        // Fallback: try to extract any "line X" mentions
        if (diagnostics.isEmpty()) {
            val fallbackPattern = Pattern.compile("""line\s*(\d+)""", Pattern.CASE_INSENSITIVE)
            matcher = fallbackPattern.matcher(output)
            while (matcher.find()) {
                val line = matcher.group(1)?.toIntOrNull() ?: continue
                diagnostics.add(
                    ParsedDiagnostic(
                        line = line,
                        column = 1,
                        length = 5,
                        message = output.lines().firstOrNull { it.contains("error", true) } ?: "Error detected",
                        severity = DiagnosticRegion.SEVERITY_ERROR
                    )
                )
            }
        }

        return diagnostics.distinctBy { it.line } // Avoid duplicates
    }

    /**
     * Convenience method: Run lint + parse output + apply to editor
     */
    fun lintAndHighlight(
        editor: CodeEditor,
        lintOutput: String,
        fileName: String
    ) {
        val diagnostics = parseDiagnostics(lintOutput, fileName)
        applyDiagnostics(editor, diagnostics)
    }
}