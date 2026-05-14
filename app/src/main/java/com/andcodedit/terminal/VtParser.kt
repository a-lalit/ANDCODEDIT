/*
 * VtParser.kt
 * Package: com.andcodedit.terminal
 *
 * PURPOSE: 5-state VT100/ANSI/xterm escape sequence parser.
 * Receives raw bytes from the PTY reader and dispatches to
 * the ScreenStateCallback interface for each decoded action.
 *
 * States: Ground → Escape → CSI / OSC / DCS → Ground
 *
 * This parser is intentionally separated from the screen model
 * so it can be unit-tested independently with mock callbacks.
 *
 * THREAD SAFETY: NOT thread-safe. Must be called from a single
 * dedicated reader coroutine (Dispatchers.IO).
 *
 * AGENT: AGENT-2 (Screen State Machine)
 * SELF-CHECK: VtParserTest.kt validates all escape sequences.
 */
package com.andcodedit.terminal

import android.util.Log

private const val TAG = "VtParser"

/** Maximum number of CSI/OSC parameters we collect before truncating */
private const val MAX_PARAMS = 16
/** Maximum length of OSC string data */
private const val MAX_OSC_LEN = 512

/**
 * Callback interface invoked by VtParser for each decoded action.
 * TerminalScreenModel implements this to update screen state.
 */
interface ScreenStateCallback {
    fun printChar(char: Char)
    fun execute(byte: Byte)                             // C0 controls (\n, \r, \b, \t, BEL)
    fun csiDispatch(params: IntArray, paramCount: Int, intermediates: ByteArray, action: Char)
    fun escDispatch(intermediate: Byte, action: Char)  // ESC sequences
    fun oscDispatch(command: Int, data: String)         // OSC sequences
    fun hook(params: IntArray, paramCount: Int, intermediates: ByteArray, action: Char) // DCS hook
    fun put(byte: Byte)                                  // DCS data
    fun unhook()                                         // DCS end
}

/**
 * Parser states for the VT state machine.
 */
private enum class ParseState {
    GROUND,    // Normal character output
    ESCAPE,    // Received 0x1B
    CSI_ENTRY, // Received ESC [
    CSI_PARAM, // Collecting CSI parameter digits
    CSI_INTER, // CSI intermediate bytes
    CSI_IGNORE,// Overflow: ignore until final byte
    OSC_STRING,// Collecting OSC data (ESC ] ... ST or BEL)
    DCS_ENTRY, // Device Control String (ESC P)
    DCS_PARAM,
    DCS_INTER,
    DCS_PASSTHROUGH,
    DCS_IGNORE,
    SOS_PM_APC_STRING // Ignore these strings
}

/**
 * Stateful VT100/ANSI/xterm escape sequence parser.
 *
 * Feed raw PTY bytes via [advance]. The parser dispatches decoded
 * actions to [callback] synchronously.
 *
 * Usage:
 * ```kotlin
 * val parser = VtParser(screenModel)
 * ptyInputStream.buffered().use { stream ->
 *     val buf = ByteArray(4096)
 *     while (true) {
 *         val n = stream.read(buf)
 *         if (n <= 0) break
 *         parser.advance(buf, n)
 *     }
 * }
 * ```
 */
class VtParser(private val callback: ScreenStateCallback) {

    private var state = ParseState.GROUND

    // CSI parameter accumulator
    private val params = IntArray(MAX_PARAMS) { 0 }
    private var paramCount = 0
    private var currentParam = 0
    private var hasCurrentParam = false
    private val intermediates = ByteArray(4)
    private var intermediateCount = 0

    // OSC string accumulator
    private val oscBuf = StringBuilder(MAX_OSC_LEN)

    /**
     * Advance the parser with [length] bytes from [data].
     * Call this from your PTY reader loop.
     */
    fun advance(data: ByteArray, length: Int = data.size) {
        for (i in 0 until length) {
            processByte(data[i])
        }
    }

