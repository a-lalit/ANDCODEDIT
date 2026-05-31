package com.andcodedit.lang

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream

/**
 * Events streamed back while a snippet is executing.
 */
sealed interface RunEvent {
    /** The fully-substituted shell command line that is about to run. */
    data class Started(val cmd: String) : RunEvent

    /** A single line read from the process's standard output. */
    data class Stdout(val line: String) : RunEvent

    /** A single line read from the process's standard error. */
    data class Stderr(val line: String) : RunEvent

    /** Terminal event: the process exited with [code] after [ms] milliseconds. */
    data class Exit(val code: Int, val ms: Long) : RunEvent
}

/**
 * Executes code snippets for a [Language] on the on-device shell.
 *
 * The source is written to `workDir/Main.<ext>`, the language's compile step
 * (if any) is run first, then the run step. stdout and stderr are streamed
 * line-by-line as [RunEvent]s. Everything happens on background dispatchers and
 * is fully cancellable: cancelling the collecting coroutine destroys the
 * underlying process.
 */
class CodeRunner {

    /**
     * Runs [code] for the given [language] inside [workDir].
     *
     * @param scope kept for API symmetry / future use; the returned [Flow] runs
     *              on its own collector's coroutine and does not depend on it.
     */
    fun run(
        language: Language,
        code: String,
        workDir: File,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope
    ): Flow<RunEvent> = channelFlow {
        val baseName = "Main"
        val sourceFile = File(workDir, "$baseName.${language.fileExtension}")
        val outFile = File(workDir, baseName)

        // Prepare the working directory and write the source.
        if (!workDir.exists()) workDir.mkdirs()
        sourceFile.writeText(code)

        fun substitute(template: String): String = template
            .replace("{file}", shellQuote(sourceFile.absolutePath))
            .replace("{dir}", shellQuote(workDir.absolutePath))
            .replace("{name}", baseName)
            .replace("{out}", shellQuote(outFile.absolutePath))

        val started = System.currentTimeMillis()

        // ---- Optional compile step ----
        language.compileTemplate?.let { tmpl ->
            val compileCmd = substitute(tmpl)
            send(RunEvent.Started(compileCmd))
            val compileExit = execStreaming(compileCmd, workDir)
            // Drain compiler output into the flow.
            compileExit.stdout.forEach { send(RunEvent.Stdout(it)) }
            compileExit.stderr.forEach { send(RunEvent.Stderr(it)) }
            if (compileExit.code != 0) {
                send(RunEvent.Exit(compileExit.code, System.currentTimeMillis() - started))
                return@channelFlow
            }
        }

        // ---- Run step (streamed live) ----
        val runCmd = substitute(language.runTemplate)
        send(RunEvent.Started(runCmd))

        val process = newShellProcess(runCmd, workDir)
        if (process == null) {
            send(RunEvent.Stderr("Failed to start shell process"))
            send(RunEvent.Exit(-1, System.currentTimeMillis() - started))
            return@channelFlow
        }

        // Stream stdout and stderr concurrently, line by line.
        val outJob = launch(Dispatchers.IO) {
            streamLines(process.inputStream) { line ->
                if (isActive) trySend(RunEvent.Stdout(line))
            }
        }
        val errJob = launch(Dispatchers.IO) {
            streamLines(process.errorStream) { line ->
                if (isActive) trySend(RunEvent.Stderr(line))
            }
        }

        // Tear the process down if the collector is cancelled.
        invokeOnClose { process.destroy() }

        val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
        outJob.join()
        errJob.join()

        send(RunEvent.Exit(exitCode, System.currentTimeMillis() - started))
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------

    private data class BufferedExec(
        val code: Int,
        val stdout: List<String>,
        val stderr: List<String>
    )

    /** Runs a command to completion, buffering its output (used for compile steps). */
    private fun execStreaming(cmd: String, workDir: File): BufferedExec {
        val process = newShellProcess(cmd, workDir)
            ?: return BufferedExec(-1, emptyList(), listOf("Failed to start shell process"))
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val outThread = Thread { streamLines(process.inputStream) { synchronized(out) { out.add(it) } } }
        val errThread = Thread { streamLines(process.errorStream) { synchronized(err) { err.add(it) } } }
        outThread.start(); errThread.start()
        val code = process.waitFor()
        outThread.join(); errThread.join()
        return BufferedExec(code, out, err)
    }

    private fun streamLines(stream: InputStream, onLine: (String) -> Unit) {
        try {
            stream.bufferedReader().use { reader: BufferedReader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            }
        } catch (_: Exception) {
            // Stream closed (e.g. process destroyed on cancellation) — stop quietly.
        }
    }

    /** Spawns `<shell> -c <cmd>` with stderr kept separate, or null on failure. */
    private fun newShellProcess(cmd: String, workDir: File): Process? = try {
        ProcessBuilder(resolveShell(), "-c", cmd)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()
    } catch (_: Exception) {
        null
    }

    /** Resolves a usable shell, defensively falling back to bare "sh". */
    private fun resolveShell(): String {
        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/sh",
            "/system/bin/sh",
            "/bin/sh"
        )
        for (c in candidates) {
            if (File(c).exists()) return c
        }
        return "sh"
    }

    /** Minimal single-quote shell escaping so paths with spaces are safe. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
