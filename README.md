# ANDCODEDIT

> **The ultimate Android-native code editor** — VSCode power on Android, with unique superpowers: **DEX Mode**, **Android 16 Desktop Mode**, and a **full interactive terminal** connected directly to the Linux shell.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-14%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![Rust](https://img.shields.io/badge/Rust-backend-orange.svg)](https://www.rust-lang.org)

---

## ✨ Why ANDCODEDIT?

Most Android code editors are either too basic or feel like compromised web ports. **ANDCODEDIT** is built from the ground up as a **native Android experience** that doesn't compromise on power.

### Core Superpowers

| Feature                    | Description                                                                 | Why It Matters |
|---------------------------|-----------------------------------------------------------------------------|----------------|
| **Monaco Editor**         | The full VS Code editor engine in a WebView, with a two-way Kotlin↔JS bridge (language, format, undo/redo, cursor). Offline via bundled `vs/`, CDN fallback otherwise. | Real desktop-grade editing on Android |
| **30+ Language Runner**   | Edit & run 30+ languages (Python, JS/TS, C/C++, Go, Rust, Java, Kotlin, Swift, …) with streamed stdout/stderr and exit codes | Execute code on-device, not just edit it |
| **Toolchain Manager**     | Provisions compilers/interpreters at runtime via a Termux/proot bootstrap (`pkg`/`apt`), with availability checks + one-tap install | 25+ toolchains without an impossible all-in-one APK |
| **DEX Mode**              | View, edit, and reassemble DEX bytecode directly. Smali support + patching (dexlib2). | True low-level Android development & reverse engineering on-device |
| **Android 16 Desktop Mode** | Full native support for Android 16's desktop/windowing features            | Real productivity on tablets, foldables, and external displays |
| **Full Terminal**         | Interactive terminal connected to the real shell (Kotlin ProcessBuilder today, Rust `portable-pty` via UniFFI next) | Run gradle, adb, python, git — everything you need |
| **AI Agents**             | RAG over project knowledge + tool-calling into the terminal                 | Context-aware assistance without leaving the editor |

> **Why toolchains aren't bundled in the APK.** A single APK can't physically
> contain 30 multi-gigabyte toolchains (GCC, JDK, Python, Node, Rust, …) within
> Play's size limits. Like Termux/Acode, ANDCODEDIT provisions them **on-device,
> on demand**: it shells into a Termux (or proot) userland and installs only what
> you use. The editor, runner UI, and language registry ship in the APK; the
> compilers are fetched at runtime. See `docs/build-and-setup.md`.

---

## 🏗️ Project Structure

```
ANDCODEDIT/
├── README.md
├── docs/                          # Project documentation
│   ├── architecture.md
│   ├── roadmap.md
│   ├── contributing.md
│   ├── build-and-setup.md
│   ├── dex-mode-spec.md
│   ├── terminal-integration-design.md
│   ├── ai-agents-design.md
│   └── desktop-mode-guidelines.md
└── skills/                        # Specialized agent skills for development
    ├── android-developer.md
    ├── rust.md
    ├── agents.md
    ├── systems-programmer.md
    ├── android-ui-engineer.md
    └── ... (30 total specialized skills)
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest)
- JDK 17+
- NDK r27+
- Rust toolchain + Android targets
- Android 16 device or emulator (for desktop mode testing)

### Quick Build
```bash
git clone https://github.com/a-lalit/ANDCODEDIT.git
cd ANDCODEDIT
./gradlew assembleDebug
```

See [build-and-setup.md](docs/build-and-setup.md) for full environment setup, Rust integration, and desktop mode testing commands.

---

## 🗺️ Development Roadmap

| Phase | Focus                          | Status      | Target     |
|-------|--------------------------------|-------------|------------|
| 0     | Foundation & Core Editor       | In Progress | Q2 2026    |
| 1     | Full Interactive Terminal      | Planned     | Q3 2026    |
| 2     | DEX Mode MVP                   | Planned     | Q4 2026    |
| 3     | Android 16 Desktop Polish      | Planned     | Q4 2026    |
| 4     | AI Agents Integration          | Planned     | Q1 2027    |
| 5     | Public Beta & Play Store       | Planned     | Q2 2027    |

Full details in [roadmap.md](docs/roadmap.md).

---

## 🧠 Specialized Skills

This project is developed using a unique **skill-based agent system**. Each major domain has its own detailed technical guide:

- **Core Development**: `android-developer`, `kotlin`, `java`, `kotlin-multiplatform`
- **Systems & Performance**: `rust`, `systems-programmer`, `c-lang`, `c-plus-plus`
- **UI/UX**: `android-ui-engineer`, `android-ux-engineer`
- **Quality**: All `*-tester` skills
- **Advanced**: `agents`, `sqlite-engineer`, `devops-engineer`, `project-manager`

All skill files are available in the [`skills/`](skills/) directory.

---

## 🤝 Contributing

We welcome contributions! Please read:
- [Contributing Guidelines](docs/contributing.md)
- [Architecture Overview](docs/architecture.md)

**Good first issues** are labeled in the repo.

---

## 📄 License

MIT License — see [LICENSE](LICENSE) file.

---

## 🌟 Star History

If you find ANDCODEDIT useful, please consider giving it a star! It helps the project grow.

---

**Built with ❤️ for Android developers who want real power on their devices.**

*ANDCODEDIT — Code. Debug. Reverse. Ship. All on Android.*