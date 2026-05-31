# Build & Setup

This guide explains how to build and run **ANDCODEDIT**, an Android app
(Kotlin 2.1.0 + Jetpack Compose, Gradle KTS, AGP 8.7.3, `compileSdk 36`,
`minSdk 26`) with an optional native Rust terminal core
(`andcodedit_terminal`, built via cargo-ndk + UniFFI).

## Prerequisites

- **JDK 17** (Temurin or any other distribution).
- **Android SDK** with:
  - **Platform 36** (`platforms;android-36`)
  - **Build-tools 36** (`build-tools;36.0.0`)
  - **NDK 27.2.12479018** (`ndk;27.2.12479018`) — only needed for the native core
  - **CMake 3.22.1** (`cmake;3.22.1`) — only needed for the native core
- **Accept the SDK licenses** after installing the components:
  ```bash
  sdkmanager --licenses
  ```
- **Rust toolchain** (stable) plus the four Android targets and `cargo-ndk`
  (only needed if you want to build the native core):
  ```bash
  rustup target add \
    aarch64-linux-android \
    armv7-linux-androideabi \
    x86_64-linux-android \
    i686-linux-android
  cargo install cargo-ndk
  ```

## Configure the SDK location (`local.properties`)

Create a `local.properties` file at the repo root pointing Gradle at your
Android SDK installation:

```properties
sdk.dir=/path/to/Android/sdk
```

Typical locations:

- macOS: `/Users/<you>/Library/Android/sdk`
- Linux: `/home/<you>/Android/Sdk`
- Windows: `C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`

`local.properties` is machine-specific and should not be committed.

## Building the app

From the repo root:

```bash
# Build the debug APK
./gradlew assembleDebug
```

The resulting APK is written to:

```
app/build/outputs/apk/debug/
```

To build and install onto a connected device or running emulator:

```bash
./gradlew installDebug
```

## Building the native Rust core (optional)

The native terminal core lives under `rust-terminal/` and is compiled into a
`cdylib` (`andcodedit_terminal`) for each supported ABI using **cargo-ndk**:

```bash
cd rust-terminal
cargo ndk \
  -o ../app/src/main/jniLibs \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86 \
  -t x86_64 \
  build --release
```

This places the compiled shared libraries in:

```
app/src/main/jniLibs/<abi>/libandcodedit_terminal.so
```

### Generating the UniFFI Kotlin bindings

The Kotlin bindings for the native library are generated with **UniFFI**. After
building the library, run the UniFFI bindgen against it, for example:

```bash
cd rust-terminal
cargo run --features cli --bin uniffi-bindgen generate \
  --library target/release/libandcodedit_terminal.so \
  --language kotlin \
  --out-dir ../app/src/main/java
```

The generated bindings land in:

```
app/src/main/java/uniffi/andcodedit_terminal/
```

> **NOTE:** The app ships a **pure-Kotlin `ProcessBuilder` terminal fallback**.
> This means the app **builds and runs without the native Rust library** — the
> native core is an optional performance/feature enhancement, not a build
> requirement. You can skip the entire Rust section above and still produce a
> working debug APK.

## Troubleshooting

### Builds require network access to Google's Maven

Building this project **requires network access to Google's Maven repositories**:

- `maven.google.com`
- `dl.google.com`

These hosts serve the **Android Gradle Plugin (AGP)**, the **AndroidX**
libraries, and the **Android SDK** components themselves.

**Restricted or allowlisted sandboxes that block those hosts cannot compile the
app locally.** If your environment blocks Google hosts, you have two options:

1. **Use the GitHub Actions CI** (see [`ci-cd.md`](ci-cd.md)), where Google's
   hosts are reachable and the debug APK is produced as a downloadable artifact.
2. **Use an environment whose network policy permits Google hosts.**

### Maven Central dependencies

Some dependencies — including the **Sora editor** and the **smali** artifacts —
are resolved from **Maven Central** (`repo.maven.apache.org` /
`repo1.maven.org`). Make sure Maven Central is also reachable, in addition to the
Google hosts above.

### SDK licenses not accepted

If Gradle fails with a licensing error, run `sdkmanager --licenses` and accept
all licenses, then retry the build.
