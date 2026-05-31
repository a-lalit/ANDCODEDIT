#!/usr/bin/env bash
#
# setup-and-build.sh — provision the Android SDK and build ANDCODEDIT.
#
# This is a no-op-friendly bootstrap intended to run in a cloud/CI environment
# (or a local machine) whose network policy ALLOWS Google's hosts:
#   - dl.google.com           (Android command-line tools + SDK packages)
#   - maven.google.com        (Android Gradle Plugin + AndroidX artifacts)
# plus Maven Central and services.gradle.org (Gradle distribution).
#
# If those hosts are blocked (e.g. a restricted sandbox), SDK download and the
# Gradle dependency resolution will fail with HTTP 403 — that is expected, and
# the build must instead run on GitHub Actions (.github/workflows/android-ci.yml)
# or an environment with a permissive network policy.
#
# Usage:  bash scripts/setup-and-build.sh [assembleDebug|assembleRelease|test]
set -euo pipefail

TASK="${1:-assembleDebug}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_VER="11076708"
PLATFORM="android-36"
BUILD_TOOLS="36.0.0"
NDK_VER="27.2.12479018"

echo "==> ANDCODEDIT setup-and-build (task: $TASK)"
echo "==> Project root: $ROOT"
echo "==> SDK root:     $SDK_ROOT"

# --- 1. Preflight: can we reach Google's hosts? ---------------------------
preflight() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' "https://dl.google.com" || echo ERR)"
  if [ "$code" != "200" ] && [ "$code" != "301" ] && [ "$code" != "302" ]; then
    echo "!! dl.google.com returned '$code' — Google hosts appear BLOCKED."
    echo "!! This environment cannot download the Android SDK or AndroidX."
    echo "!! Build on GitHub Actions or recreate the environment with a"
    echo "!! network policy that allows maven.google.com and dl.google.com."
    echo "!! See docs/build-and-setup.md (Troubleshooting)."
    return 1
  fi
  echo "==> Preflight OK (dl.google.com reachable: $code)"
}

# --- 2. Install Android command-line tools + packages ---------------------
install_sdk() {
  mkdir -p "$SDK_ROOT/cmdline-tools"
  if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "==> Downloading Android command-line tools..."
    local zip="/tmp/cmdline-tools.zip"
    curl -fsSL -o "$zip" \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VER}_latest.zip"
    rm -rf "$SDK_ROOT/cmdline-tools/tmp" "$SDK_ROOT/cmdline-tools/latest"
    mkdir -p "$SDK_ROOT/cmdline-tools/tmp"
    unzip -q -o "$zip" -d "$SDK_ROOT/cmdline-tools/tmp"
    mv "$SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -rf "$SDK_ROOT/cmdline-tools/tmp" "$zip"
  fi

  export ANDROID_SDK_ROOT="$SDK_ROOT"
  export ANDROID_HOME="$SDK_ROOT"
  export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

  echo "==> Accepting SDK licenses..."
  yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true

  echo "==> Installing SDK packages (platform $PLATFORM, build-tools $BUILD_TOOLS, NDK $NDK_VER)..."
  sdkmanager --sdk_root="$SDK_ROOT" \
    "platform-tools" \
    "platforms;${PLATFORM}" \
    "build-tools;${BUILD_TOOLS}" \
    "ndk;${NDK_VER}" \
    "cmake;3.22.1"
}

# --- 3. Point the project at the SDK --------------------------------------
write_local_properties() {
  echo "sdk.dir=$SDK_ROOT" > "$ROOT/local.properties"
  echo "==> Wrote local.properties (sdk.dir=$SDK_ROOT)"
}

# --- 4. (optional) build the native Rust terminal core --------------------
build_rust() {
  if command -v cargo >/dev/null 2>&1 && command -v cargo-ndk >/dev/null 2>&1; then
    echo "==> Building native terminal core via cargo-ndk..."
    ( cd "$ROOT/rust-terminal" && \
      cargo ndk -o ../app/src/main/jniLibs \
        -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 build --release ) || \
      echo "!! Native build failed/skipped — app uses the Kotlin ProcessBuilder fallback."
  else
    echo "==> Skipping native core (cargo/cargo-ndk not installed). Kotlin fallback is used."
  fi
}

# --- 5. Gradle build ------------------------------------------------------
gradle_build() {
  echo "==> Running ./gradlew $TASK ..."
  ( cd "$ROOT" && chmod +x ./gradlew && ./gradlew "$TASK" --stacktrace )
  echo "==> Done. APK(s):"
  find "$ROOT/app/build/outputs/apk" -name "*.apk" 2>/dev/null || true
}

preflight
install_sdk
write_local_properties
build_rust
gradle_build
