# Спецификация: нативный Android-клиент для CLI-агентов (OpenCode, Claude Code, Gemini/Antigravity, Codex)

> Документ написан для ИИ-кодинг-агента (Cursor и т.п.). Читай целиком до начала работы. Все пункты с пометкой **[ПРОВЕРИТЬ]** — не подтверждены источниками, прежде чем опираться на них, проверь по официальной документации.

---

## 0. Что изменилось относительно первого плана (ВАЖНО)

| Было в плане | Проблема | Что делаем |
|---|---|---|
| Запускать OpenCode через Node.js Mobile | OpenCode — не обычное Node-приложение: его релиз — скомпилированный Bun-бинарник под glibc. На Android/Termux он падает (нет `/lib/ld-linux-aarch64.so.1`, `e_type: 2` / не-PIE). Node.js Mobile тут не поможет. | Запускаем **официальный бинарник** внутри **proot + rootfs (Alpine/Debian)** — так делает AndCode. Параллельно — режим **Remote** (OpenCode на ПК/VPS по HTTP). |
| Запуск бинарников из `filesDir` через JNI `fork/execve` | При `targetSdk >= 29` Android запрещает `exec()` файлов из домашней директории приложения (W^X). | Исполняемые файлы (proot и т.п.) кладём в APK как `jniLibs/<abi>/libXXX.so` и запускаем из `nativeLibraryDir`. Всё остальное (агенты) исполняется *внутри* proot. |
| Парсить stdout агентов через PTY как fallback | Хрупко. | Основной протокол для не-OpenCode агентов — **ACP** (JSON-RPC по stdio). PTY — только для встроенного терминала. |
| Foreground Service «и всё» | На Android 12+ есть «phantom process killer», на 14+ обязателен тип foreground service. | См. раздел 8 (риски). |

**Главный вывод:** такой проект уже существует (AndCode, MIT). Мы не изобретаем runtime с нуля — **читаем и адаптируем его подходы**, а своё отличие делаем в UI/UX и в ACP-слое.

---

## 1. Цель и принципы

Нативное Android-приложение (Kotlin + Jetpack Compose + Material 3), которое даёт чат-интерфейс к CLI-агентам: сессии, стриминг ответов, карточки вызовов инструментов с Approve/Reject, diff-просмотр, файловое дерево, терминал. Без Termux у пользователя.

Принципы: чат вместо терминала · Material You (dynamic color) · агент-агностичность через адаптеры · ключи/токены никогда не уходят на наши серверы (у нас нет бэкенда) · честные предупреждения о безопасности.

---

## 2. Разведка: что уже сделано другими (можно брать идеи и код)

| Проект | Что это | Лицензия | Что взять |
|---|---|---|---|
| **AndCode** — github.com/yuga-hashimoto/and-code | Нативный Compose-GUI; OpenCode, Claude Code, Antigravity, Codex; on-device через PRoot (Alpine/Debian) + Remote OpenCode по LAN/Tailscale | MIT (только код приложения; сторонние CLI — свои лицензии) | Всё: manifest рантайма с SHA-256, установка rootfs, запуск агента на `127.0.0.1:4097`, SSE-таймлайн, approvals, handoff между рантаймами, расписания, QR-подключение, Keystore, 8 языков. **Первым делом прочитать `docs/LOCAL_RUNTIME.md`, `docs/CODEX.md`, `docs/ANTIGRAVITY.md`, `AGENTS.md`, `app/src/main/assets/local-runtime-manifest.json`.** |
| **Cinder** — huggingface.co/SmallAICreator/cinder | Мобильный клиент: настоящий `claude` CLI в proot + 4 МБ Alpine, Compose-UI рендерит вывод как транскрипт | код приложения MIT; proot GPL-2.0 | UI транскрипта в стиле Claude Code: tool cards, Edit-diff, сворачиваемые результаты, «code canvas» с кнопкой copy, скачивание созданных агентом файлов. Описание проблем запуска 250 МБ бинарника. |
| **PocketCode** — github.com/rajbreno/PocketCode | Termux-скрипты для OpenCode/Claude/Codex/Gemini | — | Только как справочник по установке (нам не архитектурно). |
| **android-ai-stack** — github.com/toolazytoname/android-ai-stack | OpenCode + Claude Code + Happy на телефоне, раздельные профили провайдеров | — | Идея раздельных профилей/маршрутов моделей для каждого агента; чек-листы приёмки. |
| **opencode-termux** (HanSoBored, guysoft, Hope2333, Netsnake-TN) | Способы запуска Bun-бинарника OpenCode нативно в Termux: glibc-loader (`glibc-runner`), shim для `statx`, сборки Bun под bionic | см. репозитории | Справочник по подводным камням: seccomp блокирует `statx`, pointer tagging на новых Android, `LD_PRELOAD`. Пригодится на этапе оптимизации (замена proot). |
| **ACP** — agentclientprotocol.com | Открытый JSON-RPC-протокол клиент↔агент (создан Zed) | открытый | Основной протокол для Claude Code (через адаптер), Gemini CLI, Codex (через адаптер), OpenCode и др. |

