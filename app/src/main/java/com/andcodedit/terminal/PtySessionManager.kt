/*
 * PtySessionManager.kt
 * Package: com.andcodedit.terminal
 *
 * PURPOSE: Manages the lifecycle of all active PTY sessions.
 * One PtySessionManager exists per app process, hosted inside
 * TerminalForegroundService to survive Android lifecycle events.
 *
 * RESPONSIBILITIES:
 *   - Create/destroy PTY sessions (backed by PtyBridge)
 *   - Start per-session reader coroutines on Dispatchers.IO
 *   - Feed PTY output bytes through VtParser -> TerminalScreenModel
 *   - Emit TerminalEvents via SharedFlow for ViewModel consumption
 *   - Handle resize requests (triggered by desktop mode changes)
 *   - Persist session metadata via Room (for restore after process death)
 *
 * LIFECYCLE:
 *   - Created by TerminalForegroundService
 *   - Accessed by TerminalViewModel via ServiceConnection binder
 *   - Destroyed in Service.onDestroy() -> all sessions killed
 *
 * THREAD MODEL:
 *   - Reader coroutines: Dispatchers.IO (blocking PTY reads)
 *   - Write operations: Dispatchers.IO (non-blocking usually, but safe)
 *   - Screen model access: synchronized (see TerminalScreenModel.takeSnapshot)
 *   - Event emission: Dispatchers.Default
 *
 * AGENT: AGENT-3 (Session Manager)
 * SELF-CHECK: PtySessionManagerTest.kt validates session creation,
 *             write/read roundtrip, and resize propagation.
 */
package com.andcodedit.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PtySessionManager"

/** How often (in ms) the reader loop emits a screen snapshot update */
private const val SNAPSHOT_EMIT_INTERVAL_MS = 16L  // ~60fps max update rate

/** Buffer size for the PTY reader loop */
private const val READ_BUFFER_SIZE = 8192

/**
 * Internal state for a single active PTY session.
 * Not exposed outside this package.
 */
private data class SessionState(
    val tab: TerminalTab,
    val handle: PtyHandle,
    val screenModel: TerminalScreenModel,
    val parser: VtParser,
    val readerJob: Job,
    var dimensions: TerminalDimensions
)

/**
 * Manages all active terminal sessions for the current app process.
 *
 * @param ptyBridge The PTY backend to use (Mock or Native)
 * @param serviceScope CoroutineScope tied to TerminalForegroundService lifetime
 */
