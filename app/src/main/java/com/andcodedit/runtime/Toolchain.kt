package com.andcodedit.runtime

/**
 * Describes a single **installable toolchain package** that can be provisioned
 * on-device (via Termux `pkg` or an `apt` based prefix — see [BootstrapManager]).
 *
 * A toolchain is the unit the user installs. One package frequently enables
 * several [com.andcodedit.lang.Language]s: e.g. the `openjdk-17` package powers
 * Java, Kotlin, Scala, Groovy and Clojure, and `clang` powers both C and C++.
 *
 * The mapping from a package to the language ids it enables is captured by
 * [languages]; the actual binaries it drops onto the `PATH` are listed in
 * [providesBinaries] so the UI can cross-check availability against
 * [com.andcodedit.lang.RuntimeManager].
 *
 * @property id              stable lowercase identifier for this toolchain entry
 * @property displayName     human readable name shown in the UI
 * @property pkgName         the package name as passed to `pkg install` / `apt-get install`
 * @property providesBinaries shell binaries this package makes resolvable on the PATH
 * @property sizeMb          approximate on-disk install size in megabytes (download + unpack)
 * @property languages       ids (see [com.andcodedit.lang.LanguageRegistry]) this package enables
 */
data class Toolchain(
    val id: String,
    val displayName: String,
    val pkgName: String,
    val providesBinaries: List<String>,
    val sizeMb: Int,
    val languages: List<String>
)

/**
 * Static catalogue of the toolchains ANDCODEDIT can provision at runtime.
 *
 * These are **not bundled** in the APK (a single APK cannot ship 30 multi-GB
 * toolchains, and Play caps delivery at ~200 MB). They are installed on demand
 * into app-private storage by [BootstrapManager], mirroring how Termux/Acode
 * provision compilers via a package manager.
 *
 * Package names and sizes track Termux's repository (`pkg install <name>`) and
 * are approximate.
 */
object ToolchainCatalog {