    private fun processByte(b: Byte) {
        val code = b.toInt() and 0xFF

        // C1 controls (0x80-0x9F) treated as ESC + 0x40-0x5F
        if (code in 0x80..0x9F) {
            processByte(0x1B.toByte())
            processByte((code - 0x40).toByte())
            return
        }

        when (state) {
            ParseState.GROUND -> handleGround(b, code)
            ParseState.ESCAPE -> handleEscape(b, code)
            ParseState.CSI_ENTRY -> handleCsiEntry(b, code)
            ParseState.CSI_PARAM -> handleCsiParam(b, code)
            ParseState.CSI_INTER -> handleCsiInter(b, code)
            ParseState.CSI_IGNORE -> handleCsiIgnore(b, code)
            ParseState.OSC_STRING -> handleOsc(b, code)
            ParseState.DCS_ENTRY,
            ParseState.DCS_PARAM,
            ParseState.DCS_INTER,
            ParseState.DCS_PASSTHROUGH -> handleDcs(b, code)
            ParseState.DCS_IGNORE -> if (isFinalByte(code)) transitionTo(ParseState.GROUND)
            ParseState.SOS_PM_APC_STRING -> if (code == 0x9C || b == 0x07.toByte()) transitionTo(ParseState.GROUND)
        }
    }

    // -----------------------------------------------------------------------
    // STATE HANDLERS
    // -----------------------------------------------------------------------

    private fun handleGround(b: Byte, code: Int) {
        when {
            code == 0x1B -> transitionTo(ParseState.ESCAPE)   // ESC
            code < 0x20 || code == 0x7F -> callback.execute(b) // C0 controls
            code >= 0x20 -> callback.printChar(code.toChar())  // Printable
        }
    }

    private fun handleEscape(b: Byte, code: Int) {
        when (code) {
            0x5B -> { // '[' → CSI
                resetCsiParams()
                transitionTo(ParseState.CSI_ENTRY)
            }
            0x5D -> { // ']' → OSC
                oscBuf.clear()
                transitionTo(ParseState.OSC_STRING)
            }
            0x50 -> transitionTo(ParseState.DCS_ENTRY)    // 'P' DCS
            0x58, 0x5E, 0x5F -> transitionTo(ParseState.SOS_PM_APC_STRING) // SOS, PM, APC
            in 0x20..0x2F -> {                            // Intermediate
                if (intermediateCount < intermediates.size) intermediates[intermediateCount++] = b
                // Stay in ESCAPE state
            }
            in 0x30..0x7E -> {                            // Final byte
                callback.escDispatch(
                    if (intermediateCount > 0) intermediates[0] else 0,
                    code.toChar()
                )
                transitionTo(ParseState.GROUND)
            }
            else -> transitionTo(ParseState.GROUND)
        }
    }

    private fun handleCsiEntry(b: Byte, code: Int) {
        when {
            code in 0x30..0x39 -> { // Digit '0'-'9'
                currentParam = code - 0x30
                hasCurrentParam = true
                transitionTo(ParseState.CSI_PARAM)
            }
            code == 0x3B -> { // ';' parameter separator with empty first param
                flushParam()
                transitionTo(ParseState.CSI_PARAM)
            }
            code in 0x3C..0x3F -> { // Private prefix <=>?
                if (intermediateCount < intermediates.size) intermediates[intermediateCount++] = b
                transitionTo(ParseState.CSI_PARAM)
            }
            code in 0x20..0x2F -> { // Intermediate
                if (intermediateCount < intermediates.size) intermediates[intermediateCount++] = b
                transitionTo(ParseState.CSI_INTER)
            }
            isFinalByte(code) -> { // Final byte with no params
                dispatchCsi(code.toChar())
                transitionTo(ParseState.GROUND)
            }
            else -> transitionTo(ParseState.GROUND)
        }
    }

