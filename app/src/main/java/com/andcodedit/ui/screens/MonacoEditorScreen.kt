package com.andcodedit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.andcodedit.editor.MonacoEditor
import com.andcodedit.editor.rememberMonacoController
import com.andcodedit.viewmodel.AppStateViewModel

/** Languages offered in the editor's language picker. */
private val MONACO_LANGUAGES = listOf(
    "kotlin", "java", "javascript", "typescript", "python",
    "c", "cpp", "csharp", "go", "rust", "ruby", "php",
    "swift", "dart", "scala", "html", "css", "json",
    "yaml", "markdown", "sql", "shell", "lua", "r", "perl"
)

/** A friendly hello-world snippet for the given Monaco language id. */
private fun helloWorldFor(language: String): String = when (language) {
    "kotlin" -> "fun main() {\n    println(\"Hello, World!\")\n}\n"
    "java" -> "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}\n"
    "javascript" -> "console.log(\"Hello, World!\");\n"
    "typescript" -> "const greeting: string = \"Hello, World!\";\nconsole.log(greeting);\n"
    "python" -> "def main():\n    print(\"Hello, World!\")\n\n\nif __name__ == \"__main__\":\n    main()\n"
    "c" -> "#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello, World!\\n\");\n    return 0;\n}\n"
    "cpp" -> "#include <iostream>\n\nint main() {\n    std::cout << \"Hello, World!\" << std::endl;\n    return 0;\n}\n"
    "csharp" -> "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"Hello, World!\");\n    }\n}\n"
    "go" -> "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hello, World!\")\n}\n"
    "rust" -> "fn main() {\n    println!(\"Hello, World!\");\n}\n"
    "ruby" -> "puts \"Hello, World!\"\n"
    "php" -> "<?php\necho \"Hello, World!\\n\";\n"
    "swift" -> "import Foundation\n\nprint(\"Hello, World!\")\n"
    "dart" -> "void main() {\n  print('Hello, World!');\n}\n"
    "scala" -> "object Main extends App {\n  println(\"Hello, World!\")\n}\n"
    "html" -> "<!DOCTYPE html>\n<html>\n  <body>\n    <h1>Hello, World!</h1>\n  </body>\n</html>\n"
    "css" -> "body {\n  font-family: sans-serif;\n  color: #fff;\n  background: #1e1e1e;\n}\n"
    "json" -> "{\n  \"message\": \"Hello, World!\"\n}\n"
    "yaml" -> "message: Hello, World!\n"
    "markdown" -> "# Hello, World!\n\nWelcome to **AndCode**.\n"
    "sql" -> "SELECT 'Hello, World!' AS greeting;\n"
    "shell" -> "#!/usr/bin/env bash\necho \"Hello, World!\"\n"
    "lua" -> "print(\"Hello, World!\")\n"
    "r" -> "print(\"Hello, World!\")\n"
    "perl" -> "#!/usr/bin/perl\nprint \"Hello, World!\\n\";\n"
    else -> "Hello, World!\n"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonacoEditorScreen(
    navController: NavController,
    appStateViewModel: AppStateViewModel
) {
    val controller = rememberMonacoController()

    var language by remember { mutableStateOf("kotlin") }
    var content by remember { mutableStateOf(helloWorldFor("kotlin")) }
    var languageMenuOpen by remember { mutableStateOf(false) }

    var cursorLine by remember { mutableIntStateOf(1) }
    var cursorCol by remember { mutableIntStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monaco") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Language picker
                    Box {
                        TextButton(onClick = { languageMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null
                            )
                            Text("  $language")
                        }
                        DropdownMenu(
                            expanded = languageMenuOpen,
                            onDismissRequest = { languageMenuOpen = false }
                        ) {
                            MONACO_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        languageMenuOpen = false
                                        if (lang != language) {
                                            language = lang
                                            val fresh = helloWorldFor(lang)
                                            content = fresh
                                            controller.setLanguage(lang)
                                            controller.setValue(fresh)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // Format action
                    TextButton(onClick = { controller.format() }) {
                        Text("Format")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // The runner screen is wired separately; guard against an
                // unregistered route so this is safe in isolation.
                runCatching { navController.navigate("runner") }
            }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Run"
                )
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Ln $cursorLine, Col $cursorCol",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MonacoEditor(
                initialText = content,
                language = language,
                onContentChange = { content = it },
                onCursor = { line, col ->
                    cursorLine = line
                    cursorCol = col
                },
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
