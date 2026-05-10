# ANDCODEDIT Roadmap

**Version**: 1.0  
**Date**: May 2026  
**Status**: Active Planning

## Vision
Build the most powerful native Android code editor that rivals desktop IDEs while embracing Android's unique capabilities (DEX, Desktop Mode, on-device Linux shell).

## Phase Timeline

| Phase | Name | Key Deliverables | Target | Status |
|-------|------|------------------|--------|--------|
| 0 | Foundations & Core Editor | Project structure, MVVM + Compose shell, basic editor viewport, file explorer, settings | Q2 2026 | In Progress |
| 1 | Full Interactive Terminal | Rust PTY backend, Compose terminal view, tabs/splits, clickable paths, session persistence | Q3 2026 | In Progress |
| 2 | DEX Mode MVP | dexlib2 parser, Smali editor, dual-pane (decompiled + Smali), class browser, basic rebuild & export | Q4 2026 | Planned |
| 3 | Android 16 Desktop Polish | Adaptive layouts, multi-panel, keyboard-first, trackpad/mouse, persistent panels | Q4 2026 | Planned |
| 4 | AI Agents Integration | RAG over code/DEX/terminal, tool use, local LLM hooks, human-in-loop confirmation | Q1 2027 | Planned |
| 5 | Public Beta & Launch | Play Store release, docs, onboarding, beta program, performance hardening | Q2 2027 | Planned |

## Detailed Milestones
- **Phase 0**: Core shell, navigation, basic editor, project setup (Kotlin/Compose/Room)
- **Phase 1**: Rust terminal, UniFFI, PTY, input/output, resize, desktop mode survival
- **Phase 2**: DEX parser, editor integration, patching, APK export/signing
- **Phase 3**: Desktop mode full polish, split panes, multi-editor
- **Phase 4**: AI layer, agents, RAG, tool calling
- **Phase 5**: Beta testing, crash reporting, performance, store assets

See [architecture.md](architecture.md) for technical details.