    private fun handleCsiParam(b: Byte, code: Int) {
        when {
            code in 0x30..0x39 -> { // Digit
                currentParam = currentParam * 10 + (code - 0x30)
                hasCurrentParam = true
            }
            code == 0x3B -> flushParam() // ';'
            code == 0x3A -> { /* ':' sub-param separator — skip for now */ }
            code in 0x20..0x2F -> { // Intermediate
                flushParam()
                if (intermediateCount < intermediates.size) intermediates[intermediateCount++] = b
                transitionTo(ParseState.CSI_INTER)
            }
            isFinalByte(code) -> {
                flushParam()
                dispatchCsi(code.toChar())
                transitionTo(ParseState.GROUND)
            }
            code in 0x3C..0x3F -> transitionTo(ParseState.CSI_IGNORE) // Unexpected private
        }
    }

    private fun handleCsiInter(b: Byte, code: Int) {
        when {
            isFinalByte(code) -> {
                dispatchCsi(code.toChar())
                transitionTo(ParseState.GROUND)
            }
            code in 0x20..0x2F -> {
                if (intermediateCount < intermediates.size) intermediates[intermediateCount++] = b
            }
            else -> transitionTo(ParseState.CSI_IGNORE)
        }
    }

    private fun handleCsiIgnore(b: Byte, code: Int) {
        if (isFinalByte(code)) {
            Log.d(TAG, "Ignoring overflow CSI sequence, final byte: ${code.toChar()}")
            transitionTo(ParseState.GROUND)
        }
    }

    private fun handleOsc(b: Byte, code: Int) {
        when {
            code == 0x07 || (code == 0x5C && oscBuf.endsWith('\u001B')) -> {
                // BEL or ST (\e\\) terminates OSC
                if (oscBuf.endsWith('\u001B')) oscBuf.deleteCharAt(oscBuf.length - 1)
                dispatchOsc()
                transitionTo(ParseState.GROUND)
            }
            code == 0x9C -> { // C1 ST
                dispatchOsc()
                transitionTo(ParseState.GROUND)
            }
            oscBuf.length < MAX_OSC_LEN -> oscBuf.append(code.toChar())
            // else: silently drop overflow
        }
    }

    private fun handleDcs(b: Byte, code: Int) {
        // DCS passthrough — minimal implementation, just look for ST
        if (code == 0x9C || (code == 0x5C && state == ParseState.DCS_PASSTHROUGH)) {
            callback.unhook()
            transitionTo(ParseState.GROUND)
        } else if (state == ParseState.DCS_ENTRY && isFinalByte(code)) {
            callback.hook(params, paramCount, intermediates, code.toChar())
            transitionTo(ParseState.DCS_PASSTHROUGH)
        } else if (state == ParseState.DCS_PASSTHROUGH) {
            callback.put(b)
        }
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    private fun isFinalByte(code: Int): Boolean = code in 0x40..0x7E

    private fun flushParam() {
        if (paramCount < MAX_PARAMS) {
            params[paramCount++] = if (hasCurrentParam) currentParam else -1 // -1 = omitted/default
        }
        currentParam = 0
        hasCurrentParam = false
    }

    private fun resetCsiParams() {
        paramCount = 0
        currentParam = 0
        hasCurrentParam = false
        intermediateCount = 0
        for (i in intermediates.indices) intermediates[i] = 0
    }

    private fun dispatchCsi(action: Char) {
        callback.csiDispatch(
            params.copyOf(paramCount),
            paramCount,
            intermediates.copyOf(intermediateCount),
            action
        )
    }

    private fun dispatchOsc() {
        val text = oscBuf.toString()
        val semicolonIdx = text.indexOf(';')
        val command = if (semicolonIdx > 0) text.substring(0, semicolonIdx).toIntOrNull() ?: -1 else text.toIntOrNull() ?: -1
        val data = if (semicolonIdx >= 0) text.substring(semicolonIdx + 1) else ""
        callback.oscDispatch(command, data)
    }

    private fun transitionTo(newState: ParseState) {
        if (newState == ParseState.ESCAPE ||
            newState == ParseState.CSI_ENTRY ||
            newState == ParseState.OSC_STRING ||
            newState == ParseState.DCS_ENTRY) {
            resetCsiParams()
        }
        state = newState
    }
}
