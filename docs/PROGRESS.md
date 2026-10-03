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

## Этап 3 — UI/UX Flow и Дизайн-система Material 3 Expressive (Реализован)
- [x] Создана документация `docs/UI_REFERENCE_MAP.md` с маппингом компонентов на LastChat, Read You, Metrolist, Podium, Kori, RvKernel, MD3-Windows, DialogX.
- [x] Разработана спецификация иконки `docs/APP_ICON_SPEC.md` и геометрия знака **Pocket Prompt** (карман + терминальный шеврон `>_`).
- [x] Созданы векторные исходники иконки: `pocket-prompt-master.svg`, `pocket-prompt-outlined.svg`, `pocket-prompt-monochrome.svg`, `adaptive-icon-108.svg`.
- [x] Сгенерированы Android-ресурсы адаптивной иконки: `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml`, обновлен `AndroidManifest.xml`.
- [x] Разработана спецификация анимаций `docs/MOTION_SPEC.md` и каталог ресурсов `docs/ANIMATION_ASSETS.md`.
- [x] Реализована основа дизайн-системы `:core:ui`:
  - `PocketMotion.kt`: физические токены spring и стандартизированные длительности.
  - `PocketAnimatedIcon.kt`: бесшовные анимированные переходы Send <-> Stop, Play <-> Stop, Expand <-> Collapse, Sync <-> Check, Eye <-> EyeOff.
  - `PocketStatusPill.kt`: статус-пилюля активности агента (`Готов`, `Думает`, `Выполняет`, `Ждёт подтверждения`, `Офлайн`).
  - `PocketTwoRowsTopAppBar.kt` и `PocketAppBarWithSearch.kt`: выразительные двухрядные заголовки со встроенным поиском.
  - `PocketButtonGroup.kt`: сегментированные группы кнопок для фильтров и вкладок.
  - `PocketSplitButton.kt`, `PocketFloatingToolbar.kt`, `PocketDialog.kt`.
  - `ActiveSessionBar.kt`: персистентная поверхность выполняющейся сессии над навигацией (метафора мини-плеера).
  - `DiffViewer.kt`: единый unified diff viewer с подсветкой добавлений/удалений строк и сворачиванием контекста.
  - `PermissionDetailsSheet.kt`: модальная шторка подробностей запроса прав (Once / Always / Reject).
- [x] Модернизация раздела «Проекты» (`:feature:projects`):
  - Поиск, фильтры (`Все`, `Локальные`, `Git`) и сортировка (`SortBottomSheet`).
  - Трехуровневая карточка `ProjectCard` с индикацией активности, статусом Git и действиями.
  - Пошаговый мастер клонирования `CloneBottomSheet` с карточкой этапов `CloneProgressCard`.
  - Диалог `CreateProjectDialog` с инициализацией Git и README.md.
  - Диалог `ImportWorkspaceDialog` для импорта изолированной копии папки.
  - Полноэкранный `ProjectDetailScreen` с вкладками Обзор, Файлы, Git, Terminal.
  - Шторка быстрого выбора проекта `ProjectPickerSheet` при создании сессии.
  - Экспрессивный `ProjectsEmptyState` и `AddProjectFabMenu`.
- [x] Модернизация разделов «Чаты» и «Сессии» (`:feature:sessions`, `:feature:chat`):
  - Двухрядный заголовок с пилюлей статуса рантайма и быстрым поиском.
  - Секция «Продолжаются сейчас» (до 2 активных карточек с кнопками Open/Stop).
  - Секция «Недавние диалоги» со свайпом удаления и Undo-Snackbar.
  - Композер `PocketChatComposer` с чипами вложений, меню контекста и анимацией Send -> Stop.
  - Плавающая пилюля «К новым сообщениям» при отложенном скролле вверх.
- [x] Раздел «Настройки» и Runtime Center (`:feature:settings`):
  - Двухрядный заголовок и навигация в Runtime Center и Обновления.
  - Список поддерживаемых агентов (`AgentCardsList`): OpenCode, Claude Code, Gemini/Antigravity, Codex.
  - Экран `RuntimeCenterScreen` с телеметрией, состоянием процессов, логами и проверками окружения.
- [x] Архитектура OTA-обновлений из GitHub Releases (`:core:update` и `:feature:settings:update`):
  - Спецификация `docs/RELEASE_PROCESS.md` и схема манифеста `update.json`.
  - Модуль `:core:update`: `UpdateManifest`, `GitHubReleaseApi`, `ApkDownloader` с HTTP Range, `ApkVerifier` с SHA-256, `ApkInstaller`.
  - Экран `UpdateScreen` и `UpdateViewModel` с полным конечным автоматом (Checking -> Available -> Downloading -> Verifying -> ReadyToInstall).
  - Автоматизация CI/CD в `.github/workflows/android.yml`: динамический расчет `VERSION_CODE`, сборка APK, генерация `SHA256SUMS`, `update.json` и публикация GitHub Release по тегам `v*`.
  - Юнит-тесты `UpdateManifestTest`, `ApkVerifierTest`, `UpdateCheckerTest`.

## Проверенные сборки и CI:
- GitHub Actions Ubuntu Latest (CI/CD pipeline):
  - **Run 37099966772** (Этап 1 MVP) — SUCCESS
  - **Run 37105396455** (Веха 1 :data:local) — SUCCESS
  - **Run 37106640268** (Веха 2 :feature:projects) — SUCCESS
  - **Run 37107775952** (Веха 3 PRoot & Spike) — SUCCESS
  - **Run 37109852203** (Веха 4 RuntimeInstaller) — SUCCESS
  - **Run 37111805723** (Веха 5 Supervisor & Service) — SUCCESS
  - **Run 37112584531** (Веха 6 Onboarding & Settings UI) — SUCCESS
  - **Run 37120626978** (Этап 3 Полный UI/UX Flow, Material 3 Expressive и OTA Обновления) — SUCCESS (Артефакт `pocketcli-debug-apk` собран, все тесты пройдены)
  - **Run 37121178933** (Релизный тег `v1.0.0-beta.1`) — SUCCESS (Сформирован официальный GitHub Release [v1.0.0-beta.1](https://github.com/SH20FK/PocketCLI/releases/tag/v1.0.0-beta.1), опубликованы APK `pocketcli-1.0.0-beta.1-universal.apk`, `SHA256SUMS` и манифест `update.json`).
