# andcodedit_terminal

Production PTY backend for the ANDCODEDIT terminal. It opens a pseudo-terminal,
spawns a shell, streams output to Kotlin via a UniFFI callback, and supports
writing input and resizing the terminal.

The Rust API is exposed to Kotlin through [UniFFI](https://mozilla.github.io/uniffi-rs/),
generated into the package `uniffi.andcodedit_terminal`.

## Public surface (UDL)

- `dictionary TerminalConfig { rows: u16, cols: u16, shell: string }`
- `[Error] interface TerminalError { Spawn(...); Io(...); Pty(...); }`
- `callback interface TerminalOutputCallback { void on_output(sequence<u8> data); }`
- `interface TerminalSession { write_input(...); resize(...); }`
- `create_terminal_session(config, callback) -> TerminalSession`

## Prerequisites

```sh
# Android target toolchains
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android

# cargo-ndk for cross-compiling against the Android NDK
cargo install cargo-ndk
```

You also need the Android NDK installed and `ANDROID_NDK_HOME` (or
`ANDROID_NDK_ROOT`) pointing at it.

## Build the native libraries

From this directory (`rust-terminal/`):

```sh
cargo ndk -o ../app/src/main/jniLibs \
    -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 \
    build --release
```

This produces `libandcodedit_terminal.so` for each ABI under:

```
../app/src/main/jniLibs/arm64-v8a/libandcodedit_terminal.so
../app/src/main/jniLibs/armeabi-v7a/libandcodedit_terminal.so
../app/src/main/jniLibs/x86/libandcodedit_terminal.so
../app/src/main/jniLibs/x86_64/libandcodedit_terminal.so
```

## Generate the Kotlin bindings

```sh
cargo run --features=cli --bin uniffi-bindgen \
    generate src/andcodedit_terminal.udl \
    --language kotlin \
    --out-dir ../app/src/main/java
```

The generated bindings land in:

```
../app/src/main/java/uniffi/andcodedit_terminal/
```

## Loading from the app

The app loads the native library via:

```kotlin
System.loadLibrary("andcodedit_terminal")
```

(UniFFI's generated code does this automatically when the package is first used.)

## Fallback

The app currently ships a pure-Kotlin `ProcessBuilder` fallback, so it works
without this native library. Building and bundling `andcodedit_terminal`
upgrades the terminal to a real PTY-backed session (job control, resizing,
interactive programs, etc.).
