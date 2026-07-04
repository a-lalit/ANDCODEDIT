# ANDCODEDIT — Loop 0 Ground-Truth Audit

**Date:** 2026-07-04
**Audited commit:** `e6e97b8` (`main`, merge of PR #1)
**Method:** full source walk + real clean build (`bash scripts/setup-and-build.sh assembleDebug` → `./gradlew assembleDebug`) on a fresh checkout with a freshly provisioned Android SDK (platform android-36, build-tools 36.0.0), JDK 17, Gradle 8.11.1.

---

## 1. Headline: the current state of `main`

> **`main` does not compile.** `./gradlew assembleDebug` fails in
> `:app:compileDebugKotlin` with **15 Kotlin compile errors** (verbatim list in
> §5). Everything else in this audit must be read in that light: no test, lint,
> or runtime claim about HEAD can currently be verified on-device.

Other headline facts, all verified directly:

- **Zero tests exist.** There is no `app/src/test/` or `app/src/androidTest/` directory at all — only `app/src/main/`. Test dependencies (JUnit, Espresso, compose-ui-test) are declared in Gradle but no test source exists.
- **No static-analysis config exists.** No detekt, no ktlint, no spotless. Android Lint is configured `abortOnError = false` (deliberately non-fatal, per commit `75114df`). Lint cannot currently run to completion anyway because compilation fails.
- **The Rust terminal core is not built or wired in.** `settings.gradle.kts` includes only `:app`. `rust-terminal/` (portable-pty + UniFFI, ~44 KB of real Rust) is a standalone crate that no Gradle task builds, no CI job compiles, and no Kotlin binding consumes. The shipped terminal is the Kotlin `ProcessBuilder` fallback.
- **Monaco is CDN-dependent by default.** `app/src/main/assets/monaco/` contains only `editor.html` + `PLACE_MONACO_HERE.md`; the `vs/` distribution is not bundled, so offline Monaco does not work out of the box (documented fallback to cdnjs).
- **Repo hygiene:** `.gradle/` lock/cache files and `local.properties` (with a machine-specific `sdk.dir`) are committed to git. There is no `.gitignore` covering them.
- **History note:** the directive referenced "five existing commits"; the repo actually has **37 commits** on `main` (a long `claude/app-dev-continuation` branch merged as PR #1). No git bundle and no Figma handoff doc are present in-repo; `docs/` contains the design docs listed in §2.

---

## 2. Repo inventory

### Build system
| Component | Version |
|---|---|
| Gradle (wrapper) | 8.11.1 |
| AGP | 8.7.3 (compileSdk 36 with `suppressUnsupportedCompileSdk`) |
| Kotlin | 2.1.0 (+ compose plugin, KSP 2.1.0-1.0.29) |
| Compose BOM | 2025.04.00 |
| min / target SDK | 26 / 36 |
| JVM target | 17, core-library desugaring enabled |

### Modules
- `:app` — the only Gradle module. Single package tree `com.andcodedit`, 23 Kotlin files, 2,841 LOC.
- `rust-terminal/` — Rust crate (portable-pty PTY backend via UniFFI). **Not part of the Gradle build.**
- `scripts/setup-and-build.sh` — SDK bootstrap + build script (works; used for this audit).
- `.github/workflows/android-ci.yml` — CI: `assembleDebug` only (no tests, no lint gate), uploads APK, comments errors on PRs.
- `docs/` — architecture, roadmap, prd, ux flow, build-and-setup, ci-cd, contributing, phased plan, specdriven kit + 2 skill docs.

### Source map (all under `app/src/main/java/com/andcodedit/`)
| Area | Files | LOC |
|---|---|---|
| Screens (Compose) | Home, Editor (Sora), MonacoEditor, Runner, Toolchain, Terminal, DexMode, AIChat, Settings | ~1,900 (screens not individually counted) |
| Editor | `editor/MonacoWebView.kt` (WebView + JS bridge) | 179 |
| Languages/Runner | `lang/` — Language, LanguageRegistry (30+ langs), CodeRunner, InProcessRunner (Rhino/LuaJ/BeanShell), RuntimeManager, ShellEnvironment | 884 |
| Toolchains | `runtime/BootstrapManager.kt`, `runtime/Toolchain.kt` (Termux/proot bootstrap) | 565 |
| Terminal | `terminal/TerminalSession.kt` (ProcessBuilder), `terminal/TerminalGrid.kt` | 216 |
| DEX | `dex/DexParserService.kt` (dexlib2 parse; reassembly is a documented stub) | 105 |
| Git | `git/GitService.kt` (JGit: init/open, commitAll, push, status) | 134 |
| AI | `ai/AiAgentService.kt` (hash-"embedding" RAG + canned responses — a stub) | 127 |
| State | `viewmodel/AppStateViewModel.kt` (single app-wide VM, SavedStateHandle) | 188 |
| Data | `data/` — Room DB, ProjectFile entity, DAO, ProjectRepository | 135 |
| Desktop | `ui/layouts/ExpandedDesktopLayout.kt` (self-described demo), `ui/components/ResizablePane.kt` | large / demo |
| Diagnostics | `language/DiagnosticsManager.kt` (lint-output parser → Sora diagnostics) | 181 |
| Shell | MainActivity, AndCodEditApp, AppNavigation, theme | ~130 |

### Architecture as actually committed
- MVVM-lite: **one** `AppStateViewModel` shared by all screens; several screens also hold significant `remember { }` local state and construct services (`DexParserService`, `CodeRunner`, `BootstrapManager`) directly in composables.
- **No DI framework** (no Hilt/Koin) — manual construction.
- Navigation: single `NavHost` with string routes.
- Persistence: Room is declared and a `ProjectRepository` exists but **nothing uses it** (no reference outside `data/` — verified by grep). Settings use SavedStateHandle, not DataStore (DataStore is a declared dep, unused).

---

## 3. What the git history actually delivered vs. intended

The 37 commits show three eras:
1. **Foundation commits** — Compose shell, navigation, Sora editor screen, terminal (ProcessBuilder), DEX parse, JGit sidebar, settings, DiagnosticsManager.
2. **`claude/app-dev-continuation` branch** — Monaco WebView editor, 30+ language runner + registry, Termux toolchain bootstrap, in-process interpreters (Rhino/LuaJ/BeanShell after dropping Jython), Room persistence, ShellEnvironment, CI hardening (error surfacing, non-fatal lint, desugaring).
3. **Merge `e6e97b8`** — the merge itself introduced/exposed the compile breakage (e.g. `AppNavigation` passes `appStateViewModel` to `DexModeScreen(navController)` which doesn't accept it).

Intended scope (from README/roadmap/prd): VSCode-class editor, full PTY terminal (Rust), DEX Mode with reassembly, Android 16 desktop mode, AI agents with real RAG/LLM, toolchain manager. Roadmap phases 0–1 are "In Progress"; 2–5 "Planned".

---

## 4. Classification of every subsystem

Legend: **(a)** stubbed/placeholder · **(b)** partially implemented but broken · **(c)** implemented but untested · **(d)** done & correct · **(e)** not started

| Subsystem | Class | Evidence |
|---|---|---|
| Build (assembleDebug) | **(b)** | 15 compile errors at HEAD (§5) |
| App shell / navigation / theme | **(b)** | Structure fine, but `AppNavigation.kt:26` is one of the compile errors |
| Sora EditorScreen (tabs, SAF open/save) | **(b)** | Real Sora + SAF code, but 6 compile errors in the file (mangled string/lambda around lines 132–144, misplaced composable at 625) |
| Monaco editor (WebView + bridge) | **(c)** | Real two-way JS bridge; compiles standalone; offline `vs/` assets missing; never exercised by any test |
| Language registry / CodeRunner (30+ langs) | **(c)** | Real ProcessBuilder streaming w/ Flow; correctness on-device unverified; depends on Termux toolchains being installed |
| In-process runner (JS/Lua/BeanShell) | **(c)** | Real interpreters bundled; no tests |
| Toolchain bootstrap (Termux/proot) | **(c)** | Real `pkg`/`apt` shelling logic; inherently unverifiable off-device; availability checks exist |
| Terminal session | **(c)**/partial | Real interactive `ProcessBuilder` shell, but no PTY: no SIGWINCH, no job control, `resize()` is a no-op comment. Rust PTY core exists but is unwired **(e)** integration |
| TerminalGrid / rendering | **(c)** | Plain-text append buffer; no VT100/ANSI parsing despite `TERM=xterm-256color` |
| DEX Mode — parse/browse | **(c)** | Real dexlib2 parsing (classes/methods/fields) |
| DEX Mode — reassemble/patch | **(a)** | Explicit documented stub (`DexParserService.kt:67`, `DexModeScreen.kt:196`) |
| Git integration | **(c)**/partial | Real JGit init/commitAll/push/status; no diff, stage/unstage per file, pull, branches, log |
| AI agents | **(a)** | Hash-based fake embeddings, canned "Agent Response", tool call fakes its output (`AiAgentService.kt:90-91,115`) |
| Room data layer | **(a)** in effect | Entities/DAO/repo exist but zero call sites — dead code today |
| Desktop mode (ExpandedDesktopLayout) | **(a)** | File self-describes as "fully self-contained demo … replace placeholders"; not reachable from navigation; hardcoded fake file tree/editor/terminal |
| Settings | **(b)**/partial | Terminal font/shell wired to VM; hardening toggles & "cloud backup" are local no-op state |
| DiagnosticsManager | **(c)** | Real parser for GCC/Python/TS/Java lint output → Sora diagnostics; 1 compile error (`content.length()` vs `.length`); no call sites found outside itself |
| Project/workspace management (open folder via SAF, recents) | **(e)** | Only per-file SAF open/save exists; no folder/tree/workspace |
| File explorer | **(e)** | Only the fake tree inside the demo desktop layout |
| Project-wide search | **(e)** | Nothing |
| LSP / language intelligence | **(e)** | Nothing (Sora textmate dep present, unused) |
| Accessibility / i18n | **(e)** | Hardcoded strings throughout; `strings.xml` nearly empty |
| Tests | **(e)** | Zero test files |
| CI | **(c)**/partial | Build-only pipeline, currently red by definition; no test/lint gates, not a required check |
| Rust terminal crate | **(c)** standalone / **(e)** integration | Well-formed crate + UDL; never compiled in CI, no `cargo test` run, no NDK/UniFFI wiring |

---

## 5. Verbatim build result (ground truth)

`./gradlew assembleDebug` — **BUILD FAILED in 3m 8s** (30 tasks executed; all resource/manifest/KSP tasks pass; failure is `:app:compileDebugKotlin`):

```
e: AiAgentService.kt:58:63 Too many characters in a character literal.
e: DiagnosticsManager.kt:54:33 Expression 'length' of type 'kotlin.Int' cannot be invoked as a function. Function 'invoke()' is not found.
e: AppNavigation.kt:26:58 No parameter with name 'appStateViewModel' found.
e: DexModeScreen.kt:120:61 Function invocation 'size(...)' expected.
e: DexModeScreen.kt:166:126 Unresolved reference 'sp'.
e: DexModeScreen.kt:184:126 Unresolved reference 'sp'.
e: DexModeScreen.kt:201:33 @Composable invocations can only happen from the context of a @Composable function
e: EditorScreen.kt:132:47 Unresolved reference 'e'.
e: EditorScreen.kt:142:29 Unresolved reference 'classDef'.
e: EditorScreen.kt:142:54 Unresolved reference 'classDef'.
e: EditorScreen.kt:144:34 Unresolved reference 'm'.
e: EditorScreen.kt:144:44 Unresolved reference 'm'.
e: EditorScreen.kt:144:72 Unresolved reference 'it'.
e: EditorScreen.kt:144:85 Unresolved reference 'm'.
e: EditorScreen.kt:625:33 @Composable invocations can only happen from the context of a @Composable function
```

(Path prefixes `app/src/main/java/com/andcodedit/...` trimmed for readability; full log preserved from the audited run.)

**Tests:** cannot run (compilation fails); regardless, zero test sources exist, so the honest count is **0 passed / 0 failed / 0 exist**.

**Static analysis:** no detekt/ktlint config exists; Android Lint is configured non-fatal and cannot complete a run while compilation fails.

---

## 6. Proposed backlog (Section 3 of the directive, amended by findings)

Ordered; each maps to a loop:

1. **Loop 1 — Make `main` compile + repo hygiene.** Fix the 15 compile errors (mostly mangled strings/lambdas from the last merge), add `.gitignore`, remove committed `.gradle/` + `local.properties`. Gate: green `assembleDebug` locally and in CI.
2. **Loop 2 — Test scaffolding + first real tests.** Create `app/src/test/`; unit tests for the pure-logic seams that already exist (LanguageRegistry command templating, DiagnosticsManager parsers, AppStateViewModel tab logic). Add `testDebugUnitTest` to CI as a gate.
3. **Loop 3 — Architecture triage.** Decide/converge: single-VM vs per-screen VMs, services constructed in composables, dead Room layer (wire it or delete it), unused DataStore, unreachable demo `ExpandedDesktopLayout`. No new features on a shaky base.
4. **Loop 4 — Project/workspace management.** SAF open-folder (`ACTION_OPEN_DOCUMENT_TREE` + persisted permissions), recent projects (this is where Room earns its keep), workspace state.
5. **Loop 5 — File explorer.** Lazy tree over the SAF DocumentFile tree, CRUD, filename search.
6. **Loop 6 — Editor consolidation.** Two parallel editors exist (Sora + Monaco). Pick the primary (decision point — see §7), wire DiagnosticsManager into it, find/replace, large-file behavior.
7. **Loop 7 — Project-wide search.**
8. **Loop 8 — Git completion.** Per-file stage/unstage, diff, log, branches, pull, credential storage.
9. **Loop 9 — Terminal upgrade.** Build `rust-terminal` via cargo-ndk + UniFFI into the APK for real PTY; ANSI/VT parsing in TerminalGrid; honest documentation of stock-Android sandbox limits (no exec of downloaded binaries outside Termux, W^X).
10. **Loop 10 — DEX reassembly.** Replace the documented stub with smali assemble → DEX rebuild → (re)sign, or document precisely why parts are infeasible on-device.
11. **Loop 11 — AI agents.** Replace fake RAG/LLM with a real provider integration (or explicitly descope to Phase 4 per roadmap and remove the fake success responses).
12. **Loop 12 — DeX/desktop, Settings/DataStore, a11y/i18n, performance hardening, CI matrix** — as per directive Loops 9–13.
13. **Final — Release readiness review.**

## 7. Open decision points for Lalit (flagging early, not blocking Loop 1)

- **Two editor stacks** (Sora + Monaco/WebView) are both shipped. Keeping both doubles every future feature (diagnostics, search, LSP). Recommendation: pick one primary; my lean is **Sora** for nativeness/perf on mid-range devices, keeping Monaco as an optional mode — but this deserves your call before Loop 6.
- **AI agents**: real integration needs an LLM provider + API key decision; until then the honest move is removing the fake "Success." responses.
- **Terminal ambition**: PTY-in-app is achievable (Termux proves it); *toolchain execution* on Android 10+ requires the Termux userland (W^X). The current Termux-bootstrap approach is sound; confirming it as the accepted strategy.
