# Progress & Verification Log

## Этап 0 — Исследование API (Завершён)
- [x] Запущен живой сервер `opencode serve` (v1.2.27).
- [x] Выгружена схема OpenAPI 3.1 в `docs/opencode-openapi.json`.
- [x] Проверены реальные эндпоинты `/global/health`, `/session`, `/global/event`, `/permission`.
- [x] Сохранены реальные фикстуры в `fixtures/` (health, session created, list, full turn SSE, reconcile).
- [x] Зафиксированы результаты в `docs/opencode-api-notes.md`.

## Этап 1 — Каркас + Remote OpenCode MVP (Реализован)
- [x] Подготовка структуры проекта и документации (`THIRD_PARTY_NOTICES.md`, `AGENTS.md`, `docs/PROGRESS.md`).
- [x] Настройка Gradle Version Catalog (`libs.versions.toml`) и корневых скриптов сборки.
- [x] Настройка GitHub Actions CI/CD (`.github/workflows/android.yml`).
- [x] Реализация `:core:model` (`AgentEvent`, `Session`, `Message`, `ToolCall`, `Prompt`, `ConnectionProfile`, `AgentAdapter`).
- [x] Реализация `:core:security` (`SecretStore` с шифрованием AES-GCM через Android Keystore).
- [x] Реализация `:data:opencode` (Room DB с составным ключом `(profileId, sessionId)`, защита от переполнения CursorWindow через `DbSanitizer`, OkHttp REST клиент, SSE-клиент с `readTimeout(0)` и `BasicAuthInterceptor`, `OpenCodeAdapter`).
- [x] Юнит-тесты на MockWebServer (`OpenCodeAdapterTest`, `DbSanitizerTest`).
- [x] Реализация `AgentSessionRepository` с гибридным состоянием (Room + in-flight StateFlow) и reconcile-механизмом.
- [x] Реализация `:runtime:remote` (`RemoteTransport`).
- [x] Реализация `:core:ui` (Material 3 тема, `StreamingMarkdownText` с автозакрытием незакрытых бэктиков, `ToolCallCard` с умным превью, `ReasoningCard`, `PermissionCard`, `ToolOutputBottomSheet` с виртуализированным выводом).
- [x] Реализация `:feature:sessions` (`SessionsScreen`, создание/удаление сессий).
- [x] Реализация `:feature:chat` (`ChatScreen`, стриминг, умный автоскролл, многострочный Composer с кнопками Отправить/Стоп).
- [x] Реализация `:feature:settings` (`SettingsScreen`, управление профилями серверов с кнопкой «Проверить подключение» и переключателем Cleartext HTTP).
- [x] Реализация `:app` (`PocketCliApp` с Notification Channels, `MainActivity` с `NavigationSuiteScaffold` и адаптивной навигацией).

## Проверенные устройства и среды:
- Windows 11 (Host machine, OpenCode serve 1.2.27).
- GitHub Actions Ubuntu Latest (CI/CD pipeline).
  - **Run #10**: [SH20FK/PocketCLI Actions Run 37099966772](https://github.com/SH20FK/PocketCLI/actions/runs/37099966772) — **STATUS: SUCCESS** (Unit Tests & APK Assemble passed).
  - **Artifact 1**: pocketcli-debug-apk (15.9 MB, SHA256: 46281a899e7d8bc87cd09067ffa05c292a0d3d28558b2c51673b6fc2e5394ebe).
  - **Artifact 2**: test-reports (7.26 KB, SHA256: 277b8c25491b07a33ca9316468753eeb9585098cd799eb12a07e9a6ea5ee7cb5).
