/*
 * TerminalModels.kt
 * Package: com.andcodedit.terminal
 *
 * PURPOSE: Core immutable domain models for the terminal subsystem.
 * All terminal components (screen state machine, UI renderer, ViewModel)
 * share these types. Designed for zero-copy rendering snapshots and
 * exhaustive sealed-class pattern matching.
 *
 * AGENT: AGENT-1 (Domain Models)
 * PHASE: 1 — Full Interactive Terminal
 * SELF-CHECK: All sealed classes are exhaustive. All data classes
 *             are immutable by default (val-only fields). No Android
 *             framework imports — pure Kotlin, fully unit-testable.
 */
package com.andcodedit.terminal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// ---------------------------------------------------------------------------
// COLOR MODEL
// ---------------------------------------------------------------------------

/**
 * Represents a terminal color. Follows xterm-256color spec:
 *   Default  → use the terminal's configured default fg/bg
 *   Indexed  → ANSI 256-color palette (0–255)
 *   TrueColor → 24-bit RGB (xterm extensions \e[38;2;r;g;bm)
 *
 * Using a sealed class (not enum) so TrueColor can carry RGB data
 * without boxing overhead in the hot rendering path.
 */
sealed class TerminalColor {
    /** Terminal default foreground or background color */
    object Default : TerminalColor()

    /**
     * ANSI 256-color palette index.
     * @param index 0–7  standard colors
     *              8–15 high-intensity colors
     *              16–231 216-color RGB cube
     *              232–255 grayscale ramp
     */
    data class Indexed(val index: Int) : TerminalColor() {
        init { require(index in 0..255) { "Color index must be 0-255, got $index" } }
    }

    /**
     * 24-bit true color (xterm-256color extension).
     * @param r Red channel 0–255
     * @param g Green channel 0–255
     * @param b Blue channel 0–255
     */
    data class TrueColor(val r: Int, val g: Int, val b: Int) : TerminalColor() {
        init {
            require(r in 0..255) { "Red must be 0-255" }
            require(g in 0..255) { "Green must be 0-255" }
            require(b in 0..255) { "Blue must be 0-255" }
        }

