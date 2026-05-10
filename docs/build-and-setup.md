# Build and Setup Guide for ANDCODEDIT

## Prerequisites

- Android Studio Hedgehog or later (or latest stable)
- JDK 17 (Temurin or Amazon Corretto recommended)
- Android SDK with API 36 (Android 16)
- NDK r27 or later
- Rust toolchain (rustup)
- cargo-ndk for Android builds
- Android 14+ device or emulator (Android 16 emulator recommended for desktop mode)

## Environment Setup

1. Install Android Studio and SDK tools.
2. Install Rust: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
3. Add Android targets: `rustup target add aarch64-linux-android armv7-linux-androideabi`
4. Install cargo-ndk: `cargo install cargo-ndk`
5. (Optional) Install Android NDK via Android Studio SDK Manager.

## Building the Project

```bash
git clone https://github.com/a-lalit/ANDCODEDIT.git
cd ANDCODEDIT
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`.

## Rust Terminal Integration

The `rust-terminal` module uses UniFFI to expose PTY functionality to Kotlin.

To build the native library:

```bash
cd rust-terminal
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../app/src/main/jniLibs build --release
```

Then generate Kotlin bindings if needed:

```bash
uniffi-bindgen generate src/andcodedit_terminal.udl --language kotlin --out-dir ../app/src/main/java/com/andcodedit/terminal/
```

## Testing Desktop Mode

Use Android 16 emulator with desktop mode enabled or a device with DeX/external display.

See desktop-mode-guidelines.md for detailed testing steps.

## Running Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

For full CI setup, see .github/workflows/ (to be added in Phase 5).