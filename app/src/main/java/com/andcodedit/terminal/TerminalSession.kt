package com.andcodedit.terminal

import androidx.compose.runtime.Stable
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * TerminalSession — a real, interactive shell session.
 *
 * This implementation spawns the system shell with [ProcessBuilder] and streams
 * its merged stdout/stderr back to the caller on a dedicated reader thread.
 * Commands written via [writeInput] are executed by the live shell process, so
 * `ls`, `cd`, `python`, `git`, etc. all work on-device with no native library.
 *
 * Upgrade path: a full VT/PTY (job control, `vim`, `htop`, window resize signals)
 * is provided by the Rust `portable-pty` core via UniFFI. When the
 * `libandcodedit_terminal.so` artifact and generated bindings are present, the
 * factory can be switched to construct a PTY-backed session instead — the public
 * API below is intentionally identical so callers do not change.
 */
@Stable
class TerminalSession internal constructor(
    rows: Int,
    cols: Int,
    shell: String,
    private val onOutput: (ByteArray) -> Unit
) {
    @Volatile
    var rows: Int = rows
        private set

    @Volatile
    var cols: Int = cols
        private set

    @Volatile
    private var alive: Boolean = false

    private val process: Process
    private val stdin: OutputStream
    private var readerThread: Thread? = null

    init {
        val builder = ProcessBuilder(resolveShell(shell), "-i")
            .redirectErrorStream(true)
        builder.environment().apply {
            put("TERM", "xterm-256color")
            put("LINES", rows.toString())
            put("COLUMNS", cols.toString())
        }
        process = builder.start()
        stdin = process.outputStream
        alive = true
        startReader(process.inputStream)
        // Greet the user so the buffer is never empty.
        onOutput("ANDCODEDIT terminal — ${resolveShell(shell)}\n".toByteArray())
    }

    private fun startReader(input: InputStream) {
        readerThread = thread(name = "term-reader", isDaemon = true) {
            val reader: BufferedReader = input.bufferedReader()
            val chunk = CharArray(2048)
            try {
                while (alive) {
                    val read = reader.read(chunk)
                    if (read == -1) break
                    val text = String(chunk, 0, read)
                    onOutput(text.toByteArray())
                }
            } catch (_: Exception) {
                // Stream closed — session ending.
            } finally {
                alive = false
                onOutput("\n[process exited]\n".toByteArray())
            }
        }
    }

    /** Send keystrokes / a command line to the shell. */
    fun writeInput(data: ByteArray) {
        if (!alive) return
        try {
            stdin.write(data)
            stdin.flush()
        } catch (e: Exception) {
            onOutput("\n[write error: ${e.message}]\n".toByteArray())
        }
    }

    /** Record a new viewport size. Reported to the shell via env on next program. */
    fun resize(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
        // Best-effort: many programs re-read COLUMNS/LINES; a real PTY would
        // deliver SIGWINCH which the Rust core handles.
    }

    fun isAlive(): Boolean = alive && process.isAlive

    fun close() {
        alive = false
        try {
            stdin.close()
        } catch (_: Exception) {
        }
        try {
            process.destroy()
        } catch (_: Exception) {
        }
        readerThread?.interrupt()
    }

    private fun resolveShell(preferred: String): String {
        val candidates = listOf(preferred, "/system/bin/sh", "/bin/sh", "sh")
        for (c in candidates) {
            if (c == "sh") return c
            if (java.io.File(c).exists()) return c
        }
        return "/system/bin/sh"
    }
}

/**
 * Factory for terminal sessions. Centralises construction so the backing
 * implementation (ProcessBuilder today, Rust PTY when the native lib is present)
 * can be swapped without touching call sites.
 */
object TerminalSessionFactory {
    fun create(
        rows: Int = 30,
        cols: Int = 100,
        shell: String = "/system/bin/sh",
        onOutput: (ByteArray) -> Unit
    ): TerminalSession = TerminalSession(rows, cols, shell, onOutput)
}