        /** Pack to Android Color int for use with Canvas/Paint APIs */
        fun toArgb(): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

// ---------------------------------------------------------------------------
// CELL ATTRIBUTES (SGR — Select Graphic Rendition)
// ---------------------------------------------------------------------------

/**
 * Visual attributes for a single terminal cell, as set by ANSI SGR sequences.
 * All fields are val (immutable). Use copy() to derive modified versions.
 *
 * SGR codes handled:
 *   0  = Reset all
 *   1  = Bold
 *   3  = Italic
 *   4  = Underline
 *   5  = Blink
 *   7  = Inverse (swap fg/bg)
 *   9  = Strikethrough
 *   22 = Not bold
 *   23 = Not italic
 *   24 = Not underline
 *   30-37, 90-97 = Standard fg colors
 *   40-47, 100-107 = Standard bg colors
 *   38;5;n  = 256-color fg
 *   48;5;n  = 256-color bg
 *   38;2;r;g;b = TrueColor fg
 *   48;2;r;g;b = TrueColor bg
 */
data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.Default,
    val background: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val blink: Boolean = false,
    val inverse: Boolean = false,
    val strikethrough: Boolean = false,
    val dim: Boolean = false
) {
    companion object {
        /** Factory: default terminal cell attributes (no styling) */
        val DEFAULT = CellAttributes()

        /** Standard ANSI 8 foreground colors indexed 30-37 */
        val ANSI_COLORS = arrayOf(
            TerminalColor.Indexed(0),  // Black
            TerminalColor.Indexed(1),  // Red
            TerminalColor.Indexed(2),  // Green
            TerminalColor.Indexed(3),  // Yellow
            TerminalColor.Indexed(4),  // Blue
            TerminalColor.Indexed(5),  // Magenta
            TerminalColor.Indexed(6),  // Cyan
            TerminalColor.Indexed(7)   // White
        )
    }
}

// ---------------------------------------------------------------------------
// SCREEN CELL
// ---------------------------------------------------------------------------

/**
 * A single character cell in the terminal grid.
 * @param char The Unicode code point to display. Use ' ' for blank.
 * @param attrs Visual styling for this cell.
 * @param isWide True for CJK wide characters that occupy 2 columns.
 */
data class TerminalCell(
    val char: Char = ' ',
    val attrs: CellAttributes = CellAttributes.DEFAULT,
    val isWide: Boolean = false
) {
    companion object {
        /** Blank cell with default attributes — used to fill new rows */
        val BLANK = TerminalCell()
    }
}

// ---------------------------------------------------------------------------
// GEOMETRY
// ---------------------------------------------------------------------------

/**
 * Zero-indexed cursor position within the terminal grid.
 * @param row 0 = top row
 * @param col 0 = leftmost column
 */
@Parcelize
data class CursorPosition(
    val row: Int = 0,
    val col: Int = 0
) : Parcelable {
    init {
        require(row >= 0) { "Cursor row must be >= 0" }
        require(col >= 0) { "Cursor col must be >= 0" }
    }
}

/**
 * Terminal grid dimensions.
 * @param rows Number of visible rows (lines)
 * @param cols Number of visible columns (characters per line)
 */
@Parcelize
data class TerminalDimensions(
    val rows: Int,
    val cols: Int
) : Parcelable {
    init {
        require(rows > 0) { "Terminal rows must be > 0" }
        require(cols > 0) { "Terminal cols must be > 0" }
    }

    /** Total cell count in the visible grid */
    val totalCells: Int get() = rows * cols
}

/**
 * Scroll region boundaries (1-indexed as per ANSI spec, but stored 0-indexed here).
 * The PTY backend expects 0-indexed; conversion happens at the protocol boundary.
 */
data class ScrollRegion(
    val top: Int,    // inclusive, 0-indexed
    val bottom: Int  // inclusive, 0-indexed
) {
    fun contains(row: Int): Boolean = row in top..bottom
    val height: Int get() = bottom - top + 1
}

// ---------------------------------------------------------------------------
// SCREEN SNAPSHOT (immutable, for UI rendering)
// ---------------------------------------------------------------------------

/**
 * An immutable, point-in-time snapshot of the terminal screen.
 * This is what the Compose UI renders. The screen state machine
 * produces new snapshots on each update; the UI receives them via
 * StateFlow and renders the latest one.
 *
 * Immutability guarantee: lines is a deep-copied list of lists.
 * The UI thread never races with the PTY reader thread.
 *
 * @param lines  Row-major grid of cells [row][col]
 * @param cursor Current cursor position
 * @param cursorVisible Whether the cursor should be drawn (DECTCEM)
 * @param title  Terminal window title (from OSC 0/2 sequences)
 * @param dimensions Current visible grid size
 * @param scrollbackLines Lines above the visible area (scrollback buffer)
 * @param inputMode Whether cursor keys send application sequences
 */
data class ScreenSnapshot(
    val lines: List<List<TerminalCell>>,
    val cursor: CursorPosition,
    val cursorVisible: Boolean = true,
    val title: String = "Terminal",
    val dimensions: TerminalDimensions,
    val scrollbackLines: List<List<TerminalCell>> = emptyList(),
    val inputMode: InputMode = InputMode.Normal
)

// ---------------------------------------------------------------------------
// INPUT MODE
// ---------------------------------------------------------------------------

/**
 * Terminal input mode affects how cursor/function keys are encoded.
 * Normal: cursor keys send ESC[A etc. (default)
 * Application: cursor keys send ESCO etc. (set by \e[?1h, cleared by \e[?1l)
 */
enum class InputMode { Normal, Application }

// ---------------------------------------------------------------------------
// TERMINAL SESSION
// ---------------------------------------------------------------------------

/**
 * Metadata for a single terminal session (tab).
 * Stored in Room for persistence across process death.
 *
 * @param id         Unique session identifier (UUID)
 * @param name       Display name shown in the tab bar
 * @param shellPath  Path to the shell binary being used
 * @param createdAt  Unix epoch ms when session was created
 * @param state      Current lifecycle state of the session
 */
@Parcelize
data class TerminalTab(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Terminal",
    val shellPath: String = "/system/bin/sh",
    val createdAt: Long = System.currentTimeMillis(),
    val state: TabState = TabState.STARTING
) : Parcelable

/** Lifecycle state of a terminal tab/session */
enum class TabState {
    STARTING,   // PTY spawned, shell not yet ready
    RUNNING,    // Shell is active and accepting input
    DEAD,       // Shell process exited (exit code available)
    RECONNECTING // Attempting to reconnect after desktop mode change
}

// ---------------------------------------------------------------------------
// TERMINAL EVENTS (PTY output events for the ViewModel)
// ---------------------------------------------------------------------------

/**
 * Events emitted by the PTY session manager to the ViewModel.
 * Using sealed class for exhaustive when() handling.
 */
sealed class TerminalEvent {
    /** New screen snapshot available for rendering */
    data class ScreenUpdated(val sessionId: String, val snapshot: ScreenSnapshot) : TerminalEvent()

    /** Shell process exited */
    data class SessionDied(val sessionId: String, val exitCode: Int) : TerminalEvent()

    /** Terminal title changed (OSC sequence) */
    data class TitleChanged(val sessionId: String, val title: String) : TerminalEvent()

    /** PTY resize completed */
    data class Resized(val sessionId: String, val dimensions: TerminalDimensions) : TerminalEvent()

    /** Hyperlink detected in terminal output (OSC 8) */
    data class HyperlinkDetected(val sessionId: String, val url: String, val text: String) : TerminalEvent()

    /** Non-fatal error (e.g. write to dead session) */
    data class Error(val sessionId: String, val message: String, val cause: Throwable? = null) : TerminalEvent()
}

// ---------------------------------------------------------------------------
// SESSION LIFECYCLE STATE (for ViewModel UI state)
// ---------------------------------------------------------------------------

/**
 * Full UI state for the terminal screen, managed by TerminalViewModel.
 * Sealed to enforce exhaustive rendering in Composables.
 */
sealed class TerminalSessionState {
    object Loading : TerminalSessionState()
    data class Active(
        val tabs: List<TerminalTab>,
        val activeTabId: String,
        val snapshots: Map<String, ScreenSnapshot>,
        val isKeyboardVisible: Boolean = false
    ) : TerminalSessionState()
    data class Error(val message: String, val retryable: Boolean = true) : TerminalSessionState()
    object NoSessions : TerminalSessionState()
}
