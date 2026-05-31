package com.andcodedit.lang

import java.io.PrintStream

/**
 * InProcessRunner — executes a subset of languages **entirely inside the app**,
 * using interpreters bundled in the APK. No Termux, no shell, no external
 * toolchain: these run on the JVM/ART in-process.
 *
 * Supported in-process engines (pure interpreters that emit no JVM bytecode, so
 * they run on Android/ART without a desktop JVM):
 *  - **JavaScript** via Mozilla Rhino (`org.mozilla:rhino`, interpreted mode)
 *  - **Lua 5.2** via LuaJ (`org.luaj:luaj-jse`)
 *  - **BeanShell** (Java-like scripting) via `bsh`
 *
 * These give the app real, offline, zero-setup execution for several languages,
 * complementing the shell-based [CodeRunner] that handles compiled/native
 * toolchains provisioned through Termux. (Python, Ruby, Groovy etc. are not
 * bundled in-process: their JVM engines generate bytecode at runtime, which ART
 * cannot load — they run via the Termux toolchain instead.)
 */
object InProcessRunner {

    /**
     * Language ids this runner can execute without any external toolchain.
     * Pure interpreters only (no runtime bytecode generation) so they work on
     * Android/ART: JavaScript (Rhino), Lua (LuaJ), BeanShell.
     */
    val supportedIds: Set<String> = setOf("javascript", "lua", "beanshell")

    fun canRun(languageId: String): Boolean = languageId in supportedIds

    data class Result(val output: String, val error: String, val ok: Boolean, val ms: Long)

    /**
     * Run [code] for [languageId] in-process. Standard output/print is captured
     * and returned; exceptions become the error text.
     */
    fun run(languageId: String, code: String): Result {
        val started = System.currentTimeMillis()
        return try {
            when (languageId) {
                "javascript" -> runJavaScript(code)
                "lua" -> runLua(code)
                "beanshell" -> runBeanShell(code)
                else -> Result("", "In-process execution not supported for '$languageId'.", false, 0)
            }.let { it.copy(ms = System.currentTimeMillis() - started) }
        } catch (t: Throwable) {
            Result("", t.message ?: t.toString(), false, System.currentTimeMillis() - started)
        }
    }

    // ---- JavaScript (Rhino) ----
    private fun runJavaScript(code: String): Result {
        val out = StringBuilder()
        val cx = org.mozilla.javascript.Context.enter()
        return try {
            // Android cannot generate bytecode at runtime: force interpreted mode.
            cx.optimizationLevel = -1
            val scope = cx.initStandardObjects()
            // Provide a console.log / print that appends to our buffer.
            val sink = JsConsole(out)
            org.mozilla.javascript.ScriptableObject.putProperty(
                scope, "__sink", org.mozilla.javascript.Context.javaToJS(sink, scope)
            )
            cx.evaluateString(
                scope,
                "var console={log:function(){__sink.log(Array.prototype.slice.call(arguments).join(' '));}};" +
                    "function print(){__sink.log(Array.prototype.slice.call(arguments).join(' '));}",
                "<bootstrap>", 1, null
            )
            val result = cx.evaluateString(scope, code, "<script>", 1, null)
            if (out.isEmpty() && result != null &&
                result != org.mozilla.javascript.Undefined.instance
            ) {
                out.append(org.mozilla.javascript.Context.toString(result))
            }
            Result(out.toString(), "", true, 0)
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    /** Public so Rhino's reflection-based Java access can reach [log]. */
    class JsConsole(private val sb: StringBuilder) {
        fun log(s: String) { sb.append(s).append('\n') }
    }

    // ---- Lua (LuaJ) ----
    private fun runLua(code: String): Result {
        val globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
        val out = java.io.ByteArrayOutputStream()
        globals.STDOUT = PrintStream(out, true, "UTF-8")
        val chunk = globals.load(code, "<script>")
        chunk.call()
        return Result(out.toString("UTF-8"), "", true, 0)
    }

    // ---- BeanShell ----
    private fun runBeanShell(code: String): Result {
        val out = java.io.ByteArrayOutputStream()
        val ps = PrintStream(out, true, "UTF-8")
        val interp = bsh.Interpreter(java.io.StringReader(""), ps, ps, false)
        interp.eval(code)
        return Result(out.toString("UTF-8"), "", true, 0)
    }
}
