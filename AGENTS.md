# Supported Agents and Protocols

## 1. OpenCode
- **Протокол**: HTTP REST + Server-Sent Events (SSE).
- **Порт по умолчанию**: 4096.
- **Аутентификация**: HTTP Basic Auth.
- **Поддержка в приложении**:
  - Remote: Этап 1 (реализуется сейчас).
  - Local (proot): запланировано на Этап 2.

## 2. Agent Client Protocol (ACP)
- **Протокол**: JSON-RPC 2.0 по stdio.
- **Агенты**: Claude Code (адаптер Zed), Gemini CLI, Codex (адаптер Zed).
- **Поддержка в приложении**: запланировано на Этап 3.
