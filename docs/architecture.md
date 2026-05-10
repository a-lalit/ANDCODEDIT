# ANDCODEDIT Architecture

**Version**: 1.0  
**Date**: May 2026  
**Status**: Draft for MVP

## 1. Overview
ANDCODEDIT is a native Android code editor targeting Android 14+ (with full Android 16 desktop mode support). It combines VSCode-like editing, unique DEX Mode for bytecode work, full interactive terminal connected to the Linux shell, and future AI agents.

Core Principles: Native Android first (Kotlin + Jetpack Compose), performance (no jank with editor+terminal+DEX), security (least-privilege shell), adaptability across form factors and desktop mode, extensibility.

## 2. High-Level Architecture
(See the layered diagram in the full version: UI (Compose) → ViewModels → Domain/UseCases → Repositories (Room + File + Dex + Terminal) → Native Layer (Kotlin + Rust PTY backend))

Data Flow Example: Edit Kotlin → save → run gradle in terminal → new DEX appears → parse in background → update DEX viewer reactively.

## 3. Key Components
- Editor Core: Compose + custom or library-based text rendering, syntax, minimap, multi-cursor. State persisted in Room.
- DEX Mode: dexlib2 + Kotlin wrappers, bytecode/Smali views, editing + reassemble to APK, background parsing + caching.
- Terminal: Rust backend (portable-pty + vte via uniffi/JNI) recommended for performance/safety. Full interactive, colors, resize, clickable paths. Survives desktop mode changes.
- Android 16 Desktop: WindowManager APIs, adaptive layouts with WindowSizeClass, multi-editor groups, detachable panels.
- Persistence: Room + WAL + FTS5 + embeddings for AI.
- AI Agents (future): Local inference + RAG over code/DEX/terminal history + tool calling with human confirmation.

## 4. Tech Choices
Kotlin + Compose (native + desktop support), Rust for terminal (safety + perf), Room + Flow (reactive + survives death), selective KMP, uniffi/JNI bridge.

## 5. Security & Performance
Terminal with app UID by default. Performance targets: 120 FPS editor, <3s DEX parse for 50MB, <50ms terminal latency, <350MB memory with all features.

## 6. Open Decisions
Editor library choice, vector store for RAG, on-device LLM timing.

See skill files in agent-skills folder for detailed implementation guidance per domain.