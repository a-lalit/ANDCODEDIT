package com.andcodedit.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
 * Terminal events emitted while a toolchain is being provisioned.
 */
sealed interface InstallEvent {
    /** A single line read from the package manager's output. */
    data class Log(val line: String) : InstallEvent

    /** Best-effort install progress in the range 0..100, parsed from output. */
    data class Progress(val pct: Int) : InstallEvent

    /** Terminal event: the install finished, [success] tells whether it worked. */
    data class Done(val success: Boolean, val message: String) : InstallEvent
}

/**
 * BootstrapManager — the on-device **toolchain provisioning layer**
 * ("Termux-style bootstrap").
 *
 * ## Why this exists
 *
 * A single APK cannot bundle the ~30 language toolchains ANDCODEDIT supports:
 * together they are multiple gigabytes, and Google Play caps delivery at roughly
 * 200 MB. Native compilers also cannot legally/practically be shipped inside a
 * stock Android APK. So, exactly like Termux and Acode, ANDCODEDIT **provisions
 * toolchains at runtime** rather than bundling them: it installs a base Linux
 * userland and then uses a package manager to fetch compilers on demand into
 * app-private storage.
 *
 * ## The two backends
 *
 * This class abstracts over *where* packages get installed:
 *
 *  - [Backend.TERMUX] — if the Termux app and its `com.termux` files prefix are
 *    present, we target Termux directly. Packages are installed with
 *    `pkg install -y <pkg>` running under Termux's `usr` prefix. (In a fully
 *    wired build this would be dispatched through Termux's `RUN_COMMAND`
 *    intent / shared files; here we shell into the prefix.)
 *  - [Backend.LOCAL_PREFIX] — otherwise we target a self-contained prefix under
 *    the app's own files dir ([prefixDir] = `$filesDir/usr`), where a bootstrap
 *    archive would be unpacked and `apt-get`/`dpkg` run with `PREFIX` pointing
 *    at it. Until that archive is unpacked, install attempts explain what is
 *    required instead of failing opaquely.
 *  - [Backend.NONE] — neither is available; operations report a helpful message.
 *
 * Everything here uses real `ProcessBuilder` + `File` I/O, runs on the IO
 * dispatcher, and is fully cancellable.
 */
class BootstrapManager(private val context: Context) {

    /**
     * The self-contained install prefix used by the [Backend.LOCAL_PREFIX]
     * backend: `$filesDir/usr`. A bootstrap archive would be unpacked here so
     * that `usr/bin`, `usr/lib`, etc. mirror a minimal Linux userland.
     */
    val prefixDir: File = File(context.filesDir, "usr")

    /** Absolute path of the Termux `usr` prefix on a standard Termux install. */
    private val termuxPrefix = File("/data/data/com.termux/files/usr")

    /** Which provisioning target a package install should be routed to. */
    enum class Backend { TERMUX, LOCAL_PREFIX, NONE }