---

## 3. Источники по дизайну (вдохновение, не копипаст без проверки лицензии)

| Репозиторий | Что это (проверено / нет) | Что смотреть | Лицензия-ловушка |
|---|---|---|---|
| ReadYouApp/ReadYou | Проверено: RSS-ридер на Compose в стиле Material You; в кредитах — Monet-движок от Kyant0 | Динамические цвета, адаптивные экраны, Settings-экраны, типографика статей (пригодится для рендера Markdown) | **GPL-3.0** — копирование кода делает твоё приложение GPL-3.0 |
| MetrolistGroup/Metrolist | Проверено: Kotlin, Compose + Material 3, клиент YouTube Music | Плеер-экран, bottom sheets, переходы, анимации, структура модулей | **GPL-3.0** |
| gokadzev/Musify | Не проверял в этой сессии. Насколько знаю — Flutter-приложение **[ПРОВЕРИТЬ]** | Только визуальные идеи | проверить |
| kongzue/DialogX | Не проверял. Насколько знаю — Android-библиотека диалогов на классических Views, не Compose **[ПРОВЕРИТЬ]** | Идеи анимаций диалогов; в Compose использовать `ModalBottomSheet`/`AlertDialog` M3 | проверить |
| Runixe786/MD3-Windows | Не удалось верифицировать, что это за проект **[ПРОВЕРИТЬ]** | — | проверить |

Правило для агента: **перед копированием любого кода открыть LICENSE репозитория-источника и зафиксировать в `THIRD_PARTY_NOTICES.md`**. Если нужен код с GPL — либо весь проект GPL-3.0, либо переписываем идею своими словами (идеи и общие паттерны не защищены, код — да).

---

## 4. Архитектура

```
UI (Compose M3)         Chat · Sessions · Files/Diff · Terminal · Settings
        │
ViewModels / Domain     AgentSessionManager, ApprovalManager, WorkspaceRepository
        │
AgentAdapter interface  ┌ OpenCodeAdapter  (HTTP + SSE)
                        ├ AcpAdapter       (JSON-RPC 2.0, stdio)  ← Claude/Gemini/Codex/OpenCode-acp
                        └ RawPtyAdapter    (только для терминала)
        │
Transport/Runtime       ┌ RemoteTransport  (URL + Basic auth, Tailscale/LAN)
                        └ LocalRuntime     (proot + rootfs, процессы агента)
        │
Android platform        Foreground Service · Keystore · SAF/All-files · Notifications
```

### 4.1 Единая модель событий (ключевое решение)

Все адаптеры приводят вывод к одному sealed-интерфейсу — UI знает только его:

```kotlin
sealed interface AgentEvent {
    data class SessionStatus(val id: String, val state: State) : AgentEvent   // idle/busy/error
    data class MessageStarted(val sessionId: String, val messageId: String, val role: Role) : AgentEvent
    data class TextDelta(val messageId: String, val text: String) : AgentEvent
    data class ReasoningDelta(val messageId: String, val text: String) : AgentEvent
    data class ToolCallUpdate(val messageId: String, val callId: String, val name: String,
                              val status: ToolStatus, val input: JsonElement?, val output: String?) : AgentEvent
    data class PermissionRequested(val requestId: String, val callId: String?, val title: String,
                                   val options: List<PermissionOption>) : AgentEvent
    data class PlanUpdate(val entries: List<PlanEntry>) : AgentEvent
    data class FileDiff(val path: String, val unifiedDiff: String) : AgentEvent
    data class Error(val message: String, val recoverable: Boolean) : AgentEvent
}

interface AgentAdapter {
    val capabilities: Set<Capability>            // Streaming, Permissions, Plan, Diff, Images…
    suspend fun connect(): Result<Unit>
    fun events(sessionId: String): Flow<AgentEvent>
    suspend fun createSession(workspace: Workspace): Session
    suspend fun listSessions(): List<Session>
    suspend fun sendPrompt(sessionId: String, prompt: Prompt)
    suspend fun cancel(sessionId: String)
    suspend fun respondPermission(requestId: String, optionId: String)
    suspend fun disconnect()
}
```