    val all: List<Toolchain> = listOf(
        // ---- Scripting ----
        Toolchain(
            id = "python",
            displayName = "Python 3",
            pkgName = "python",
            providesBinaries = listOf("python", "python3", "pip"),
            sizeMb = 140,
            languages = listOf("python")
        ),
        Toolchain(
            id = "nodejs",
            displayName = "Node.js (JavaScript / TypeScript)",
            pkgName = "nodejs",
            providesBinaries = listOf("node", "npm", "npx"),
            sizeMb = 110,
            // ts-node/typescript come from npm on top of nodejs; one toolchain enables both.
            languages = listOf("javascript", "typescript")
        ),
        Toolchain(
            id = "ruby",
            displayName = "Ruby",
            pkgName = "ruby",
            providesBinaries = listOf("ruby", "gem"),
            sizeMb = 45,
            languages = listOf("ruby")
        ),
        Toolchain(
            id = "php",
            displayName = "PHP",
            pkgName = "php",
            providesBinaries = listOf("php"),
            sizeMb = 60,
            languages = listOf("php")
        ),
        Toolchain(
            id = "perl",
            displayName = "Perl",
            pkgName = "perl",
            providesBinaries = listOf("perl"),
            sizeMb = 35,
            languages = listOf("perl")
        ),
        Toolchain(
            id = "lua",
            displayName = "Lua 5.4",
            pkgName = "lua54",
            providesBinaries = listOf("lua", "lua5.4"),
            sizeMb = 3,
            languages = listOf("lua")
        ),
        Toolchain(
            id = "r-base",
            displayName = "R",
            pkgName = "r-base",
            providesBinaries = listOf("R", "Rscript"),
            sizeMb = 95,
            languages = listOf("r")
        ),
        Toolchain(
            id = "julia",
            displayName = "Julia",
            pkgName = "julia",
            providesBinaries = listOf("julia"),
            sizeMb = 420,
            languages = listOf("julia")
        ),

        // ---- Compiled (native) ----
        Toolchain(
            id = "clang",
            displayName = "Clang / LLVM (C & C++)",
            pkgName = "clang",
            providesBinaries = listOf("clang", "clang++"),
            sizeMb = 180,
            languages = listOf("c", "cpp")
        ),
        Toolchain(
            id = "golang",
            displayName = "Go",
            pkgName = "golang",
            providesBinaries = listOf("go", "gofmt"),
            sizeMb = 340,
            languages = listOf("go")
        ),
        Toolchain(
            id = "rust",
            displayName = "Rust",
            pkgName = "rust",
            providesBinaries = listOf("rustc", "cargo"),
            sizeMb = 290,
            languages = listOf("rust")
        ),
        Toolchain(
            id = "swift",
            displayName = "Swift",
            pkgName = "swift",
            providesBinaries = listOf("swift", "swiftc"),
            sizeMb = 650,
            languages = listOf("swift")
        ),
        Toolchain(
            id = "nim",
            displayName = "Nim",
            pkgName = "nim",
            providesBinaries = listOf("nim", "nimble"),
            sizeMb = 55,
            languages = listOf("nim")
        ),
        Toolchain(
            id = "zig",
            displayName = "Zig",
            pkgName = "zig",
            providesBinaries = listOf("zig"),
            sizeMb = 130,
            languages = listOf("zig")
        ),
        Toolchain(
            id = "crystal",
            displayName = "Crystal",
            pkgName = "crystal",
            providesBinaries = listOf("crystal", "shards"),
            sizeMb = 120,
            languages = listOf("crystal")
        ),
        Toolchain(
            id = "gfortran",
            displayName = "GNU Fortran",
            pkgName = "gfortran",
            providesBinaries = listOf("gfortran"),
            sizeMb = 95,
            languages = listOf("fortran")
        ),

        // ---- JVM ----
        Toolchain(
            id = "openjdk-17",
            displayName = "OpenJDK 17 (Java, Kotlin, Scala, Groovy, Clojure)",
            pkgName = "openjdk-17",
            providesBinaries = listOf("java", "javac"),
            sizeMb = 220,
            // The JVM is the shared runtime; language compilers (kotlinc, scala,
            // groovy, clojure) are layered on top but all require this toolchain.
            languages = listOf("java", "kotlin", "scala", "groovy", "clojure")
        ),

        // ---- .NET ----
        Toolchain(
            id = "mono",
            displayName = "Mono (.NET / C#)",
            pkgName = "mono",
            providesBinaries = listOf("mono", "mcs"),
            sizeMb = 480,
            languages = listOf("csharp")
        ),

        // ---- Functional ----
        Toolchain(
            id = "ghc",
            displayName = "GHC (Haskell)",
            pkgName = "ghc",
            providesBinaries = listOf("ghc", "runghc", "ghci"),
            sizeMb = 950,
            languages = listOf("haskell")
        ),
        Toolchain(
            id = "elixir",
            displayName = "Elixir (Erlang)",
            pkgName = "elixir",
            providesBinaries = listOf("elixir", "iex", "mix"),
            sizeMb = 130,
            languages = listOf("elixir")
        ),
        Toolchain(
            id = "ocaml",
            displayName = "OCaml",
            pkgName = "ocaml",
            providesBinaries = listOf("ocaml", "ocamlc"),
            sizeMb = 90,
            languages = listOf("ocaml")
        ),

        // ---- Web / other scripting ----
        Toolchain(
            id = "dart",
            displayName = "Dart",
            pkgName = "dart",
            providesBinaries = listOf("dart"),
            sizeMb = 410,
            languages = listOf("dart")
        ),

        // ---- Shell ----
        Toolchain(
            id = "bash",
            displayName = "Bash",
            pkgName = "bash",
            providesBinaries = listOf("bash"),
            sizeMb = 4,
            languages = listOf("bash")
        ),

        // ---- Data ----
        Toolchain(
            id = "sqlite",
            displayName = "SQLite (SQL)",
            pkgName = "sqlite",
            providesBinaries = listOf("sqlite3"),
            sizeMb = 6,
            languages = listOf("sql")
        )
    )

    private val byIdMap: Map<String, Toolchain> = all.associateBy { it.id }

    /** All toolchains indexed by every language id they enable. */
    private val byLanguageMap: Map<String, Toolchain> = buildMap {
        all.forEach { tc -> tc.languages.forEach { lang -> put(lang.lowercase(), tc) } }
    }

    /** The toolchain that provisions the given [com.andcodedit.lang.Language.id], if any. */
    fun forLanguage(langId: String): Toolchain? = byLanguageMap[langId.lowercase()]

    /** Looks up a toolchain by its stable [Toolchain.id]. */
    fun byId(id: String): Toolchain? = byIdMap[id.lowercase()]
}
