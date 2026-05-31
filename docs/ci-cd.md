# CI/CD

Continuous integration is handled by GitHub Actions. The workflow is defined in
[`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) and is
named **Android CI**.

## When it runs

The workflow runs on every **push** and **pull request** to **all branches**.

## Jobs

### `build`

Runs on `ubuntu-latest` and produces the debug APK:

1. Checks out the repository.
2. Sets up **JDK 17** (Temurin).
3. Sets up the **Android SDK**.
4. Installs the **NDK (27.2.12479018)** and **CMake (3.22.1)**.
5. Installs the **stable Rust toolchain**, the four Android Rust targets, and
   **cargo-ndk**.
6. Builds the **native terminal core** with cargo-ndk. This step is marked
   `continue-on-error: true`, so a native build failure does not fail the job —
   the app ships a pure-Kotlin terminal fallback and builds without the native
   library.
7. Sets up **Gradle**.
8. Runs `./gradlew assembleDebug --stacktrace`.
9. Uploads the resulting APK(s) as an artifact named **`app-debug-apk`** from
   `app/build/outputs/apk/debug/*.apk`.

### `checks`

Runs on `ubuntu-latest` and performs verification:

1. Checks out the repository.
2. Sets up **JDK 17** (Temurin).
3. Sets up the **Android SDK**.
4. Sets up **Gradle**.
5. Runs `./gradlew testDebugUnitTest lintDebug --stacktrace` (unit tests and
   Android lint for the debug variant).

## Downloading the APK artifact

After a workflow run completes:

1. Go to the **Actions** tab of the repository on GitHub.
2. Select the **Android CI** workflow run you are interested in.
3. Scroll to the **Artifacts** section at the bottom of the run summary page.
4. Click **`app-debug-apk`** to download a zip containing the debug APK.

Artifacts are retained according to the repository's default retention policy.
