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

## Этап 2 — Автономный локальный рантайм на телефоне (Завершён)

### Веха 1: Выделение `:data:local` (Завершена)
- [x] Создан выделенный модуль `:data:local`.
- [x] Перенесена и обновлена база данных Room до v2 (`AppDatabase`, `WorkspaceEntity`, `SessionEntity`, `MessageEntity`, `ToolCallEntity`, `DbSanitizer`).
- [x] Реализован `WorkspaceRepository` и `WorkspaceStorage`.
- [x] Написаны юнит-тесты `DbSanitizerTest`, `WorkspaceRepositoryTest`.
- [x] Пройдена верификация CI на GitHub Actions.

### Веха 2: Модуль «Проекты» (`:feature:projects`) (Завершена)
- [x] Создан модуль `:feature:projects`.
- [x] Реализован `ProjectsViewModel` (реактивное управление воркспейсами, архивация, создание, клонирование).
- [x] Реализован экран `ProjectsScreen` со списком карточек `ProjectCard` (Git ветка, dirty/clean статус, счетчик сессий, тип источника).
- [x] Реализован `CloneBottomSheet` (валидация URL, авто-детекция имени проекта, shallow clone `--depth 1`, шифрование PAT токена в Keystore через `SecretStore`).
- [x] Реализован диалог `CreateProjectDialog` с опцией инициализации `README.md`.
- [x] Интеграция Project Picker в `CreateSessionDialog` в `:feature:sessions`.
- [x] Поддержка query-параметра `directory` в OpenCode API (`/session?directory=...`).
- [x] Добавлена вкладка Projects в `NavigationSuiteScaffold` (`MainActivity.kt`).
- [x] Написаны юнит-тесты `ProjectsViewModelTest`.
- [x] Пройдена верификация CI на GitHub Actions.

### Веха 3: Подготовка PRoot и спайка (`:runtime:local`) (Завершена)
- [x] Скомпилированные бинарники `libproot.so` и `libproot-loader.so` интегрированы в `app/src/main/jniLibs/arm64-v8a/` и `jniLibs/x86_64/` (обход W^X noexec на Android 10+).
- [x] Настроены `useLegacyPackaging = true` и `android:extractNativeLibs="true"` в `app/build.gradle.kts` и `AndroidManifest.xml`.
- [x] Создан манифест рантайма `assets/local-runtime-manifest.json` с проверенными контрольными суммами SHA-256 (Alpine 3.21.3 minirootfs ~3.5-3.8MB, OpenCode musl binary 1.2.27 ~44.4MB).
- [x] Создан модуль `:runtime:local`.
- [x] Реализован `ManifestParser` с валидацией схемы и контрольных сумм.
- [x] Реализованы `ProotEnvironment` и `ProotSpikeRunner`.
- [x] Написаны юнит-тесты `ManifestParserTest`, `ProotEnvironmentTest`.
- [x] Пройдена верификация CI на GitHub Actions (Run 37107775952).

### Веха 4: Установщик рантайма (`RuntimeInstaller`) (Завершена)
- [x] Разработан `TarExtractor` на чистом Kotlin (поддержка symlinks, директорий, бинарных прав chmod, GNU LongLink, защита от Zip-Slip path traversal).
- [x] Реализован `RuntimeInstaller` с конечным автоматом состояний (`NotInstalled` -> `CheckingPrerequisites` -> `Downloading` -> `Verifying` -> `Extracting` -> `Configuring` -> `Ready`).
- [x] Реализована докачка через HTTP Range (`bytes=X-`), вычисление SHA-256 на лету, проверка свободного дискового пространства (>350 МБ).
- [x] Первичная настройка rootfs: генерация `/etc/resolv.conf` (Google/Cloudflare DNS), `/etc/hosts`, маркерный файл `.pocketcli_ready`.
- [x] Юнит-тесты на MockWebServer: `TarExtractorTest`, `RuntimeInstallerTest`.
- [x] Пройдена верификация CI на GitHub Actions (Run 37109852203).

### Веха 5: Супервайзер процессов и Foreground Service (Завершена)
- [x] Разработан потокобезопасный кольцевой буфер `CircularLogBuffer` для захвата stdout/stderr процесса.
- [x] Реализован `LocalRuntimeSupervisor`: управление жизненным циклом процесса, выделение случайного порта, генерация 24-байтного криптографического токена, автоматический перезапуск с экспоненциальным backoff (1s, 2s, 4s, макс 3 попытки), монтирование `/workspace`, health-check `/global/health` с `BasicAuth`.
- [x] Реализован `LocalRuntimeService` (Foreground Service типа `specialUse`, WakeLock, ongoing-уведомление с действием «Остановить», открытие чата по тапу, таймер простоя 10 минут).
- [x] Объявлены необходимые разрешения в `AndroidManifest.xml` (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`).
- [x] Написаны юнит-тесты `LocalRuntimeSupervisorTest`.
- [x] Пройдена верификация CI на GitHub Actions (Run 37111805723).

### Веха 6: Онбординг, настройки рантайма и интеграция UI (Завершена)
- [x] Реализован `ProviderKeyStore` и `SharedPreferencesProviderKeyStore` в `:core:security` для безопасного хранения зашифрованных в Android Keystore API-ключей моделей (Anthropic, OpenAI, Gemini, DeepSeek, OpenRouter, Groq).
- [x] Автоматическая инъекция ключей провайдеров в переменные окружения процесса OpenCode при старте.
- [x] Разработан `LocalRuntimeViewModel` в `:feature:settings` (реактивное управление состояниями установки, супервайзера, потоком логов и ключами провайдеров).
- [x] Реализован `LocalRuntimeCard`: статус-бэйджи, линейный прогресс скачивания/распаковки, кнопки «Установить», «Запустить», «Стоп», «Рестарт», «Удалить», быстрый доступ к логам и ключам.
- [x] Реализован `LogViewerDialog`: моноширинная консоль логов stdout/stderr с подсветкой синтаксиса, поисковым фильтром, кнопками копирования и очистки.
- [x] Реализован `ProviderKeysDialog`: настройка API-ключей с маскированием и переключателем видимости паролей.
- [x] Реализован `OnboardingDialog`: приветственный диалог выбора между локальным автономным запуском на телефоне и подключением к удалённому серверу.
- [x] Написаны юнит-тесты `LocalRuntimeViewModelTest`.
- [x] Пройдена верификация CI на GitHub Actions (Run 37112584531).

## Проверенные сборки и CI:
- GitHub Actions Ubuntu Latest (CI/CD pipeline):
  - **Run 37099966772** (Этап 1 MVP) — SUCCESS
  - **Run 37105396455** (Веха 1 :data:local) — SUCCESS
  - **Run 37106640268** (Веха 2 :feature:projects) — SUCCESS
  - **Run 37107775952** (Веха 3 PRoot & Spike) — SUCCESS
  - **Run 37109852203** (Веха 4 RuntimeInstaller) — SUCCESS
  - **Run 37111805723** (Веха 5 Supervisor & Service) — SUCCESS
  - **Run 37112584531** (Веха 6 Onboarding & Settings UI) — SUCCESS
