package com.andcodedit.lang

/**
 * Central catalogue of every language ANDCODEDIT can execute through the
 * on-device shell. Each entry carries realistic compile/run commands as they
 * would run inside a Termux / proot Linux environment.
 *
 * Command templates use the placeholders documented on [Language]:
 * `{file}`, `{dir}`, `{name}`, `{out}`.
 */
object LanguageRegistry {

    val all: List<Language> = listOf(
        // ---- Scripting ----
        Language(
            id = "python",
            displayName = "Python",
            fileExtension = "py",
            monacoId = "python",
            category = LanguageCategory.Scripting,
            interpreter = "python",
            compileTemplate = null,
            runTemplate = "python {file}",
            requiredBinaries = listOf("python"),
            installHint = "pkg install python"
        ),
        Language(
            id = "javascript",
            displayName = "JavaScript (Node)",
            fileExtension = "js",
            monacoId = "javascript",
            category = LanguageCategory.Scripting,
            interpreter = "node",
            compileTemplate = null,
            runTemplate = "node {file}",
            requiredBinaries = listOf("node"),
            installHint = "pkg install nodejs"
        ),
        Language(
            id = "typescript",
            displayName = "TypeScript",
            fileExtension = "ts",
            monacoId = "typescript",
            category = LanguageCategory.Scripting,
            interpreter = "ts-node",
            compileTemplate = null,
            runTemplate = "ts-node {file}",
            requiredBinaries = listOf("ts-node", "node"),
            installHint = "pkg install nodejs && npm install -g ts-node typescript"
        ),
        Language(
            id = "ruby",
            displayName = "Ruby",
            fileExtension = "rb",
            monacoId = "ruby",
            category = LanguageCategory.Scripting,
            interpreter = "ruby",
            compileTemplate = null,
            runTemplate = "ruby {file}",
            requiredBinaries = listOf("ruby"),
            installHint = "pkg install ruby"
        ),
        Language(
            id = "php",
            displayName = "PHP",
            fileExtension = "php",
            monacoId = "php",
            category = LanguageCategory.Scripting,
            interpreter = "php",
            compileTemplate = null,
            runTemplate = "php {file}",
            requiredBinaries = listOf("php"),
            installHint = "pkg install php"
        ),
        Language(
            id = "perl",
            displayName = "Perl",
            fileExtension = "pl",
            monacoId = "perl",
            category = LanguageCategory.Scripting,
            interpreter = "perl",
            compileTemplate = null,
            runTemplate = "perl {file}",
            requiredBinaries = listOf("perl"),
            installHint = "pkg install perl"
        ),
        Language(
            id = "lua",
            displayName = "Lua",
            fileExtension = "lua",
            monacoId = "lua",
            category = LanguageCategory.Scripting,
            interpreter = "lua",
            compileTemplate = null,
            runTemplate = "lua {file}",
            requiredBinaries = listOf("lua"),
            installHint = "pkg install lua54"
        ),
        Language(
            id = "r",
            displayName = "R",
            fileExtension = "R",
            monacoId = "r",
            category = LanguageCategory.Data,
            interpreter = "Rscript",
            compileTemplate = null,
            runTemplate = "Rscript {file}",
            requiredBinaries = listOf("Rscript"),
            installHint = "pkg install r-base"
        ),
        Language(
            id = "julia",
            displayName = "Julia",
            fileExtension = "jl",
            monacoId = "julia",
            category = LanguageCategory.Data,
            interpreter = "julia",
            compileTemplate = null,
            runTemplate = "julia {file}",
            requiredBinaries = listOf("julia"),
            installHint = "pkg install julia"
        ),

        // ---- Compiled (native) ----
        Language(
            id = "c",
            displayName = "C",
            fileExtension = "c",
            monacoId = "c",
            category = LanguageCategory.Compiled,
            interpreter = null,
            compileTemplate = "clang {file} -o {out}",
            runTemplate = "{out}",
            requiredBinaries = listOf("clang"),
            installHint = "pkg install clang"
        ),
        Language(
            id = "cpp",
            displayName = "C++",
            fileExtension = "cpp",
            monacoId = "cpp",
            category = LanguageCategory.Compiled,
            interpreter = null,
            compileTemplate = "clang++ -std=c++17 {file} -o {out}",
            runTemplate = "{out}",
            requiredBinaries = listOf("clang++"),
            installHint = "pkg install clang"
        ),
        Language(
            id = "go",
            displayName = "Go",
            fileExtension = "go",
            monacoId = "go",
            category = LanguageCategory.Compiled,
            interpreter = "go",
            compileTemplate = null,
            runTemplate = "go run {file}",
            requiredBinaries = listOf("go"),
            installHint = "pkg install golang"
        ),
        Language(
            id = "rust",
            displayName = "Rust",
            fileExtension = "rs",
            monacoId = "rust",
            category = LanguageCategory.Compiled,
            interpreter = null,
            compileTemplate = "rustc {file} -o {out}",
            runTemplate = "{out}",
            requiredBinaries = listOf("rustc"),
            installHint = "pkg install rust"
        ),
        Language(
            id = "swift",
            displayName = "Swift",
            fileExtension = "swift",
            monacoId = "swift",
            category = LanguageCategory.Compiled,
            interpreter = "swift",
            compileTemplate = null,
            runTemplate = "swift {file}",
            requiredBinaries = listOf("swift"),
            installHint = "pkg install swift"
        ),
        Language(
            id = "nim",
            displayName = "Nim",
            fileExtension = "nim",
            monacoId = "nim",
            category = LanguageCategory.Compiled,
            interpreter = "nim",
            compileTemplate = null,
            runTemplate = "nim compile --run {file}",
            requiredBinaries = listOf("nim"),
            installHint = "pkg install nim"
        ),
        Language(
            id = "zig",
            displayName = "Zig",
            fileExtension = "zig",
            monacoId = "zig",
            category = LanguageCategory.Compiled,
            interpreter = "zig",
            compileTemplate = null,
            runTemplate = "zig run {file}",
            requiredBinaries = listOf("zig"),
            installHint = "pkg install zig"
        ),
        Language(
            id = "crystal",
            displayName = "Crystal",
            fileExtension = "cr",
            monacoId = "crystal",
            category = LanguageCategory.Compiled,
            interpreter = "crystal",
            compileTemplate = null,
            runTemplate = "crystal {file}",
            requiredBinaries = listOf("crystal"),
            installHint = "pkg install crystal"
        ),
        Language(
            id = "fortran",
            displayName = "Fortran",
            fileExtension = "f90",
            monacoId = "fortran",
            category = LanguageCategory.Compiled,
            interpreter = null,
            compileTemplate = "gfortran {file} -o {out}",
            runTemplate = "{out}",
            requiredBinaries = listOf("gfortran"),
            installHint = "pkg install gfortran"
        ),

        // ---- JVM ----
        Language(
            id = "java",
            displayName = "Java",
            fileExtension = "java",
            monacoId = "java",
            category = LanguageCategory.JVM,
            interpreter = "java",
            compileTemplate = "javac {file}",
            runTemplate = "java -cp {dir} {name}",
            requiredBinaries = listOf("javac", "java"),
            installHint = "pkg install openjdk-17"
        ),
        Language(
            id = "kotlin",
            displayName = "Kotlin",
            fileExtension = "kt",
            monacoId = "kotlin",
            category = LanguageCategory.JVM,
            interpreter = "kotlinc",
            compileTemplate = "kotlinc {file} -include-runtime -d {dir}/{name}.jar",
            runTemplate = "java -jar {dir}/{name}.jar",
            requiredBinaries = listOf("kotlinc", "java"),
            installHint = "pkg install kotlin openjdk-17"
        ),
        Language(
            id = "scala",
            displayName = "Scala",
            fileExtension = "scala",
            monacoId = "scala",
            category = LanguageCategory.JVM,
            interpreter = "scala",
            compileTemplate = null,
            runTemplate = "scala {file}",
            requiredBinaries = listOf("scala", "java"),
            installHint = "pkg install scala openjdk-17"
        ),
        Language(
            id = "groovy",
            displayName = "Groovy",
            fileExtension = "groovy",
            monacoId = "groovy",
            category = LanguageCategory.JVM,
            interpreter = "groovy",
            compileTemplate = null,
            runTemplate = "groovy {file}",
            requiredBinaries = listOf("groovy", "java"),
            installHint = "pkg install groovy openjdk-17"
        ),
        Language(
            id = "clojure",
            displayName = "Clojure",
            fileExtension = "clj",
            monacoId = "clojure",
            category = LanguageCategory.JVM,
            interpreter = "clojure",
            compileTemplate = null,
            runTemplate = "clojure {file}",
            requiredBinaries = listOf("clojure", "java"),
            installHint = "pkg install clojure openjdk-17"
        ),

        // ---- .NET ----
        Language(
            id = "csharp",
            displayName = "C#",
            fileExtension = "cs",
            monacoId = "csharp",
            category = LanguageCategory.Compiled,
            interpreter = "mono",
            compileTemplate = "mcs {file}",
            runTemplate = "mono {dir}/{name}.exe",
            requiredBinaries = listOf("mcs", "mono"),
            installHint = "pkg install mono"
        ),

        // ---- Functional ----
        Language(
            id = "haskell",
            displayName = "Haskell",
            fileExtension = "hs",
            monacoId = "haskell",
            category = LanguageCategory.Functional,
            interpreter = "runghc",
            compileTemplate = null,
            runTemplate = "runghc {file}",
            requiredBinaries = listOf("runghc"),
            installHint = "pkg install ghc"
        ),
        Language(
            id = "elixir",
            displayName = "Elixir",
            fileExtension = "exs",
            monacoId = "elixir",
            category = LanguageCategory.Functional,
            interpreter = "elixir",
            compileTemplate = null,
            runTemplate = "elixir {file}",
            requiredBinaries = listOf("elixir"),
            installHint = "pkg install elixir"
        ),
        Language(
            id = "ocaml",
            displayName = "OCaml",
            fileExtension = "ml",
            monacoId = "ocaml",
            category = LanguageCategory.Functional,
            interpreter = "ocaml",
            compileTemplate = null,
            runTemplate = "ocaml {file}",
            requiredBinaries = listOf("ocaml"),
            installHint = "pkg install ocaml"
        ),

        // ---- Web / other scripting ----
        Language(
            id = "dart",
            displayName = "Dart",
            fileExtension = "dart",
            monacoId = "dart",
            category = LanguageCategory.Web,
            interpreter = "dart",
            compileTemplate = null,
            runTemplate = "dart run {file}",
            requiredBinaries = listOf("dart"),
            installHint = "pkg install dart"
        ),

        // ---- Shell ----
        Language(
            id = "bash",
            displayName = "Bash / Shell",
            fileExtension = "sh",
            monacoId = "shell",
            category = LanguageCategory.Shell,
            interpreter = "bash",
            compileTemplate = null,
            runTemplate = "bash {file}",
            requiredBinaries = listOf("bash"),
            installHint = "pkg install bash"
        ),

        // ---- Data ----
        Language(
            id = "sql",
            displayName = "SQL (SQLite)",
            fileExtension = "sql",
            monacoId = "sql",
            category = LanguageCategory.Data,
            interpreter = "sqlite3",
            compileTemplate = null,
            runTemplate = "sqlite3 :memory: < {file}",
            requiredBinaries = listOf("sqlite3"),
            installHint = "pkg install sqlite"
        )
    )

    private val byIdMap: Map<String, Language> = all.associateBy { it.id }
    private val byExtMap: Map<String, Language> =
        all.associateBy { it.fileExtension.lowercase() }

    /** Looks up a language by its stable [Language.id]. */
    fun byId(id: String): Language? = byIdMap[id.lowercase()]

    /** Looks up a language by file extension (with or without a leading dot). */
    fun byExtension(ext: String): Language? =
        byExtMap[ext.removePrefix(".").lowercase()]

    /**
     * Returns the Monaco editor language id for the given language [id], falling
     * back to `"plaintext"` when the language is unknown.
     */
    fun monacoIdFor(id: String): String = byId(id)?.monacoId ?: "plaintext"
}
