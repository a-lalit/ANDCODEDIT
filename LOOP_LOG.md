# ANDCODEDIT — Loop Log

Running log of the Loop Protocol execution. Newest loop last.

---

## Loop 0 — Reconnaissance & Ground-Truth Audit
- **Objective:** produce an honest audit of the repo's true state, verified by a real build.
- **Result:** `docs/AUDIT.md` created and merged (PR #2). Key finding: `main` did not compile (15 Kotlin errors); zero tests; no lint/detekt config; Rust terminal crate unwired; Monaco `vs/` assets absent; Room layer dead; committed `.gradle/`+`local.properties`.
- **Build result:** `./gradlew assembleDebug` **FAILED** (`:app:compileDebugKotlin`, 15 errors).
- **Test result:** 0 tests exist.

---

## Loop 1 — Make `main` compile + repo hygiene
- **Objective:** green `./gradlew clean build` and `lintDebug` with no errors; stop committing build/IDE artifacts.
- **Files touched:**
  - `app/.../ui/screens/EditorScreen.kt` — escaped `$` interpolations inside the embedded `sampleDexService` raw string (they were being parsed as real Kotlin, hence the `Unresolved reference 'e'/'classDef'/'m'/'it'` errors); moved the post-save snackbar out of the button `onClick` into `rememberCoroutineScope().launch` (`LaunchedEffect` cannot be invoked from a non-composable lambda).
  - `app/.../ui/screens/DexModeScreen.kt` — added missing `androidx.compose.ui.unit.sp` import; `methods.size` → `methods.count()` (dexlib2 returns an `Iterable`, not a `Collection`); same snackbar-in-onClick fix via `rememberCoroutineScope()`.
  - `app/.../navigation/AppNavigation.kt` — `DexModeScreen` takes no `appStateViewModel` parameter.
  - `app/.../ai/AiAgentService.kt` — `'%.2f'` (char literal, too many chars) → `"%.2f"` (string).
  - `app/.../language/DiagnosticsManager.kt` — Sora `Content.length` is a property, not a function (`content.length()` → `content.length`).
  - `app/.../editor/MonacoWebView.kt` — extracted the anonymous JS-bridge object into a named `MonacoBridge` class so the `@JavascriptInterface` annotations are visible to the WebView runtime (fixes the one Lint **error**); suppressed the residual `JavascriptInterface` lint false-positive that fires because lint can't resolve the type through `remember`'s generic.
  - `.gitignore` — added; untracked `.gradle/` caches and `local.properties`.
- **Build result:** `./gradlew clean build` **SUCCESSFUL** (99 tasks). `assembleDebug` + `assembleRelease` both build.
- **Lint result:** `lintDebug` → **0 errors, 31 warnings** (warnings pre-existing: deprecated `ArrowBack`/`statusBarColor`/`navigationBarColor`, etc. — logged for a later cleanup loop, not introduced here).
- **Test result:** still 0 tests (test scaffolding is Loop 2).
- **Assumptions made:** none material; all fixes were mechanical corrections of existing intent.
- **Open risks carried forward:** 31 lint warnings; no tests yet; Monaco offline assets still absent; Rust terminal still unwired; `ExpandedDesktopLayout` still an unreachable demo.
- **Next loop:** Loop 2 — test scaffolding (`app/src/test/`) + first unit tests for pure logic (LanguageRegistry, DiagnosticsManager, AppStateViewModel) and wire `testDebugUnitTest` into CI.

---

## Loop 2 — Test scaffolding + CI test gate
- **Objective:** the project has a real unit-test suite (>0 tests) exercising its pure-logic seams, and CI fails when a test fails.
- **Files touched:**
  - `app/src/test/java/com/andcodedit/lang/LanguageRegistryTest.kt` — 8 tests: unique ids/extensions, `byId`/`byExtension` case- and dot-insensitivity, `monacoIdFor` plaintext fallback, template invariants across all 30 languages.
  - `app/src/test/java/com/andcodedit/language/DiagnosticsManagerTest.kt` — 7 tests: GCC/Clang, Python traceback, tsc, javac, generic fallback parsing; same-line dedupe; clean output → empty.
  - `app/src/test/java/com/andcodedit/terminal/TerminalGridTest.kt` — 7 tests: text/byte append, ANSI + carriage-return stripping, clear, resize, scrollback cap.
  - `.github/workflows/android-ci.yml` — added `testDebugUnitTest` gate to the `build` job + test-report artifact upload.
- **Build result:** `./gradlew testDebugUnitTest` **SUCCESSFUL**.
- **Test result:** **22 tests, 0 failures** (LanguageRegistryTest 8, DiagnosticsManagerTest 7, TerminalGridTest 7).
- **Issues found and resolved:** none in production code; tests were written against current behavior.
- **Open questions:**
  - `AppStateViewModel` is not unit-testable as-is: its `init` block spawns a live shell `Process` via `TerminalSessionFactory` (fails on a plain JVM). Loop 3 (architecture triage) should inject the factory so tab logic can be tested without a process.
  - `DiagnosticsManager.applyDiagnostics` needs a `CodeEditor` (Android widget) — covered later via instrumentation or by extracting the offset math.
- **Next loop:** Loop 3 — architecture triage (Room dead code, DataStore, terminal-session injection, `ExpandedDesktopLayout` reachability).

---

## Loop 3 — Architecture triage: testable terminal seam + lifecycle hygiene
- **Objective:** `AppStateViewModel` becomes unit-testable (no live process spawned on construction in tests), terminal sessions are properly closed, and the audit's architecture questions get explicit dispositions.
- **Files touched:**
  - `app/.../terminal/TerminalSession.kt` — `TerminalSession` is now an interface (`rows`, `cols`, `writeInput`, `resize`, `isAlive`, `close`); the ProcessBuilder implementation is renamed `ProcessTerminalSession`. `TerminalSessionFactory.create` keeps its exact signature, so no call site changed. This is also the seam the Loop 9 Rust-PTY implementation plugs into.
  - `app/.../viewmodel/AppStateViewModel.kt` — session construction goes through an injectable `sessionFactory` (defaulting to `TerminalSessionFactory`, `@JvmOverloads` so the framework's `SavedStateHandle` constructor still resolves); the configured `terminalShell` preference is now actually passed to new sessions; `closeTerminalTab` and `onCleared` now close sessions (previously leaked processes).
  - `app/src/test/java/com/andcodedit/viewmodel/AppStateViewModelTest.kt` — 13 tests with a `FakeTerminalSession`: editor-tab add/dedupe/close/switch bounds, terminal-tab lifecycle incl. session close, layout/pref clamping, SavedStateHandle persistence.
  - `app/build.gradle.kts` — `kotlinx-coroutines-test` (test only).
- **Architecture dispositions (recorded, not guessed):**
  - **Room (`data/`)**: currently zero call sites. KEEP — Loop 4 wires it for workspace/recent-project persistence; will delete then if a better fit (DataStore) wins.
  - **`ExpandedDesktopLayout`**: audit said unreachable — that was wrong; it is used by `MainActivity` and `EditorScreen`. Stays; DeX polish lands in a later loop.
  - **Single `AppStateViewModel`**: kept (matches existing pattern); refactor only if a loop actually needs feature-scoped VMs.
- **Build result:** `./gradlew assembleDebug testDebugUnitTest` **SUCCESSFUL**.
- **Test result:** **35 tests, 0 failures** (13 new).
- **Issues found and resolved:** terminal `Process` leaked on tab close and on ViewModel clear — fixed by closing sessions.
- **Next loop:** Loop 4 — project/workspace management (SAF folder open, recents, persistence).
