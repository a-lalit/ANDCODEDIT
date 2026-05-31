package com.andcodedit.lang

/**
 * Broad family a [Language] belongs to. Used by the UI to group languages and to
 * pick a sensible hello-world sample when none is otherwise supplied.
 */
enum class LanguageCategory {
    Scripting,
    Compiled,
    JVM,
    Web,
    Functional,
    Shell,
    Data
}

/**
 * Describes a single programming language the runner can execute on the
 * on-device shell (Termux / proot toolchains).
 *
 * Command templates use the following placeholders, substituted by [CodeRunner]
 * before execution:
 *
 *  - `{file}` — absolute path to the written source file (e.g. `/.../Main.py`)
 *  - `{dir}`  — absolute path to the working directory
 *  - `{name}` — basename of the source file without its extension (e.g. `Main`)
 *  - `{out}`  — absolute path to a compiled output binary (`{dir}/{name}` by convention)
 *
 * @property id              stable lowercase identifier (also used by Monaco where possible)
 * @property displayName     human readable name shown in the UI
 * @property fileExtension   extension (no dot) used when writing the source file
 * @property monacoId        Monaco editor language id for syntax highlighting
 * @property category        broad grouping, see [LanguageCategory]
 * @property interpreter     primary interpreter binary, or null for purely compiled languages
 * @property compileTemplate optional compile command; when non-null it runs before [runTemplate]
 * @property runTemplate     command that executes the program (after compilation if any)
 * @property requiredBinaries shell binaries that must be present for this language to work
 * @property installHint     Termux `pkg install ...` line that provisions the toolchain
 */
data class Language(
    val id: String,
    val displayName: String,
    val fileExtension: String,
    val monacoId: String,
    val category: LanguageCategory,
    val interpreter: String?,
    val compileTemplate: String?,
    val runTemplate: String,
    val requiredBinaries: List<String>,
    val installHint: String
)
