package com.andcodedit.lang

/**
 * RuntimeManager — checks which language toolchains are actually available on
 * the device and tells the user how to install the ones that are missing.
 *
 * ## Termux / proot bootstrap model
 *
 * ANDCODEDIT does **not** bundle compilers or interpreters inside the APK. Doing
 * so would be impractical (size, licensing) and is disallowed for native
 * toolchains on stock Android. Instead, the actual language runtimes are
 * provisioned **on-device** by a Linux userland:
 *
 *  - A Termux installation (or a proot-distro/proot guest such as Ubuntu/Alpine)
 *    supplies a real `PATH` of binaries: `python`, `node`, `clang`, `go`, etc.
 *  - The app's [com.andcodedit.terminal.TerminalSession] shells into that
 *    userland, so anything installed via `pkg install ...` (Termux) or
 *    `apt install ...` (proot guest) becomes runnable.
 *  - Availability is therefore a **runtime** property: it is discovered by
 *    asking the shell `command -v <binary>`, never assumed at build time.
 *
 * [installCommand] returns the Termux `pkg install` line that provisions a
 * language; [isAvailable] / [missingBinaries] report current state.
 */
object RuntimeManager {

    /**
     * Builds an availability map over every required binary of [lang]:
     * binary name -> resolved path (true) or absent (false).
     */
    fun availability(lang: Language): Map<String, Boolean> =
        lang.requiredBinaries.associateWith { commandExists(it) }

    /** True when every required binary of [lang] resolves on the shell PATH. */
    fun isAvailable(lang: Language): Boolean =
        lang.requiredBinaries.all { commandExists(it) }

    /** The subset of [lang]'s required binaries that are not currently resolvable. */
    fun missingBinaries(lang: Language): List<String> =
        lang.requiredBinaries.filterNot { commandExists(it) }

    /** The Termux `pkg install ...` line that provisions [lang]'s toolchain. */
    fun installCommand(lang: Language): String = lang.installHint

    // ---------------------------------------------------------------------

    /** Returns true if `command -v <binary>` succeeds on the resolved shell. */
    private fun commandExists(binary: String): Boolean = try {
        val process = ShellEnvironment.apply(
            ProcessBuilder(ShellEnvironment.resolveShell(), "-c", "command -v ${shellQuote(binary)}")
                .redirectErrorStream(true)
        ).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val code = process.waitFor()
        code == 0 && output.isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