### 4.2 OpenCodeAdapter (HTTP + SSE)

Факты из официальной документации OpenCode (https://opencode.ai/docs/server/):
- `opencode serve [--port] [--hostname] [--cors]` — headless HTTP-сервер с OpenAPI 3.1; спецификация на `http://<host>:<port>/doc` (по умолчанию порт 4096).
- Авторизация: переменная `OPENCODE_SERVER_PASSWORD` включает HTTP Basic; логин по умолчанию `opencode` (`OPENCODE_SERVER_USERNAME` меняет).
- `GET /global/health` → `{healthy, version}`; `GET /global/event` и `GET /event` — SSE; первый event — `server.connected`.

Задачи агенту:
1. Скачать `/doc` с живого сервера, **сгенерировать Kotlin-клиент** (openapi-generator, kotlinx.serialization + Ktor/OkHttp) либо руками описать минимально нужные эндпоинты (sessions, messages, prompt, permissions, abort, file/diff).
2. SSE через `okhttp-sse` или Ktor SSE; экспоненциальный реконнект.
3. **SSE — «best-effort»**: после реконнекта/возврата из фона делать *reconcile* — перезапросить состояние сессии и сообщений (этот совет даёт автор Rust-клиента `opencode-codes`). Не доверять одному потоку.
4. Маппинг bus-событий OpenCode → `AgentEvent`. Неизвестные типы событий **игнорировать с логом**, а не падать (API ещё меняется).
5. Версию сервера проверять через `/global/health` и показывать в настройках; в манифесте фиксировать протестированную версию.

### 4.3 AcpAdapter (JSON-RPC по stdio)

Факты: ACP — открытый стандарт на JSON-RPC 2.0; в списке агентов — Claude Code (через адаптер Zed), Codex CLI (через адаптер Zed), Gemini CLI (референсная реализация), OpenCode, Goose, Kimi CLI и др. Есть официальные библиотеки, включая Kotlin (agentclientprotocol.com → Libraries).

Задачи агенту:
1. Подключить Kotlin SDK ACP **[ПРОВЕРИТЬ актуальные координаты Maven на странице Libraries → Kotlin]**.
2. Запуск агента как процесса внутри runtime; stdin/stdout → транспорт SDK. Stderr — в лог.
3. Реализовать клиентскую сторону: `initialize` (capabilities), `session/new`, `session/prompt`, обработка `session/update` (текст, tool calls, plan), `session/request_permission` → `PermissionRequested`, методы файловой системы и терминала, которые агент может вызывать у клиента (fs read/write, terminal) — реализовать поверх workspace с проверкой путей (не выходить за корень проекта).
4. Конкретные команды запуска (npm-пакеты адаптеров, `gemini --experimental-acp`, `opencode acp` и т.д.) **брать из ACP Registry/доков агента [ПРОВЕРИТЬ]** и хранить в `agents.json`, а не хардкодить.
5. Claude Code и Codex через ACP требуют Node.js внутри rootfs (адаптеры — npm-пакеты): значит в Alpine/Debian rootfs ставим `nodejs`/`npm`. Это «настоящий» Node, а не Node.js Mobile.

### 4.4 Runtime-слой

**Режим A — Remote (делаем первым, он самый простой):** URL + логин/пароль, mDNS/QR/Tailscale. Нужен только `OpenCodeAdapter`.

**Режим B — Local (proot):** по рецепту AndCode:
1. В APK: `libproot.so` (+ loader) в `jniLibs/arm64-v8a` (и `x86_64` для эмулятора). `android:extractNativeLibs="true"`, `useLegacyPackaging = true` в Gradle, чтобы файлы лежали на диске в `nativeLibraryDir`.
2. Скачать rootfs (Alpine minirootfs; Debian — если бинарник под glibc) с официального CDN, **проверить SHA-256 из pinned-манифеста**.
3. Распаковать в `filesDir/rootfs` (внимание: симлинки, права, hardlinks — использовать commons-compress/tar с сохранением).
4. Внутри: `apk add git bash curl ripgrep ca-certificates nodejs npm`.
5. Скачать бинарник агента из GitHub Releases (с проверкой SHA-256), положить в rootfs.
6. Запуск: `libproot.so -r <rootfs> -b /dev -b /proc -b /sys -b <workspace>:/workspace -w /workspace <agent> serve --hostname 127.0.0.1 --port <random>`; **пароль генерировать на каждый запуск**, хранить в памяти.
7. Health-check `/global/health`, затем `OpenCodeAdapter` подключается как к remote.

Оптимизация (после MVP, необязательно): отказ от proot в пользу glibc-loader/bionic-сборок — см. opencode-termux; выигрыш в скорости файловых операций, но выше хрупкость.

### 4.5 Хранение секретов

- Android Keystore: мастер-ключ (AES-GCM, без экспорта) → шифрование API-ключей и паролей подключений; хранение в DataStore/Room (BLOB).
- OAuth-токены CLI (Claude, Codex, Antigravity) остаются **внутри rootfs**, как у официального CLI; приложение их не копирует (как в AndCode).
- Нет собственного бэкенда: запросы идут напрямую от CLI к провайдеру.

### 4.6 Фоновая работа

- Foreground Service с типом, подходящим под задачу (Android 14+ требует объявить `foregroundServiceType`) **[ПРОВЕРИТЬ какой тип и разрешения нужны для долгого запуска процессов, и лимиты на Android 15]**.
- Уведомление: статус агента, кнопки «Открыть», «Остановить».
- Phantom process killer (Android 12+) может убивать дочерние процессы приложения **[ПРОВЕРИТЬ актуальные детали и обходы]** → в настройках показать подсказку и логировать причины завершения процесса (`ApplicationExitInfo`).

---

## 5. UI/UX (Material 3)

**Тема:** `dynamicLightColorScheme/dynamicDarkColorScheme` на Android 12+, fallback — статичная схема; AMOLED-чёрная тема переключателем. Material 3 Expressive — по желанию **[ПРОВЕРИТЬ статус API в используемой версии `material3`]**.

**Навигация:** `NavigationSuiteScaffold` (material3-adaptive-navigation-suite) — сам выбирает NavigationBar/Rail/Drawer; `ListDetailPaneScaffold` для планшетов (список сессий + чат). Навигация — Navigation Compose (или Navigation 3 **[ПРОВЕРИТЬ стабильность]**).

**Экраны:**
1. **Чат.** `LazyColumn` с `key` по messageId. Блоки: пользовательское сообщение · ответ агента (Markdown) · сворачиваемый Reasoning · **ToolCallCard** (иконка по типу: read/edit/bash/search, статус spinner/ok/error, раскрывающийся input/output) · **PermissionCard** (Approve once / Always / Reject — варианты берём из события) · PlanCard (чек-лист) · DiffCard (inline unified diff, подсветка +/−). Composer: многострочное поле, выбор модели/агента, режимы разрешений (Plan / Ask / Auto-edit / Full — с предупреждением на «Full»), кнопка Stop.
2. **Сессии.** Список (статус, последнее сообщение, время), поиск, FAB «Новая сессия» → выбор агента, рантайма, рабочей папки.
3. **Файлы/Git.** Дерево, просмотр с подсветкой синтаксиса, статус git и diff (через CLI git внутри runtime).
4. **Терминал.** Для интерактива: компонент `terminal-view`/`terminal-emulator` из Termux **[ПРОВЕРИТЬ лицензии модулей]** либо xterm.js в WebView; подключается к PTY внутри proot.
5. **Настройки.** Агенты (установка/обновление/версия), провайдеры и ключи, MCP-серверы, тема/язык, юридическая информация.

**Markdown/код:** `mikepenz/multiplatform-markdown-renderer` **[ПРОВЕРИТЬ совместимость с твоей версией Compose]**; для подсветки — отдельная библиотека (Highlights/Prism-подобная). Стриминг: инкрементальный рендер — не пересоздавать весь Markdown на каждый дельта-токен (батчить обновления ~30–50 мс).

**Производительность чата:** стабильные ключи, `derivedStateOf`, ограничение размера output в карточке (обрезка + «показать полностью»), отдельный `Dispatchers.Default` для парсинга Markdown.

**Что подсмотреть в дизайн-репо:** ReadYou — адаптивность и settings-экраны; Metrolist — bottom sheets, анимации переходов, структура модулей; Cinder — вид транскрипта и tool-карточек.

---

## 6. Структура проекта

```
:app                      — Application, навигация, DI-граф
:core:model               — AgentEvent, Session, Workspace, Prompt (чистый Kotlin)
:core:ui                  — тема, компоненты (ToolCallCard, DiffView, MarkdownText)
:core:security            — Keystore, SecretStore
:data:opencode            — OpenCodeAdapter + сгенерированный API-клиент
:data:acp                 — AcpAdapter
:runtime:remote           — подключения, mDNS, QR
:runtime:local            — proot, rootfs, manifest, installer, process supervisor
:feature:chat | :feature:sessions | :feature:files | :feature:terminal | :feature:settings
```

Стек: Kotlin, Compose BOM (последний стабильный), Hilt или Koin, Room, DataStore, kotlinx.serialization, Ktor/OkHttp, Coil, Coroutines/Flow, detekt + spotless, JUnit + Turbine + MockWebServer, Compose UI tests. minSdk — 26 (или 28; определить по требованиям proot-запуска), targetSdk — актуальный.

---

## 7. План по этапам (каждый этап заканчивается рабочим APK)

**Этап 0 — Исследование (1–2 дня).** Прочитать документы AndCode (список выше). Поднять `opencode serve` на ПК, скачать `/doc`, сделать curl-прогон: создать сессию, отправить prompt, посмотреть SSE. Результат: `docs/opencode-api-notes.md`.
*Приёмка:* заметки содержат реальные примеры JSON-событий.

**Этап 1 — Каркас + Remote OpenCode MVP.** Модули, тема M3, навигация, подключение по URL/паролю, список сессий, чат со стримингом текста, tool cards (read-only), approvals.
*Приёмка:* на реальном телефоне подключиться к OpenCode на ПК через Tailscale/LAN, отправить задачу, увидеть стриминг и одобрить tool call.

**Этап 2 — Local runtime (proot).** Installer rootfs + агент OpenCode, supervisor процесса, Foreground Service.
*Приёмка:* «Set up on this device» с нуля ≤ 5 мин на хорошем канале; чат работает без ПК; после сворачивания приложения на 10 минут сессия жива (или корректно сообщает о смерти процесса и восстанавливается).

**Этап 3 — ACP.** AcpAdapter + Gemini CLI (референс) → Claude Code → Codex.
*Приёмка:* один и тот же ChatScreen работает с тремя агентами без агент-специфичного кода в UI.

**Этап 4 — Файлы, diff, git, терминал.**
**Этап 5 — Полировка:** расписания, handoff между рантаймами, виджет, голосовой ввод, локализация (RU/EN минимум), edge-to-edge, предиктивный back, доступность (TalkBack, размеры шрифта).
**Этап 6 — Релиз:** сборки на GitHub Releases + F-Droid-метаданные; Google Play — **[ПРОВЕРИТЬ политики о скачивании/исполнении кода]**, вероятно проблемно.

---

## 8. Риски и меры

| Риск | Мера |
|---|---|
| W^X: нельзя `exec` из `filesDir` (targetSdk ≥ 29) | Бинарники proot в `jniLibs`; агенты — внутри proot. Альтернатива: вызов через `/system/bin/linker64` (описано в статье на Habr) — запасной вариант. |
| Bun/glibc бинарники не стартуют на bionic | proot + glibc/Alpine rootfs; манифест с pinned-версиями; тест-матрица устройств |
| Размер rootfs и бинарников (сотни МБ) | Скачивание после установки APK, прогресс, возобновление, проверка места |
| Скорость файловых операций в proot (ptrace) | Хранить репозитории в rootfs/приватной папке, не на `/sdcard`; оптимизации — этап после MVP |
| Агент с Full Access выполняет опасные команды | Предупреждение при включении; по умолчанию Ask; proot **не** является песочницей (прямо указано в README AndCode) |
| Убийство процессов системой | Foreground Service, `ApplicationExitInfo`, авто-реконнект и reconcile |
| API OpenCode меняется | Фиксируем версию, толерантный парсинг, контрактные тесты на сохранённых JSON |
| Лицензии (GPL в дизайн-репо, proot GPL-2.0) | `THIRD_PARTY_NOTICES.md`, проверка перед копированием; proot запускается как отдельный процесс |
| Товарные знаки / ToS провайдеров | Дисклеймеры «не аффилировано», пользователь входит в *свой* аккаунт, мы не ретранслируем токены |

---

## 9. Инструкция для ИИ-агента по работе над проектом

1. Работай этапами из раздела 7; не переходи к следующему, пока не выполнена приёмка.
2. Любой факт про внешний API/протокол сначала проверяй по первоисточнику (ссылки в разделе 10); не выдумывай эндпоинты — тяни `/doc` у живого сервера.
3. Не добавляй зависимости без записи в `THIRD_PARTY_NOTICES.md` с лицензией.
4. Не хардкодь пути, версии и команды агентов — только `local-runtime-manifest.json` и `agents.json`.
5. Каждый адаптер покрывай тестами на записанных фикстурах событий.
6. Секреты не логировать. Перед коммитом — grep по ключам/паролям.
7. После каждого этапа обновляй `docs/PROGRESS.md`: что сделано, что не работает, что проверено на каких устройствах.
8. Если пункт помечен **[ПРОВЕРИТЬ]** — сначала выясни, потом пиши код.

**Стартовый промпт для агента (Этап 1):**
> Прочитай `android-agent-client-spec.md`. Создай multi-module Android-проект (Kotlin, Compose, Material 3, dynamic color) по разделу 6. Реализуй `:core:model` с `AgentEvent` и `AgentAdapter` из раздела 4.1, затем `:data:opencode` с подключением к `opencode serve` по HTTP Basic и SSE (`/global/event`), с reconcile после реконнекта. Построй ChatScreen со стримингом, ToolCallCard и PermissionCard. Проверь на MockWebServer-фикстурах и на реальном сервере. Не трогай local runtime на этом этапе.

---

## 10. Источники

- OpenCode Server docs — https://opencode.ai/docs/server/
- Rust-клиент `opencode-codes` (советы по SSE/reconcile) — https://docs.rs/opencode-codes/latest/src/opencode_codes/lib.rs.html
- Agent Client Protocol: список агентов — https://agentclientprotocol.com/overview/agents
- Zed: внешние агенты по ACP — https://zed.dev/docs/ai/external-agents
- Обзор ACP и SDK — https://www.danilchenko.dev/posts/agent-client-protocol/
- AndCode — https://github.com/yuga-hashimoto/and-code (Product Hunt: https://www.producthunt.com/posts/1243053)
- Cinder — https://huggingface.co/SmallAICreator/cinder
- PocketCode — https://github.com/rajbreno/PocketCode
- android-ai-stack — https://github.com/toolazytoname/android-ai-stack
- Проблема запуска бинарника OpenCode на Android: https://github.com/anomalyco/opencode/issues/10504 и https://github.com/anomalyco/opencode/issues/11689
- opencode-termux (glibc-loader, statx shim): https://github.com/HanSoBored/opencode-termux ; https://github.com/Netsnake-TN/opencode-termux-fork ; https://github.com/guysoft/opencode-termux ; https://github.com/Hope2333/opencode-termux
- Android W^X / Termux: https://github.com/termux/termux-packages/wiki/Termux-and-Android-10 ; https://termux.dev/en/posts/general/2024/11/11/termux-selected-for-nlnet-ngi-mobifree-grant.html ; запуск через linker — https://habr.com/en/articles/943188
- Дизайн: https://github.com/ReadYouApp/ReadYou · https://github.com/MetrolistGroup/Metrolist · https://github.com/gokadzev/Musify · https://github.com/kongzue/DialogX · https://github.com/Runixe786/MD3-Windows
