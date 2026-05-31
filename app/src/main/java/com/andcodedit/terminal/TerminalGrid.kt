package com.andcodedit.terminal

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TerminalGrid — the scrollback model behind a terminal tab.
 *
 * It accumulates raw PTY/shell output, strips the most common ANSI/VT control
 * sequences so the Compose [androidx.compose.foundation.text.BasicText] surface
 * stays readable, and exposes the rolling buffer as a [StateFlow] of plain text.
 *
 * A full VT100 cell grid lives in the Rust core; this Kotlin model is the
 * lightweight view-side buffer the UI renders and auto-scrolls.
 */
@Stable
class TerminalGrid(
    val initialRows: Int = 30,
    val initialCols: Int = 100,
    private val maxScrollbackLines: Int = 5_000
) {
    var rows: Int = initialRows
        private set
    var cols: Int = initialCols
        private set

    private val buffer = StringBuilder()

    private val _outputFlow = MutableStateFlow("")
    val outputFlow: StateFlow<String> = _outputFlow.asStateFlow()

    /** Append a chunk of raw output coming from the shell/PTY. */
    @Synchronized
    fun appendOutput(data: ByteArray) {
        appendOutput(String(data, Charsets.UTF_8))
    }

    @Synchronized
    fun appendOutput(text: String) {
        buffer.append(stripAnsi(text))
        trimScrollback()
        _outputFlow.value = buffer.toString()
    }

    @Synchronized
    fun resize(newRows: Int, newCols: Int) {
        rows = newRows
        cols = newCols
    }

    @Synchronized
    fun clear() {
        buffer.setLength(0)
        _outputFlow.value = ""
    }

    private fun trimScrollback() {
        // Cap memory by dropping the oldest lines once we exceed the limit.
        var newlineCount = 0
        for (i in buffer.indices.reversed()) {
            if (buffer[i] == '\n') newlineCount++
            if (newlineCount > maxScrollbackLines) {
                buffer.delete(0, i + 1)
                break
            }
        }
    }

    private companion object {
        // CSI / OSC / single-char escape sequences. Good enough for shell prompts,
        // colour codes and cursor moves that would otherwise show as garbage.
        private val ANSI_REGEX =
            Regex("\\[[0-9;?]*[ -/]*[@-~]|\\][^]*|[@-Z\\\\-_]|[\r\b]")

        fun stripAnsi(input: String): String = ANSI_REGEX.replace(input, "")
    }
}
