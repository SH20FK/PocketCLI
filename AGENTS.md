# Supported Agents and Protocols

## 1. OpenCode
- **Протокол**: HTTP REST + Server-Sent Events (SSE).
- **Порт по умолчанию**: 4096.
- **Аутентификация**: HTTP Basic Auth.
- **Поддержка в приложении**:
  - Remote: Этап 1 (реализуется сейчас).
  - Local (proot): запланировано на Этап 2.

## 2. Agent Client Protocol (ACP)
- **Протокол**: JSON-RPC 2.0 по stdio / stream.
- **Агенты**: Claude Code (адаптер Zed / Anthropic API), Gemini / Antigravity (Google DeepMind), Codex (адаптер Zed / OpenAI API).
- **Поддержка в приложении**:
  - Интеграция протокола ACP (`AcpProtocol`, `AcpAdapter`).
  - Диалоги настройки API-ключей (`ANTHROPIC_API_KEY`, `GEMINI_API_KEY`) и выбора моделей в Настройках.
  - Селектор агентов при создании сессий с отображением бейджей в чате и списке сессий.
