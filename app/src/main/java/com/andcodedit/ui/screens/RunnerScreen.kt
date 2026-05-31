package com.andcodedit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.andcodedit.lang.CodeRunner
import com.andcodedit.lang.InProcessRunner
import com.andcodedit.lang.Language
import com.andcodedit.lang.LanguageCategory
import com.andcodedit.lang.LanguageRegistry
import com.andcodedit.lang.RunEvent
import com.andcodedit.lang.RuntimeManager
import com.andcodedit.viewmodel.AppStateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A single line shown in the output console, tagged with how it should render. */
private data class ConsoleLine(val text: String, val kind: Kind) {
    enum class Kind { Stdout, Stderr, System }
}

/** A tiny hello-world sample per category, used to seed the editor. */
private fun sampleFor(language: Language): String = when (language.id) {
    "python" -> "print(\"Hello, World!\")\n"
    "javascript" -> "console.log(\"Hello, World!\");\n"
    "typescript" -> "const msg: string = \"Hello, World!\";\nconsole.log(msg);\n"
    "ruby" -> "puts \"Hello, World!\"\n"
    "php" -> "<?php\necho \"Hello, World!\\n\";\n"
    "perl" -> "print \"Hello, World!\\n\";\n"
    "lua" -> "print(\"Hello, World!\")\n"
    "r" -> "cat(\"Hello, World!\\n\")\n"
    "julia" -> "println(\"Hello, World!\")\n"
    "c" -> "#include <stdio.h>\nint main(void) {\n    printf(\"Hello, World!\\n\");\n    return 0;\n}\n"
    "cpp" -> "#include <iostream>\nint main() {\n    std::cout << \"Hello, World!\" << std::endl;\n    return 0;\n}\n"
    "go" -> "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hello, World!\")\n}\n"
    "rust" -> "fn main() {\n    println!(\"Hello, World!\");\n}\n"
    "swift" -> "print(\"Hello, World!\")\n"
    "nim" -> "echo \"Hello, World!\"\n"
    "zig" -> "const std = @import(\"std\");\npub fn main() void {\n    std.debug.print(\"Hello, World!\\n\", .{});\n}\n"
    "crystal" -> "puts \"Hello, World!\"\n"
    "fortran" -> "program hello\n    print *, \"Hello, World!\"\nend program hello\n"
    "java" -> "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}\n"
    "kotlin" -> "fun main() {\n    println(\"Hello, World!\")\n}\n"
    "scala" -> "object Main extends App {\n    println(\"Hello, World!\")\n}\n"
    "groovy" -> "println \"Hello, World!\"\n"
    "clojure" -> "(println \"Hello, World!\")\n"
    "csharp" -> "using System;\nclass Main {\n    static void Main() {\n        Console.WriteLine(\"Hello, World!\");\n    }\n}\n"
    "haskell" -> "main :: IO ()\nmain = putStrLn \"Hello, World!\"\n"
    "elixir" -> "IO.puts(\"Hello, World!\")\n"
    "ocaml" -> "print_endline \"Hello, World!\"\n"
    "dart" -> "void main() {\n    print(\"Hello, World!\");\n}\n"
    "bash" -> "echo \"Hello, World!\"\n"
    "beanshell" -> "print(\"Hello, World!\");\n"
    "sql" -> "SELECT 'Hello, World!';\n"
    else -> when (language.category) {
        LanguageCategory.Shell -> "echo \"Hello, World!\"\n"
        else -> "// Hello, World!\n"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerScreen(navController: NavController, appStateViewModel: AppStateViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { CodeRunner() }

    val languages = remember { LanguageRegistry.all }
    var selectedLanguage by remember { mutableStateOf(languages.first()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var code by remember { mutableStateOf(sampleFor(selectedLanguage)) }
    val output = remember { mutableStateListOf<ConsoleLine>() }

    var isRunning by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }

    // Availability re-checked whenever the selected language changes.
    // In-process languages (Rhino/Jython/LuaJ/BeanShell) are always available —
    // their interpreters are bundled in the APK, so no toolchain is required.
    val inProcess = InProcessRunner.canRun(selectedLanguage.id)
    var available by remember { mutableStateOf(true) }
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(selectedLanguage.id) {
        if (inProcess) {
            available = true
            missing = emptyList()
        } else {
            available = RuntimeManager.isAvailable(selectedLanguage)
            missing = RuntimeManager.missingBinaries(selectedLanguage)
        }
    }

    val outputScroll = rememberScrollState()
    LaunchedEffect(output.size) {
        outputScroll.animateScrollTo(outputScroll.maxValue)
    }

    fun selectLanguage(lang: Language) {
        // Only replace the editor contents if the user hasn't diverged from the sample.
        if (code.isBlank() || code == sampleFor(selectedLanguage)) {
            code = sampleFor(lang)
        }
        selectedLanguage = lang
    }

    fun startRun() {
        if (isRunning) return
        output.clear()
        isRunning = true

        // In-process languages execute on a bundled interpreter — no shell.
        if (InProcessRunner.canRun(selectedLanguage.id)) {
            output.add(ConsoleLine("▶ ${selectedLanguage.displayName} (in-process, bundled)", ConsoleLine.Kind.System))
            runJob = scope.launch {
                val result = withContext(Dispatchers.Default) {
                    InProcessRunner.run(selectedLanguage.id, code)
                }
                result.output.lineSequence().forEach {
                    if (it.isNotEmpty()) output.add(ConsoleLine(it, ConsoleLine.Kind.Stdout))
                }
                if (result.error.isNotBlank()) {
                    result.error.lineSequence().forEach {
                        if (it.isNotEmpty()) output.add(ConsoleLine(it, ConsoleLine.Kind.Stderr))
                    }
                }
                output.add(
                    ConsoleLine(
                        "[${if (result.ok) "ok" else "error"} • ${result.ms} ms]",
                        ConsoleLine.Kind.System
                    )
                )
                isRunning = false
            }
            return
        }

        val workDir = File(context.cacheDir, "runner").apply { mkdirs() }
        runJob = scope.launch {
            runner.run(selectedLanguage, code, workDir, this)
                .catch { e ->
                    output.add(ConsoleLine("Runner error: ${e.message}", ConsoleLine.Kind.Stderr))
                }
                .collect { event ->
                    when (event) {
                        is RunEvent.Started ->
                            output.add(ConsoleLine("$ ${event.cmd}", ConsoleLine.Kind.System))
                        is RunEvent.Stdout ->
                            output.add(ConsoleLine(event.line, ConsoleLine.Kind.Stdout))
                        is RunEvent.Stderr ->
                            output.add(ConsoleLine(event.line, ConsoleLine.Kind.Stderr))
                        is RunEvent.Exit -> {
                            output.add(
                                ConsoleLine(
                                    "[exit ${event.code} • ${event.ms} ms]",
                                    ConsoleLine.Kind.System
                                )
                            )
                            isRunning = false
                        }
                    }
                }
            isRunning = false
        }
    }

    fun stopRun() {
        runJob?.cancel()
        runJob = null
        isRunning = false
        output.add(ConsoleLine("[cancelled]", ConsoleLine.Kind.System))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Code") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Language picker + Run controls ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedLanguage.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.displayName}  (.${lang.fileExtension})") },
                                onClick = {
                                    selectLanguage(lang)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isRunning) {
                    Button(onClick = { stopRun() }) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = { startRun() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Run")
                    }
                }
            }

            // ---- Bundled (in-process) indicator ----
            if (inProcess) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Runs in-process — interpreter bundled in the app, no toolchain needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ---- Availability banner ----
            if (!inProcess && !available) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Toolchain not found",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Missing: ${missing.joinToString(", ").ifEmpty { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                RuntimeManager.installCommand(selectedLanguage),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ---- Code editor ----
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Source (Main.${selectedLanguage.fileExtension})") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // ---- Output console ----
            Text(
                "Output",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = Color(0xFF1E1E1E),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .verticalScroll(outputScroll)
                        .padding(8.dp)
                ) {
                    if (output.isEmpty()) {
                        Text(
                            "Press Run to execute. Output appears here.",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    output.forEach { line ->
                        Text(
                            text = line.text,
                            color = when (line.kind) {
                                ConsoleLine.Kind.Stdout -> Color.White
                                ConsoleLine.Kind.Stderr -> Color(0xFFFF6B6B)
                                ConsoleLine.Kind.System -> Color(0xFF6CB6FF)
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
