/*
 * TerminalScreenModel.kt
 * Package: com.andcodedit.terminal
 *
 * PURPOSE: Mutable screen grid state machine. Implements ScreenStateCallback
 * (called by VtParser) and maintains the current terminal grid, cursor,
 * attributes, scroll region, and 5000-line scrollback buffer.
 *
 * This class is the "virtual screen" that the Compose Canvas renders.
 * It produces immutable ScreenSnapshot copies on demand.
 *
 * THREAD SAFETY: All methods must be called from a single thread
 * (the PTY reader coroutine on Dispatchers.IO). The UI retrieves
 * snapshots via [takeSnapshot] which is synchronized.
 *
 * AGENT: AGENT-2 (Screen State Machine)
 * SELF-CHECK: TerminalScreenModelTest.kt validates all state transitions.
 *             Key invariants checked after every test:
 *               cursor.row in [0, rows-1]
 *               cursor.col in [0, cols]
 *               scrollbackBuffer.size <= MAX_SCROLLBACK_LINES
 */
package com.andcodedit.terminal

import java.util.ArrayDeque

/** Maximum lines stored in the scrollback buffer */
private const val MAX_SCROLLBACK_LINES = 5000

/**
 * Mutable terminal screen state. Do not expose directly to the UI —
 * use [takeSnapshot] to produce an immutable [ScreenSnapshot].
 */
