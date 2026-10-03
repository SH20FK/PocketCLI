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