    /**
     * True if the Termux environment is usable from this app: either the Termux
     * package is installed, or its `usr/bin` prefix directory exists on disk.
     */
    fun isTermuxInstalled(): Boolean {
        val packagePresent = try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: Exception) {
            false
        }
        return packagePresent || File(termuxPrefix, "bin").let { it.exists() && it.isDirectory }
    }

    /**
     * Selects the backend to provision into:
     *  - [Backend.TERMUX] when Termux is installed,
     *  - [Backend.LOCAL_PREFIX] when the local prefix already exists or can be created,
     *  - [Backend.NONE] only if neither path is viable.
     */
    fun detectBackend(): Backend = when {
        isTermuxInstalled() -> Backend.TERMUX
        prefixDir.exists() || canUsePrefix() -> Backend.LOCAL_PREFIX
        else -> Backend.NONE
    }

    /**
     * True when a usable toolchain environment is already provisioned: Termux is
     * present, or the local prefix has been bootstrapped (a `bin/sh` exists, or
     * the [bootstrapMarker] file is present).
     */
    fun isBootstrapped(): Boolean {
        if (isTermuxInstalled()) return true
        val localShell = File(prefixDir, "bin/sh")
        return localShell.exists() || bootstrapMarker.exists()
    }

    /**
     * Provisions [toolchain] by running the appropriate package manager and
     * streaming its output as [InstallEvent]s.
     *
     * Behaviour by backend:
     *  - [Backend.TERMUX]: runs `pkg install -y <pkg>` via the Termux shell.
     *  - [Backend.LOCAL_PREFIX]: if the prefix is bootstrapped, runs
     *    `apt-get install -y <pkg>` with `PREFIX` pointing at [prefixDir];
     *    otherwise emits an explanatory [InstallEvent.Log] describing that the
     *    bootstrap archive must be unpacked first, then a failing [InstallEvent.Done].
     *  - [Backend.NONE]: emits a single helpful failing [InstallEvent.Done].
     *
     * The returned [Flow] runs on [Dispatchers.IO] and is cancellable: cancelling
     * the collector destroys the underlying process.
     */
    fun installPackage(toolchain: Toolchain): Flow<InstallEvent> = channelFlow {
        val backend = detectBackend()

        when (backend) {
            Backend.NONE -> {
                send(
                    InstallEvent.Done(
                        success = false,
                        message = "No toolchain backend available. Install the Termux app " +
                            "(F-Droid) or run the local bootstrap to create $prefixDir."
                    )
                )
                return@channelFlow
            }

            Backend.LOCAL_PREFIX -> {
                if (!isBootstrapped()) {
                    send(InstallEvent.Log("Local prefix not bootstrapped yet."))
                    send(InstallEvent.Log("Expected a userland under: ${prefixDir.absolutePath}"))
                    send(InstallEvent.Log("A bootstrap archive (base env: sh, apt, dpkg) must be"))
                    send(InstallEvent.Log("downloaded and unpacked into the prefix before packages"))
                    send(InstallEvent.Log("can be installed. Install Termux for a turnkey environment."))
                    send(
                        InstallEvent.Done(
                            success = false,
                            message = "Bootstrap required before installing ${toolchain.pkgName}."
                        )
                    )
                    return@channelFlow
                }
            }

            Backend.TERMUX -> Unit // ready to install
        }

        val command = buildInstallCommand(backend, toolchain)
        val shell = resolveShell(backend)
        send(InstallEvent.Log("$ $command"))

        val process = try {
            ProcessBuilder(shell, "-c", command)
                .directory(workingDir(backend))
                .apply {
                    if (backend == Backend.LOCAL_PREFIX) {
                        environment()["PREFIX"] = prefixDir.absolutePath
                        environment()["PATH"] =
                            File(prefixDir, "bin").absolutePath + ":" + (environment()["PATH"] ?: "")
                    }
                }
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            send(InstallEvent.Done(false, "Failed to start installer: ${e.message}"))
            return@channelFlow
        }

        // Tear the process down if the collector is cancelled.
        invokeOnClose { runCatching { process.destroy() } }

        val reader = launch(Dispatchers.IO) {
            streamLines(process.inputStream) { line ->
                if (!isActive) return@streamLines
                trySend(InstallEvent.Log(line))
                parseProgress(line)?.let { trySend(InstallEvent.Progress(it)) }
            }
        }

        val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
        reader.join()

        if (exitCode == 0) {
            send(InstallEvent.Progress(100))
            send(InstallEvent.Done(true, "${toolchain.displayName} installed."))
        } else {
            send(
                InstallEvent.Done(
                    false,
                    "Installer exited with code $exitCode while installing ${toolchain.pkgName}."
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Snapshot of the provisioning environment for display in the UI:
     * backend, prefix dir, whether Termux is installed, and bootstrap state.
     */
    fun environmentInfo(): Map<String, String> = mapOf(
        "backend" to detectBackend().name,
        "prefixDir" to prefixDir.absolutePath,
        "termuxInstalled" to isTermuxInstalled().toString(),
        "bootstrapped" to isBootstrapped().toString()
    )

    // ---------------------------------------------------------------------

    /** Marker file written once the local prefix has been bootstrapped. */
    private val bootstrapMarker: File get() = File(prefixDir, ".bootstrapped")

    /** Builds the package-manager command line for [backend]. */
    private fun buildInstallCommand(backend: Backend, toolchain: Toolchain): String =
        when (backend) {
            Backend.TERMUX ->
                "pkg install -y ${shellQuote(toolchain.pkgName)}"
            Backend.LOCAL_PREFIX ->
                "apt-get install -y ${shellQuote(toolchain.pkgName)}"
            Backend.NONE ->
                "true" // unreachable: handled before this is called
        }

    /** Resolves the shell to run the install command under for [backend]. */
    private fun resolveShell(backend: Backend): String {
        val candidates = when (backend) {
            Backend.TERMUX -> listOf(
                "/data/data/com.termux/files/usr/bin/bash",
                "/data/data/com.termux/files/usr/bin/sh"
            )
            else -> listOf(
                File(prefixDir, "bin/bash").absolutePath,
                File(prefixDir, "bin/sh").absolutePath,
                "/system/bin/sh",
                "/bin/sh"
            )
        }
        for (c in candidates) if (File(c).exists()) return c
        return "sh"
    }

    /** Working directory for the installer process. */
    private fun workingDir(backend: Backend): File = when (backend) {
        Backend.TERMUX -> termuxPrefix.takeIf { it.exists() } ?: context.filesDir
        else -> prefixDir.takeIf { it.exists() } ?: context.filesDir
    }

    /** True if we can create (or already have) the local prefix directory. */
    private fun canUsePrefix(): Boolean = try {
        prefixDir.exists() || prefixDir.mkdirs()
    } catch (_: Exception) {
        false
    }

    /**
     * Parses a best-effort percentage from a package-manager output [line].
     * Recognises trailing `NN%` tokens (apt/pkg progress) and clamps to 0..100.
     */
    private fun parseProgress(line: String): Int? {
        val match = Regex("(\\d{1,3})\\s*%").find(line) ?: return null
        return match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
    }

    /** Reads [stream] line-by-line, invoking [onLine]; swallows close exceptions. */
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

    /** Minimal single-quote shell escaping so package names are passed safely. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
