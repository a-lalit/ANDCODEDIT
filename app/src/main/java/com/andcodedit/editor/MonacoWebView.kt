package com.andcodedit.editor

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Controller for the Monaco editor hosted in a [WebView]. All methods marshal
 * their arguments safely into JS and invoke the page's `window.AndCodeMonaco`
 * API via [WebView.evaluateJavascript].
 *
 * Obtain an instance via [rememberMonacoController] and pass it to
 * [MonacoEditor].
 */
class MonacoController {
    internal var webView: WebView? = null

    private fun eval(script: String) {
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    /** JSON-encode a string so it can be embedded safely in a JS call. */
    private fun quote(s: String): String = JSONObject.quote(s)

    fun setValue(text: String) {
        eval("AndCodeMonaco.setValue(${quote(text)});")
    }

    /**
     * Asynchronously reads the current editor content. The [callback] is
     * invoked on the UI thread with the decoded string.
     */
    fun getValue(callback: (String) -> Unit) {
        val wv = webView ?: run { callback(""); return }
        wv.post {
            wv.evaluateJavascript("AndCodeMonaco.getValue();") { raw ->
                callback(decodeJsString(raw))
            }
        }
    }

    fun setLanguage(language: String) {
        eval("AndCodeMonaco.setLanguage(${quote(language)});")
    }

    fun setTheme(theme: String) {
        eval("AndCodeMonaco.setTheme(${quote(theme)});")
    }

    fun setFontSize(px: Int) {
        eval("AndCodeMonaco.setFontSize($px);")
    }

    fun insert(text: String) {
        eval("AndCodeMonaco.insert(${quote(text)});")
    }

    fun goToLine(line: Int) {
        eval("AndCodeMonaco.goToLine($line);")
    }

    fun format() {
        eval("AndCodeMonaco.format();")
    }

    fun undo() {
        eval("AndCodeMonaco.undo();")
    }

    fun redo() {
        eval("AndCodeMonaco.redo();")
    }

    /** `evaluateJavascript` returns a JSON-encoded value; decode it to text. */
    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            // Wrap so JSONObject can parse a bare JSON string value.
            JSONObject("{\"v\":$raw}").getString("v")
        } catch (e: Exception) {
            // Fallback: strip surrounding quotes if present.
            raw.trim().removeSurrounding("\"")
        }
    }
}

@Composable
fun rememberMonacoController(): MonacoController = remember { MonacoController() }

/**
 * Composable wrapping a [WebView] that hosts the Monaco editor from
 * `file:///android_asset/monaco/editor.html`.
 *
 * Two-way bridge:
 *  - JS -> Kotlin via the `AndCodeBridge` JavascriptInterface
 *    ([onContentChange], [onCursor], readiness).
 *  - Kotlin -> JS via [controller].
 *
 * The [initialText] / [language] are pushed into the editor once it signals
 * readiness through `onEditorReady()`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoEditor(
    initialText: String,
    language: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: MonacoController = rememberMonacoController(),
    onCursor: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    // Bridge object exposed to JS. Methods may be invoked on a non-UI thread,
    // so callbacks are marshalled back onto the WebView's thread.
    val bridge = remember(controller) {
        object {
            @JavascriptInterface
            fun onEditorReady() {
                val wv = controller.webView ?: return
                val text = JSONObject.quote(initialText)
                val lang = JSONObject.quote(language)
                wv.post {
                    wv.evaluateJavascript("window.__initEditor($text, $lang);", null)
                }
            }

            @JavascriptInterface
            fun onContentChanged(value: String) {
                val wv = controller.webView
                if (wv != null) {
                    wv.post { onContentChange(value) }
                } else {
                    onContentChange(value)
                }
            }

            @JavascriptInterface
            fun onCursor(line: Int, col: Int) {
                val wv = controller.webView
                if (wv != null) {
                    wv.post { onCursor(line, col) }
                } else {
                    onCursor(line, col)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true
                webViewClient = WebViewClient()
                addJavascriptInterface(bridge, "AndCodeBridge")
                controller.webView = this
                loadUrl("file:///android_asset/monaco/editor.html")
            }
        },
        update = { webView ->
            controller.webView = webView
        }
    )
}
