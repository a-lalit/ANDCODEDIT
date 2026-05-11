package com.andcodedit.terminal

import androidx.compose.runtime.Stable
import uniffi.andcodedit_terminal.*

/**
 * Full production Kotlin wrapper around the real Rust PTY TerminalSession via UniFFI.
 *
 * This replaces the demo implementation.
 * 
 * Prerequisites for full build:
 * 1. Build the Rust library for Android targets (arm64-v8a, armeabi-v7a, x86, x86_64)
 *    using cargo-ndk or ndk-build.
 * 2. Place the resulting libandcodedit_terminal.so in src/main/jniLibs/<arch>/
 * 3. Run uniffi-bindgen generate (or use the build.rs which does it) to generate
 *    the Kotlin bindings in the correct package.
 *
 * Once wired, typing in the terminal will execute real commands on the Android
 * Linux shell through a proper PTY.
 */
@Stable
class TerminalSession private constructor(
    private val nativeSession: uniffi.andcodedit_terminal.TerminalSession,
    private val outputCallback: (ByteArray) -> Unit
) {
    companion object {
        init {
            // Load the native library produced by Rust + UniFFI
            System.loadLibrary("andcodedit_terminal")
        }

        fun create(
            rows: Int = 30,
            cols: Int = 100,
            shell: String = "/system/bin/sh",
            onOutput: (ByteArray) -> Unit
        ): TerminalSession {
            val config = TerminalConfig(
                rows = rows.toUShort(),
                cols = cols.toUShort(),
                shell = shell
            )

            // UniFFI callback adapter: Rust calls this, we forward to Kotlin lambda
            val callback = object : TerminalOutputCallback {
                override fun onOutput(data: List<UByte>) {
                    // Convert List<UByte> from UniFFI to ByteArray
                    val byteArray = ByteArray(data.size) { i -> data[i].toByte() }
                    onOutput(byteArray)
                }
            }

            val native = try {
                createTerminalSession(config, callback)
            } catch (e: TerminalError) {
                throw RuntimeException("Failed to create real PTY: ${e.message}", e)
            }

            return TerminalSession(native, onOutput)
        }
    }

    fun writeInput(data: ByteArray) {
        try {
            // Convert ByteArray to List<UByte> for UniFFI
            val uBytes = data.map { it.toUByte() }
            nativeSession.writeInput(uBytes)
        } catch (e: TerminalError) {
            // Log or handle gracefully in production
            println("PTY writeInput error: ${e.message}")
        }
    }

    fun resize(rows: Int, cols: Int) {
        try {
            nativeSession.resize(rows.toUShort(), cols.toUShort())
        } catch (e: TerminalError) {
            println("PTY resize error: ${e.message}")
        }
    }

    // Optional: expose close if needed in future
    // fun close() { ... }
}

/**
 * Factory that now creates **real** PTY sessions.
 * The demo behavior is completely removed.
 */
object TerminalSessionFactory {
    fun create(
        rows: Int = 30,
        cols: Int = 100,
        shell: String = "/system/bin/sh",
        onOutput: (ByteArray) -> Unit
    ): TerminalSession {
        return TerminalSession.create(rows, cols, shell, onOutput)
    }
}