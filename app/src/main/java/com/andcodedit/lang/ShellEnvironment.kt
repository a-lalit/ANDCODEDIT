package com.andcodedit.lang

import java.io.File

/**
 * ShellEnvironment — the single source of truth for *how* the app shells out to
 * run user code and probe toolchains.
 *
 * The toolchains live in an on-device Linux userland (Termux, or a proot guest),
 * not in the APK. To actually find `python`, `clang`, `node`, … the spawned
 * shell needs the right `PATH` and a few environment variables (HOME, PREFIX,
 * TMPDIR, LD_LIBRARY_PATH). This object resolves a usable shell and assembles
 * that environment so [CodeRunner] and [RuntimeManager] behave identically.
 */
object ShellEnvironment {

    /** Termux's default install prefix on a non-rooted device. */
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    /** Candidate shells, most-capable first. */
    private val shellCandidates = listOf(
        "$TERMUX_PREFIX/bin/bash",
        "$TERMUX_PREFIX/bin/sh",
        "/system/bin/sh",
        "/bin/bash",
        "/bin/sh"
    )

    /** Extra bin directories prepended to PATH when present. */
    private val extraBinDirs = listOf(
        "$TERMUX_PREFIX/bin",
        "$TERMUX_PREFIX/bin/applets",
        "/system/bin",
        "/system/xbin"
    )

    /** Resolve a usable shell, falling back to bare `sh` on PATH. */
    fun resolveShell(): String {
        for (c in shellCandidates) if (File(c).exists()) return c
        return "sh"
    }

    /** True when a Termux userland prefix is present on this device. */
    fun hasTermuxPrefix(): Boolean = File("$TERMUX_PREFIX/bin").isDirectory

    /**
     * The PATH the spawned shell should use: existing extra bin dirs first, then
     * whatever the inherited environment already had, so both Termux binaries
     * and system tools resolve.
     */
    fun buildPath(inherited: String?): String {
        val present = extraBinDirs.filter { File(it).exists() }
        val tail = inherited?.takeIf { it.isNotBlank() }
        return (present + listOfNotNull(tail)).joinToString(File.pathSeparator)
    }

    /**
     * Apply the toolchain-aware environment to a [ProcessBuilder]. Honours an
     * existing Termux prefix; otherwise leaves system defaults intact while still
     * fixing up PATH. [tmpDir] (e.g. the app cache dir) is used for TMPDIR.
     */
    fun apply(pb: ProcessBuilder, tmpDir: File? = null): ProcessBuilder {
        val env = pb.environment()
        env["PATH"] = buildPath(env["PATH"])
        env["TERM"] = env["TERM"] ?: "xterm-256color"
        if (hasTermuxPrefix()) {
            env["PREFIX"] = TERMUX_PREFIX
            env.putIfAbsent("HOME", TERMUX_HOME)
            env["LD_LIBRARY_PATH"] = listOf("$TERMUX_PREFIX/lib", env["LD_LIBRARY_PATH"])
                .filterNotNull().filter { it.isNotBlank() }
                .joinToString(File.pathSeparator)
        }
        tmpDir?.let { env["TMPDIR"] = it.absolutePath }
        return pb
    }
}
