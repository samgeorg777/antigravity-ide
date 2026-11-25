# ▲ Antigravity IDE for Android

[![Latest Release](https://img.shields.io/github/v/release/samgeorg777/antigravity-ide?label=Latest%20Release&color=00F0FF)](https://github.com/samgeorg777/antigravity-ide/releases/latest)
[![Download APK](https://img.shields.io/badge/Download-Antigravity--IDE.apk-7928CA?logo=android&logoColor=white)](https://github.com/samgeorg777/antigravity-ide/releases/latest/download/Antigravity-IDE.apk)

A standalone, native Android AI-first IDE (`com.agyide`) inspired by desktop **Antigravity IDE / Antigravity 2.0**, powered directly by the native ARM64 Linux + glibc + `agy` engine.

## ✨ Features

- **Adaptive Phone & Tablet/DeX Layout:**
  - **Phone:** Compact multi-tab workspace across **Agent | Code | Files & Git | Terminal | Preview** with project switcher drawer.
  - **Tablet / Landscape / DeX:** Desktop 3-pane IDE layout (File/Git Explorer + Code Editor + Agent Chat + collapsible Terminal/Preview drawer).
- **Real Code Editor (Sora Editor + TextMate):**
  - Multi-tab editing with unsaved indicators (`●`), undo/redo, word wrap, font sizing, quick symbol bar, and inline Find & Replace (case-sensitive + regex).
  - TextMate syntax highlighting for Kotlin, Java, Python, JavaScript/TypeScript, HTML, XML, Markdown, and Lua, including the custom **Antigravity Cosmic** theme (`#0B0F19`).
  - Line-by-line **Unsaved Diff** viewer and **Ask Agent** jump-to-context button.
- **Antigravity 2.0 Agent Chat:**
  - Streams structured events directly from `agy --output-format stream-json`.
  - Renders Markdown, copyable code blocks, collapsible tool call cards with clickable file-open chips, token/duration stats, model & effort selector, `/` slash commands, `@` workspace file mentions, attachments, and voice-to-prompt input.
  - Reads local session summaries (`conversation_summaries.db`) so you can resume past conversations (`--resume`).
- **Workspace File Explorer & Built-in Git:**
  - Create/clone projects, browse/filter files, create/rename/delete files & folders.
  - Built-in Git status, stage/unstage, inline diff viewer, commit, pull, push, and recent commit log.
- **Embedded Terminal, Web Preview & `agy-device`:**
  - Multi-tab Termux PTY terminal with Cosmic palette, clickable URLs (opens `localhost` right in the Preview tab!), and extra-keys toolbar.
  - Built-in WebView preview with mobile/desktop viewport toggle and JS Console drawer.
  - Autonomous ADB loopback controls (`agy-device`) and zero-setup ARM64 glibc + `agy` bootstrapping.
