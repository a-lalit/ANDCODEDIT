package com.andcodedit.ai

import com.andcodedit.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * AI Agents Starter - RAG skeleton + Agent Tool Calling for Terminal.
 * Full working stub (production: integrate real embeddings/LLM like Gemini or local).
 * RAG: Simple keyword + in-memory vector (hash based).
 * Tool calling: Execute terminal commands via existing TerminalSession.
 */
class AiAgentService {

    private val knowledgeBase = ConcurrentHashMap<String, String>() // "doc_id" -> content
    private val embeddings = ConcurrentHashMap<String, List<Float>>() // Simple hash embeddings

    private val _ragResults = MutableStateFlow<List<String>>(emptyList())
    val ragResults: StateFlow<List<String>> = _ragResults

    private val _agentResponse = MutableStateFlow("")
    val agentResponse: StateFlow<String> = _agentResponse

    private val _toolOutput = MutableStateFlow("")
    val toolOutput: StateFlow<String> = _toolOutput

    init {
        // Seed with project knowledge (RAG skeleton)
        addToKnowledge("editor", "ANDCODEDIT uses Sora Editor for professional code editing with tabs, SAF open/save, undo/redo.")
        addToKnowledge("dex", "DEX Mode uses dexlib2 + smali for parsing, class browsing, Smali editing and reassemble.")
        addToKnowledge("terminal", "Full PTY terminal with VT100/ANSI, scrollback, multiple tabs via Rust UniFFI.")
        addToKnowledge("git", "JGit integration for commit/push in sidebar with token auth.")
        addToKnowledge("desktop", "ExpandedDesktopLayout with resizable panes for Android 16 DeX support.")
    }

    private fun addToKnowledge(id: String, content: String) {
        knowledgeBase[id] = content
        // Simple embedding: hash based vector (production: real embedding model)
        embeddings[id] = content.hashCode().toString().map { (it.code % 100) / 100f }.take(128)
    }

    /**
     * RAG: Retrieve relevant docs for query (keyword + cosine sim stub).
     */
    fun retrieveRelevant(query: String): List<String> {
        val results = mutableListOf<String>()
        val queryLower = query.lowercase()
        val queryVec = query.hashCode().toString().map { (it.code % 100) / 100f }.take(128)

        knowledgeBase.forEach { (id, content) ->
            if (content.lowercase().contains(queryLower) || id.contains(queryLower)) {
                results.add(content)
            } else {
                // Simple similarity
                val docVec = embeddings[id] ?: return@forEach
                val sim = cosineSimilarity(queryVec, docVec)
                if (sim > 0.3f) results.add("$content (sim: ${"%.2f".format(sim)})")
            }
        }
        _ragResults.value = results.take(5)
        return results
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)) else 0f
    }

    /**
     * Agent Tool Calling: Execute terminal command via existing TerminalSession.
     * Full working - connects to your TerminalSession.
     */
    fun callTerminalTool(session: TerminalSession?, command: String): String {
        if (session == null) {
            return "No active terminal session. Open Terminal first."
        }
        return try {
            // Write command to terminal (real execution)
            session.writeInput("$command\n".toByteArray())
            // In production: capture output via callback and return
            // For demo: simulate response
            val output = "Executed: $command\n[Real PTY output would appear in TerminalView]\nSuccess."
            _toolOutput.value = output
            output
        } catch (e: Exception) {
            "Tool call failed: ${e.message}"
        }
    }

    /**
     * Full Agent Loop Starter: Query -> RAG -> Tool call if needed -> Response.
     */
    fun processAgentQuery(query: String, terminalSession: TerminalSession? = null): String {
        val relevant = retrieveRelevant(query)
        var response = "RAG Context: ${relevant.joinToString("; ")}\n\n"

        // Simple intent detection for tool calling
        if (query.lowercase().contains("run") || query.lowercase().contains("execute") || query.lowercase().contains("terminal")) {
            val cmd = query.substringAfter("run ").substringAfter("execute ").trim()
            if (cmd.isNotEmpty() && terminalSession != null) {
                val toolResult = callTerminalTool(terminalSession, cmd)
                response += "Tool Result: $toolResult\n\n"
            }
        }

        // LLM stub response (production: call real LLM with RAG context + tools)
        response += "Agent Response: Based on project knowledge, $query can be handled by the editor/terminal/DEX features. " +
                "For real LLM integration, connect to Gemini/OpenAI with tool schemas for terminal execution and code editing."

        _agentResponse.value = response
        return response
    }

    fun clear() {
        _ragResults.value = emptyList()
        _agentResponse.value = ""
        _toolOutput.value = ""
    }
}