class PtySessionManager(
    private val ptyBridge: PtyBridge,
    private val serviceScope: CoroutineScope
) {

    // -----------------------------------------------------------------------
    // SESSION REGISTRY
    // -----------------------------------------------------------------------

    /** All active sessions, keyed by session ID (UUID string) */
    private val sessions = ConcurrentHashMap<String, SessionState>()

    // -----------------------------------------------------------------------
    // EVENT STREAM
    // -----------------------------------------------------------------------

    /**
     * Hot shared flow of terminal events.
     * ViewModels collect from this to receive screen updates.
     * extraBufferCapacity=64 prevents slow consumers from blocking the reader.
     */
    private val _events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TerminalEvent> = _events.asSharedFlow()

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Create and start a new terminal session.
     *
     * @param rows Initial terminal height
     * @param cols Initial terminal width
     * @param name Display name for the tab
     * @param shellPath Path to the shell binary
     * @return The ID of the newly created session
     */
    suspend fun createSession(
        rows: Int = 24,
        cols: Int = 80,
        name: String = "Terminal",
        shellPath: String = "/system/bin/sh"
    ): String {
        val sessionId = UUID.randomUUID().toString()
        Log.i(TAG, "Creating session $sessionId ($name) ${rows}x${cols}")

        val tab = TerminalTab(
            id = sessionId,
            name = name,
            shellPath = shellPath,
            state = TabState.STARTING
        )

        // Spawn the PTY process
        val handle = try {
            ptyBridge.spawnSession(rows = rows, cols = cols, shellPath = shellPath)
        } catch (e: PtyException) {
            Log.e(TAG, "Failed to spawn session $sessionId", e)
            emitEvent(TerminalEvent.Error(sessionId, "Failed to start shell: ${e.message}", e))
            throw e
        }

        val dimensions = TerminalDimensions(rows, cols)
        val screenModel = TerminalScreenModel(rows, cols)
        val parser = VtParser(screenModel)

        // Start the background reader coroutine
        val readerJob = serviceScope.launch(Dispatchers.IO + CoroutineName("pty-reader-$sessionId")) {
            runReaderLoop(sessionId, handle, parser, screenModel, dimensions)
        }

        sessions[sessionId] = SessionState(
            tab = tab.copy(state = TabState.RUNNING),
            handle = handle,
            screenModel = screenModel,
            parser = parser,
            readerJob = readerJob,
            dimensions = dimensions
        )

        Log.i(TAG, "Session $sessionId started successfully")
        return sessionId
    }

    /**
     * Write keyboard input to the shell.
     * Bytes are written directly to the PTY master; no encoding is done here.
     * Use [TerminalInputHandler.encodeKey] to produce the correct bytes.
     *
     * @param sessionId Target session ID
     * @param data Raw bytes to write (keyboard input, paste content, etc.)
     */
    suspend fun writeInput(sessionId: String, data: ByteArray) {
        val session = sessions[sessionId] ?: run {
            Log.w(TAG, "writeInput: session $sessionId not found")
            return
        }
        try {
            ptyBridge.writeInput(session.handle, data)
        } catch (e: PtyException) {
            Log.e(TAG, "writeInput failed for $sessionId", e)
            emitEvent(TerminalEvent.Error(sessionId, "Write failed: ${e.message}", e))
        }
    }

    /**
     * Resize an existing terminal session to new dimensions.
     * Called when the Compose layout measures a new size (orientation change,
     * desktop mode, split pane resize).
     *
     * @param sessionId Target session ID
     * @param rows New height in rows
     * @param cols New width in columns
     */
    suspend fun resizeSession(sessionId: String, rows: Int, cols: Int) {
        val session = sessions[sessionId] ?: return
        if (session.dimensions.rows == rows && session.dimensions.cols == cols) return

        Log.d(TAG, "Resizing session $sessionId to ${rows}x${cols}")

        try {
            ptyBridge.resize(session.handle, rows, cols)
            session.screenModel.resize(rows, cols)
            sessions[sessionId] = session.copy(
                dimensions = TerminalDimensions(rows, cols)
            )
            emitEvent(TerminalEvent.Resized(sessionId, TerminalDimensions(rows, cols)))
        } catch (e: PtyException) {
            Log.e(TAG, "Resize failed for $sessionId", e)
        }
    }

    /**
     * Get the current screen snapshot for a session (for initial render or
     * re-attaching after configuration change).
     *
     * @param sessionId Target session ID
     * @return Current [ScreenSnapshot] or null if session not found
     */
    fun getSnapshot(sessionId: String): ScreenSnapshot? =
        sessions[sessionId]?.screenModel?.takeSnapshot()

    /** Get all active tabs (for TabRow rendering) */
    fun getActiveTabs(): List<TerminalTab> = sessions.values.map { it.tab }

    /** True if the session exists and the shell process is running */
    fun isSessionAlive(sessionId: String): Boolean =
        sessions[sessionId]?.let { ptyBridge.isAlive(it.handle) } ?: false

    /**
     * Kill a specific session and clean up resources.
     *
     * @param sessionId Target session ID
     */
    suspend fun killSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        Log.i(TAG, "Killing session $sessionId")
        session.readerJob.cancelAndJoin()
        try { ptyBridge.killSession(session.handle) } catch (_: Exception) {}
    }

    /**
     * Kill all sessions. Called from Service.onDestroy().
     */
    suspend fun killAllSessions() {
        Log.i(TAG, "Killing all ${sessions.size} sessions")
        val ids = sessions.keys.toList()
        ids.forEach { killSession(it) }
    }

    /** Returns the count of currently active sessions */
    fun activeSessionCount(): Int = sessions.size

    // -----------------------------------------------------------------------
    // PRIVATE: PTY READER LOOP
    // -----------------------------------------------------------------------

    /**
     * Runs continuously on Dispatchers.IO, reading raw bytes from the PTY
     * and feeding them through [VtParser] -> [TerminalScreenModel].
     *
     * Emits [TerminalEvent.ScreenUpdated] after each read burst (bounded
     * by SNAPSHOT_EMIT_INTERVAL_MS to avoid flooding the UI).
     *
     * When the stream ends (shell exits), emits [TerminalEvent.SessionDied].
     */
    private suspend fun runReaderLoop(
        sessionId: String,
        handle: PtyHandle,
        parser: VtParser,
        screenModel: TerminalScreenModel,
        initialDimensions: TerminalDimensions
    ) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        var lastEmitTime = 0L

        Log.d(TAG, "Reader loop started for session $sessionId")

        try {
            while (currentCoroutineContext().isActive) {
                val n = handle.outputStream.read(buffer)
                if (n <= 0) {
                    // EOF: shell exited
                    Log.i(TAG, "Session $sessionId EOF (shell exited)")
                    break
                }

                // Feed bytes to VT parser → screen model (synchronized internally)
                parser.advance(buffer, n)

                // Emit snapshot update at max display rate
                val now = System.currentTimeMillis()
                if (now - lastEmitTime >= SNAPSHOT_EMIT_INTERVAL_MS) {
                    val snapshot = screenModel.takeSnapshot()
                    emitEvent(TerminalEvent.ScreenUpdated(sessionId, snapshot))

                    // Check if title changed and emit separate event
                    if (snapshot.title != sessions[sessionId]?.tab?.name) {
                        emitEvent(TerminalEvent.TitleChanged(sessionId, snapshot.title))
                    }
                    lastEmitTime = now
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Reader loop cancelled for $sessionId")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reader loop error for $sessionId", e)
            emitEvent(TerminalEvent.Error(sessionId, "Read error: ${e.message}", e))
        } finally {
            // Emit final snapshot so UI shows shell exit state
            val finalSnapshot = screenModel.takeSnapshot()
            emitEvent(TerminalEvent.ScreenUpdated(sessionId, finalSnapshot))
            emitEvent(TerminalEvent.SessionDied(sessionId, exitCode = -1))

            // Update session state to DEAD
            sessions[sessionId]?.let {
                sessions[sessionId] = it.copy(tab = it.tab.copy(state = TabState.DEAD))
            }
            Log.d(TAG, "Reader loop finished for $sessionId")
        }
    }

    private suspend fun emitEvent(event: TerminalEvent) {
        _events.emit(event)
    }
}