class TerminalScreenModel(
    private var rows: Int,
    private var cols: Int
) : ScreenStateCallback {

    // -----------------------------------------------------------------------
    // SCREEN GRID
    // -----------------------------------------------------------------------

    /** The visible screen grid. grid[row][col] = TerminalCell */
    private var grid: Array<Array<TerminalCell>> = newGrid(rows, cols)

    /** Current cursor position (0-indexed) */
    private var cursor = CursorPosition(0, 0)

    /** Saved cursor position (ESC 7 / ESC[s) */
    private var savedCursor = CursorPosition(0, 0)
    private var savedAttrs = CellAttributes.DEFAULT

    /** Current cell attributes for newly printed characters */
    private var currentAttrs = CellAttributes.DEFAULT

    /** Whether the cursor is visible (DECTCEM: \e[?25h/l) */
    private var cursorVisible = true

    /** Current scroll region (defaults to full screen) */
    private var scrollRegion = ScrollRegion(0, rows - 1)

    /** Whether auto-wrap is enabled (\e[?7h/l) */
    private var autoWrap = true

    /** Pending wrap: next printChar will wrap to next line */
    private var pendingWrap = false

    /** Current input mode (affects cursor key encoding) */
    private var inputMode = InputMode.Normal

    /** Alternate screen buffer (activated by \e[?1049h) */
    private var altGrid: Array<Array<TerminalCell>>? = null
    private var altCursor = CursorPosition(0, 0)
    private var usingAltScreen = false

    /** Terminal window title */
    private var title = "Terminal"

    // -----------------------------------------------------------------------
    // SCROLLBACK BUFFER
    // -----------------------------------------------------------------------

    /**
     * Scrollback buffer: lines that have scrolled off the top of the
     * visible area. ArrayDeque used as a ring buffer — when full,
     * oldest line is removed from the front.
     */
    private val scrollback = ArrayDeque<List<TerminalCell>>(MAX_SCROLLBACK_LINES)

    // -----------------------------------------------------------------------
    // ScreenStateCallback IMPLEMENTATION
    // -----------------------------------------------------------------------

    /**
     * Print a single character at the current cursor position.
     * Handles wide characters (CJK) by occupying 2 columns.
     */
    override fun printChar(char: Char) {
        // Handle pending auto-wrap from previous character
        if (pendingWrap) {
            pendingWrap = false
            cursor = cursor.copy(col = 0)
            doLineFeed()
        }

        val isWide = isWideChar(char)
        val cell = TerminalCell(char, currentAttrs, isWide)

        setCell(cursor.row, cursor.col, cell)
        if (isWide && cursor.col + 1 < cols) {
            // Placeholder cell for second column of wide char
            setCell(cursor.row, cursor.col + 1, TerminalCell('\u0000', currentAttrs, false))
        }

        val advance = if (isWide) 2 else 1
        if (cursor.col + advance >= cols) {
            if (autoWrap) {
                pendingWrap = true
            }
        } else {
            cursor = cursor.copy(col = cursor.col + advance)
        }
    }

    /**
     * Handle C0 control characters.
     * \n (LF), \r (CR), \b (BS), \t (HT), \a (BEL), \u000E/\u000F (SO/SI)
     */
    override fun execute(byte: Byte) {
        when (byte.toInt() and 0xFF) {
            0x08 -> { // Backspace — move cursor left (don't erase)
                if (cursor.col > 0) cursor = cursor.copy(col = cursor.col - 1)
                pendingWrap = false
            }
            0x09 -> horizontalTab()   // HT (Tab)
            0x0A, 0x0B, 0x0C -> {    // LF / VT / FF
                pendingWrap = false
                doLineFeed()
            }
            0x0D -> {                 // CR
                pendingWrap = false
                cursor = cursor.copy(col = 0)
            }
            0x07 -> { /* BEL — could trigger vibration in future */ }
            0x0E -> { /* SO (Shift Out) — alternate charset, not implemented */ }
            0x0F -> { /* SI (Shift In) — default charset, not implemented */ }
        }
    }

    /**
     * Handle CSI (Control Sequence Introducer) escape sequences.
     * ESC [ <params> <final>
     *
     * params[i] == -1 means the parameter was omitted (use default).
     */
    override fun csiDispatch(params: IntArray, paramCount: Int, intermediates: ByteArray, action: Char) {
        val isPrivate = intermediates.isNotEmpty() && intermediates[0] == '?'.code.toByte()

        when (action) {
            // --- Cursor Movement ---
            'A' -> moveCursorRelative(row = -(params.getOrDefault(0, 1)))
            'B' -> moveCursorRelative(row = params.getOrDefault(0, 1))
            'C' -> moveCursorRelative(col = params.getOrDefault(0, 1))
            'D' -> moveCursorRelative(col = -(params.getOrDefault(0, 1)))
            'E' -> { cursor = cursor.copy(col = 0); moveCursorRelative(row = params.getOrDefault(0, 1)) }
            'F' -> { cursor = cursor.copy(col = 0); moveCursorRelative(row = -(params.getOrDefault(0, 1))) }
            'G' -> cursor = cursor.copy(col = (params.getOrDefault(0, 1) - 1).coerceIn(0, cols - 1))
            'H', 'f' -> {
                val row = (params.getOrDefault(0, 1) - 1).coerceIn(0, rows - 1)
                val col = (params.getOrDefault(1, 1) - 1).coerceIn(0, cols - 1)
                cursor = CursorPosition(row, col)
                pendingWrap = false
            }

            // --- Erase ---
            'J' -> eraseInDisplay(params.getOrDefault(0, 0))
            'K' -> eraseInLine(params.getOrDefault(0, 0))

            // --- Insert/Delete ---
            'L' -> insertLines(params.getOrDefault(0, 1))
            'M' -> deleteLines(params.getOrDefault(0, 1))
            'P' -> deleteChars(params.getOrDefault(0, 1))
            '@' -> insertChars(params.getOrDefault(0, 1))

            // --- Scroll ---
            'S' -> scrollUp(params.getOrDefault(0, 1))
            'T' -> scrollDown(params.getOrDefault(0, 1))

            // --- SGR (Select Graphic Rendition — colors and styling) ---
            'm' -> selectGraphicRendition(params, paramCount)

            // --- Scroll region ---
            'r' -> {
                val top = (params.getOrDefault(0, 1) - 1).coerceIn(0, rows - 1)
                val bottom = (params.getOrDefault(1, rows) - 1).coerceIn(top, rows - 1)
                scrollRegion = ScrollRegion(top, bottom)
                cursor = CursorPosition(0, 0) // Cursor moves to origin after DECSTBM
            }

            // --- Cursor Save/Restore ---
            's' -> { savedCursor = cursor; savedAttrs = currentAttrs }
            'u' -> { cursor = savedCursor; currentAttrs = savedAttrs }

            // --- Window manipulation ---
            't' -> { /* Window ops — ignore safely */ }

            // --- Private modes (ESC [ ? ... h/l) ---
            'h' -> if (isPrivate) setPrivateMode(params, paramCount, enable = true)
            'l' -> if (isPrivate) setPrivateMode(params, paramCount, enable = false)

            // --- Cursor style ---
            'q' -> { /* DECSCUSR cursor style — ignore for now */ }

            else -> { /* Unknown sequence — safe to ignore */ }
        }
    }

    /** Handle ESC sequences (non-CSI, non-OSC) */
    override fun escDispatch(intermediate: Byte, action: Char) {
        when (action) {
            '7' -> { savedCursor = cursor; savedAttrs = currentAttrs }  // DECSC Save cursor
            '8' -> { cursor = savedCursor; currentAttrs = savedAttrs }  // DECRC Restore cursor
            'c' -> resetTerminal()                                        // RIS Full reset
            'D' -> doLineFeed()                                          // IND Index
            'E' -> { cursor = cursor.copy(col = 0); doLineFeed() }      // NEL Next line
            'M' -> reverseIndex()                                        // RI Reverse index
            'H' -> { /* HTS Horizontal tab stop — not implemented */ }
            else -> { /* Unknown ESC sequence */ }
        }
    }

    /** Handle OSC (Operating System Command) sequences */
    override fun oscDispatch(command: Int, data: String) {
        when (command) {
            0, 1, 2 -> title = data.take(256).ifEmpty { "Terminal" }
            8 -> { /* OSC 8 hyperlinks — TODO: emit HyperlinkDetected event */ }
            else -> { /* Unknown OSC command */ }
        }
    }

    // DCS passthrough — minimal stub implementations
    override fun hook(params: IntArray, paramCount: Int, intermediates: ByteArray, action: Char) {}
    override fun put(byte: Byte) {}
    override fun unhook() {}

    // -----------------------------------------------------------------------
    // RESIZE (called when display geometry changes)
    // -----------------------------------------------------------------------

    /**
     * Resize the terminal grid to [newRows] x [newCols].
     * - Expanding: new cells filled with BLANK
     * - Shrinking: lines may be pushed to scrollback; cursor clamped
     * - Must be called from the same thread as printChar etc.
     */
    fun resize(newRows: Int, newCols: Int) {
        require(newRows > 0 && newCols > 0) { "Dimensions must be positive" }
        if (newRows == rows && newCols == cols) return

        val newGrid = newGrid(newRows, newCols)

        // Copy existing content into new grid (up to min dimensions)
        val copyRows = minOf(rows, newRows)
        val copyCols = minOf(cols, newCols)
        for (r in 0 until copyRows) {
            for (c in 0 until copyCols) {
                newGrid[r][c] = grid[r][c]
            }
        }

        grid = newGrid
        rows = newRows
        cols = newCols

        // Clamp cursor to new dimensions
        cursor = CursorPosition(
            row = cursor.row.coerceIn(0, rows - 1),
            col = cursor.col.coerceIn(0, cols - 1)
        )

        // Reset scroll region to full screen after resize
        scrollRegion = ScrollRegion(0, rows - 1)
        pendingWrap = false
    }

    // -----------------------------------------------------------------------
    // SNAPSHOT (thread-safe read for UI)
    // -----------------------------------------------------------------------

    /**
     * Produce an immutable snapshot of the current screen for the UI.
     * Deep-copies the grid cells to guarantee thread safety.
     * Called from the UI coroutine; the grid may be modified concurrently
     * on the reader coroutine, so this is synchronized.
     */
    @Synchronized
    fun takeSnapshot(): ScreenSnapshot {
        val lines = Array(rows) { r -> List(cols) { c -> grid[r][c] } }.toList()
        val scrollbackCopy = scrollback.toList()
        return ScreenSnapshot(
            lines = lines,
            cursor = cursor,
            cursorVisible = cursorVisible,
            title = title,
            dimensions = TerminalDimensions(rows, cols),
            scrollbackLines = scrollbackCopy,
            inputMode = inputMode
        )
    }

    // -----------------------------------------------------------------------
    // PRIVATE HELPERS — Screen operations
    // -----------------------------------------------------------------------

    private fun doLineFeed() {
        if (cursor.row == scrollRegion.bottom) {
            scrollUp(1)
        } else if (cursor.row < rows - 1) {
            cursor = cursor.copy(row = cursor.row + 1)
        }
    }

    private fun reverseIndex() {
        if (cursor.row == scrollRegion.top) {
            scrollDown(1)
        } else if (cursor.row > 0) {
            cursor = cursor.copy(row = cursor.row - 1)
        }
    }

    private fun scrollUp(count: Int) {
        repeat(count) {
            // Push top line of scroll region to scrollback
            if (!usingAltScreen) {
                val topLine = List(cols) { c -> grid[scrollRegion.top][c] }
                scrollback.addLast(topLine)
                if (scrollback.size > MAX_SCROLLBACK_LINES) scrollback.removeFirst()
            }
            // Shift rows up within scroll region
            for (r in scrollRegion.top until scrollRegion.bottom) {
                grid[r] = grid[r + 1]
            }
            // Clear bottom row
            grid[scrollRegion.bottom] = blankRow()
        }
    }

    private fun scrollDown(count: Int) {
        repeat(count) {
            for (r in scrollRegion.bottom downTo scrollRegion.top + 1) {
                grid[r] = grid[r - 1]
            }
            grid[scrollRegion.top] = blankRow()
        }
    }

    private fun horizontalTab() {
        val nextTab = ((cursor.col / 8) + 1) * 8
        cursor = cursor.copy(col = minOf(nextTab, cols - 1))
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // Erase below cursor (inclusive)
                eraseInLine(0)
                for (r in cursor.row + 1 until rows) grid[r] = blankRow()
            }
            1 -> { // Erase above cursor (inclusive)
                for (r in 0 until cursor.row) grid[r] = blankRow()
                eraseInLine(1)
            }
            2 -> for (r in 0 until rows) grid[r] = blankRow() // Erase all
            3 -> { // Erase all + scrollback
                for (r in 0 until rows) grid[r] = blankRow()
                scrollback.clear()
            }
        }
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursor.col until cols) setCell(cursor.row, c, TerminalCell.BLANK)
            1 -> for (c in 0..cursor.col) setCell(cursor.row, c, TerminalCell.BLANK)
            2 -> grid[cursor.row] = blankRow()
        }
    }

    private fun insertLines(count: Int) {
        if (cursor.row !in scrollRegion.top..scrollRegion.bottom) return
        repeat(count) {
            for (r in scrollRegion.bottom downTo cursor.row + 1) grid[r] = grid[r - 1]
            grid[cursor.row] = blankRow()
        }
        cursor = cursor.copy(col = 0)
    }

    private fun deleteLines(count: Int) {
        if (cursor.row !in scrollRegion.top..scrollRegion.bottom) return
        repeat(count) {
            for (r in cursor.row until scrollRegion.bottom) grid[r] = grid[r + 1]
            grid[scrollRegion.bottom] = blankRow()
        }
        cursor = cursor.copy(col = 0)
    }

    private fun deleteChars(count: Int) {
        val row = grid[cursor.row].toMutableList()
        repeat(count) { row.removeAt(cursor.col.coerceAtMost(row.size - 1)) }
        while (row.size < cols) row.add(TerminalCell.BLANK)
        grid[cursor.row] = row.toTypedArray()
    }

    private fun insertChars(count: Int) {
        val row = grid[cursor.row].toMutableList()
        repeat(count) { row.add(cursor.col, TerminalCell.BLANK) }
        while (row.size > cols) row.removeLast()
        grid[cursor.row] = row.toTypedArray()
    }

    private fun moveCursorRelative(row: Int = 0, col: Int = 0) {
        val newRow = (cursor.row + row).coerceIn(0, rows - 1)
        val newCol = (cursor.col + col).coerceIn(0, cols - 1)
        cursor = CursorPosition(newRow, newCol)
        pendingWrap = false
    }

    /**
     * SGR (Select Graphic Rendition) — colors and text styling.
     * Handles: reset (0), bold (1), dim (2), italic (3), underline (4),
     * blink (5), inverse (7), strikethrough (9), standard colors (30-37,
     * 40-47, 90-97, 100-107), 256-color (38;5;n / 48;5;n),
     * truecolor (38;2;r;g;b / 48;2;r;g;b), reset fg/bg (39/49).
     */
    private fun selectGraphicRendition(params: IntArray, paramCount: Int) {
        var i = 0
        val count = if (paramCount == 0) 1 else paramCount  // "\e[m" = reset
        while (i < count) {
            val p = if (i < paramCount && params[i] != -1) params[i] else 0
            when (p) {
                0 -> currentAttrs = CellAttributes.DEFAULT
                1 -> currentAttrs = currentAttrs.copy(bold = true)
                2 -> currentAttrs = currentAttrs.copy(dim = true)
                3 -> currentAttrs = currentAttrs.copy(italic = true)
                4 -> currentAttrs = currentAttrs.copy(underline = true)
                5, 6 -> currentAttrs = currentAttrs.copy(blink = true)
                7 -> currentAttrs = currentAttrs.copy(inverse = true)
                9 -> currentAttrs = currentAttrs.copy(strikethrough = true)
                22 -> currentAttrs = currentAttrs.copy(bold = false, dim = false)
                23 -> currentAttrs = currentAttrs.copy(italic = false)
                24 -> currentAttrs = currentAttrs.copy(underline = false)
                25 -> currentAttrs = currentAttrs.copy(blink = false)
                27 -> currentAttrs = currentAttrs.copy(inverse = false)
                29 -> currentAttrs = currentAttrs.copy(strikethrough = false)
                in 30..37 -> currentAttrs = currentAttrs.copy(foreground = CellAttributes.ANSI_COLORS[p - 30])
                38 -> {
                    val color = parseExtendedColor(params, paramCount, i)
                    if (color != null) { currentAttrs = currentAttrs.copy(foreground = color.first); i += color.second }
                }
                39 -> currentAttrs = currentAttrs.copy(foreground = TerminalColor.Default)
                in 40..47 -> currentAttrs = currentAttrs.copy(background = CellAttributes.ANSI_COLORS[p - 40])
                48 -> {
                    val color = parseExtendedColor(params, paramCount, i)
                    if (color != null) { currentAttrs = currentAttrs.copy(background = color.first); i += color.second }
                }
                49 -> currentAttrs = currentAttrs.copy(background = TerminalColor.Default)
                in 90..97 -> currentAttrs = currentAttrs.copy(foreground = TerminalColor.Indexed(p - 90 + 8))
                in 100..107 -> currentAttrs = currentAttrs.copy(background = TerminalColor.Indexed(p - 100 + 8))
            }
            i++
        }
    }

    /** Parse 256-color or TrueColor from SGR params, returns (color, consumedExtra) */
    private fun parseExtendedColor(params: IntArray, paramCount: Int, startIdx: Int): Pair<TerminalColor, Int>? {
        val next = if (startIdx + 1 < paramCount) params[startIdx + 1] else return null
        return when (next) {
            5 -> { // 256-color: 38;5;n
                val idx = if (startIdx + 2 < paramCount) params[startIdx + 2] else return null
                Pair(TerminalColor.Indexed(idx.coerceIn(0, 255)), 2)
            }
            2 -> { // TrueColor: 38;2;r;g;b
                if (startIdx + 4 >= paramCount) return null
                val r = params[startIdx + 2]
                val g = params[startIdx + 3]
                val b = params[startIdx + 4]
                Pair(TerminalColor.TrueColor(r, g, b), 4)
            }
            else -> null
        }
    }

    private fun setPrivateMode(params: IntArray, paramCount: Int, enable: Boolean) {
        val count = maxOf(1, paramCount)
        for (i in 0 until count) {
            val mode = if (i < paramCount) params[i] else -1
            when (mode) {
                1 -> inputMode = if (enable) InputMode.Application else InputMode.Normal
                7 -> autoWrap = enable
                25 -> cursorVisible = enable  // DECTCEM
                1049 -> {
                    if (enable && !usingAltScreen) {
                        // Switch to alternate screen
                        altGrid = grid.copyOf()
                        altCursor = cursor
                        grid = newGrid(rows, cols)
                        cursor = CursorPosition(0, 0)
                        usingAltScreen = true
                    } else if (!enable && usingAltScreen) {
                        // Restore main screen
                        altGrid?.let { grid = it }
                        cursor = altCursor
                        altGrid = null
                        usingAltScreen = false
                    }
                }
            }
        }
    }

    private fun resetTerminal() {
        grid = newGrid(rows, cols)
        cursor = CursorPosition(0, 0)
        savedCursor = CursorPosition(0, 0)
        currentAttrs = CellAttributes.DEFAULT
        savedAttrs = CellAttributes.DEFAULT
        cursorVisible = true
        autoWrap = true
        pendingWrap = false
        scrollRegion = ScrollRegion(0, rows - 1)
        inputMode = InputMode.Normal
        scrollback.clear()
        title = "Terminal"
    }

    private fun setCell(row: Int, col: Int, cell: TerminalCell) {
        if (row in 0 until rows && col in 0 until cols) {
            grid[row][col] = cell
        }
    }

    private fun blankRow(): Array<TerminalCell> = Array(cols) { TerminalCell.BLANK }

    private fun newGrid(r: Int, c: Int): Array<Array<TerminalCell>> =
        Array(r) { Array(c) { TerminalCell.BLANK } }

    /** Returns true if char is an East Asian Wide character (occupies 2 columns) */
    private fun isWideChar(char: Char): Boolean {
        val cp = char.code
        return cp in 0x1100..0x115F ||   // Hangul Jamo
               cp in 0x2E80..0x303E ||   // CJK Radicals
               cp in 0x3041..0x33FF ||   // CJK Misc
               cp in 0x3400..0x4DBF ||   // CJK Extension A
               cp in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
               cp in 0xAC00..0xD7AF ||   // Hangul Syllables
               cp in 0xF900..0xFAFF ||   // CJK Compatibility Ideographs
               cp in 0xFE10..0xFE6F ||   // CJK Compatibility Forms
               cp in 0xFF00..0xFF60 ||   // Fullwidth Forms
               cp in 0xFFE0..0xFFE6      // Fullwidth Signs
    }
}

/** Safe param accessor with default value for omitted (-1) or missing params */
private fun IntArray.getOrDefault(index: Int, default: Int): Int =
    if (index < size && this[index] != -1) this[index] else default
