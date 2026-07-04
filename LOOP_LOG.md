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
