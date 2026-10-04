# PocketCLI v1.0.9-beta.5 — полный аудит проекта

> **Вердикт:** проект уже похож на рабочий технический прототип, но пока не на стабильное приложение. Главная проблема не только в визуале. В репозитории одновременно присутствуют **скомпрометированная цепочка подписи APK**, несколько функций-заглушек, неверно заявленные возможности агентов, критическая утечка секретов в логи и разорванные пользовательские сценарии. Косметический редизайн поверх этого закрепит проблемы, а не исправит их.

## 1. Что именно проверено

Аудит выполнен по свежему клону коммита [`f5541d7`](https://github.com/SH20FK/PocketCLI/commit/f5541d7894639b1215ce13fb3ff702d85c7021ac) и релизу [`v1.0.9-beta.5`](https://github.com/SH20FK/PocketCLI/releases/tag/v1.0.9-beta.5).

Проверено:

- 123 production Kotlin-файла, примерно 19 тыс. строк;
- навигация, все доступные экраны и ViewModel;
- OpenCode, Claude/Codex, Antigravity, local runtime и remote profiles;
- Room, хранилище ключей и миграции;
- экран OTA, скачивание, проверка APK и release workflow;
- Compose UI, motion, accessibility и производительность;
- тесты, Gradle и GitHub Actions;
- опубликованные assets последнего GitHub Release и runtime packages.

Ограничение: UI не запускался на Android-эмуляторе — в репозитории отсутствует настоящий Gradle Wrapper, а системного Gradle в среде нет. Поэтому динамические выводы основаны на исходниках, пользовательском скриншоте и проверке опубликованных артефактов. Это само по себе является проблемой воспроизводимости сборки.

## 2. Итоговая оценка

| Область | Оценка | Статус |
|---|---:|---|
| Безопасность релиза | 1/10 | критично |
| Честность заявленного функционала | 3/10 | критично |
| Стабильность local runtime | 5/10 | частично исправлено |
| Чат и streaming pipeline | 4/10 | функционален, но архитектурно тяжёлый |
| Проекты / Git / файлы / terminal | 2/10 | большая часть — прототип или заглушка |
| OTA updater | 5/10 | релиз находится, проверка неполная |
| UI/визуальная система | 4/10 | компоненты есть, цельного продукта нет |
| Motion | 3/10 | локальные эффекты без общей системы |
| Accessibility | 2/10 | системно не проверялась |
| Тестирование и CI | 3/10 | только JVM happy-path |
| Готовность к публичному использованию | 2/10 | не выпускать как stable |

### P0 — блокеры до любой новой фичи

1. Отозвать текущую release-подпись: keystore и пароли опубликованы в Git.
2. Убрать токены и API keys из команды, попадающей в Supervisor logs.
3. Перестать называть forwarding-заглушку «ACP» и Claude/Codex рабочими агентами.
4. Исправить дублирование prompt для Antigravity и небезопасный dedup одинаковых сообщений.
5. Либо реализовать настоящий Git clone/files/diff/terminal, либо явно пометить их «не готово».
6. Сделать updater проверяющим точный package name, возрастающий versionCode и certificate; сейчас часть проверки написана, но не вызывается.

---

## 3. Критическая безопасность

### 3.1 Release signing key скомпрометирован

В Git отслеживается [`app/pocketcli-release.jks`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/pocketcli-release.jks), а [`app/build.gradle.kts`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/build.gradle.kts) содержит fallback-пароли `pocketcli_release_2026`. Debug-сборка также подписывается этим ключом.

**Последствие:** любой человек может собрать вредоносный APK с тем же package/signature и выдать его за обновление PocketCLI. SHA-256 в GitHub Release не спасает, потому что атакующий уже владеет signing identity.

**Что делать:**

- немедленно удалить ключ из истории и считать его безвозвратно скомпрометированным;
- создать новый offline keystore, хранить только в защищённых secrets;
- для уже установленных beta APK безопасный переход на новый ключ обычным обновлением невозможен: потребуется uninstall/reinstall либо новый `applicationId`;
- разделить debug/release keys;
- после ротации опубликовать certificate fingerprint в документации релиза.

### 3.2 Секреты попадают в runtime log

[`Supervisor.kt`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/src/main/java/com/pocketcli/runtime/Supervisor.kt) логирует полный `prootCmd`. Внутри guest command передаются `OPENCODE_SERVER_PASSWORD` и provider API keys. Runtime log можно просматривать и копировать из UI.

**Исправление:** передавать секреты через отдельный environment map, никогда не сериализовать их в командную строку, в logger применять allowlist/redaction и отдельный тест «secret never appears in logs».

### 3.3 OAuth Antigravity небезопасен

В [`AntigravityAuthManager.kt`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/src/main/java/com/pocketcli/agent/antigravity/AntigravityAuthManager.kt) client secret лишь XOR-обфусцирован и извлекается из APK. Authorization URL не использует полноценную пару `state + PKCE`; loopback/deep-link callback принимает code без надёжной связи с начатой сессией. [`MainActivity.kt`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/src/main/java/com/pocketcli/MainActivity.kt) принимает `pocketcli://auth` без проверки `state`.

**Исправление:** Authorization Code + PKCE S256, криптографический state, одноразовый verifier, точное redirect binding, таймаут и очистка pending flow. Public Android client не должен полагаться на client secret.

### 3.4 Слишком широкая network policy

[`network_security_config.xml`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/src/main/res/xml/network_security_config.xml) глобально разрешает cleartext и доверяет user-installed CA. UI-переключатель профиля не компенсирует глобальную политику.

**Исправление:** HTTPS по умолчанию на уровне platform policy; cleartext — только точечное явно подтверждённое исключение для LAN-профиля.

### 3.5 Хранилище и база

- `allowBackup=true`, но нет явной backup policy для encrypted preferences; восстановленная ciphertext без старого Keystore key может привести к ошибке дешифрования.
- Room использует `fallbackToDestructiveMigration()` и `exportSchema=false`: неизвестный upgrade может молча стереть историю.
- `getSessionBySessionId` не учитывает profile, поэтому одинаковые remote session IDs потенциально пересекаются.

**Исправление:** schema export, полный набор migrations, тест migrations, composite lookup `(profileId, sessionId)`, понятное recovery-поведение для недешифруемых secrets.

---

## 4. Разбор экранов и сценариев

## 4.1 Запуск и onboarding

### Что есть

В коде существует `OnboardingDialog`, ViewModel вычисляет first-run state, предусмотрен выбор local/remote.

### Что не работает

- приложение всегда стартует сразу в Sessions;
- Settings получает onboarding state, но не отображает диалог;
- пользователь не получает объяснение runtime, размера загрузки, remote/local выбора и рисков;
- нет восстановления незавершённой установки как отдельного UX-состояния.

### Как должно быть

`Splash/recovery → Welcome → Local или Remote → Проверка/установка → Workspace → Первый чат`. Каждый этап должен иметь progress, ошибку, retry и «открыть офлайн». Onboarding должен быть полноценным route, а не условным dialog внутри Settings.

## 4.2 Экран «Сессии»

### Плюсы

- есть поиск;
- активные и недавние сессии разделены;
- появилась постоянная ActiveSessionBar;
- карточки показывают проект, модель и состояние.

### Недоработки

- карточки перегружены одинаково акцентными pills и плохо сканируются;
- sync не показывает loading/error/last synced;
- удаление выполняется только локально: remote session может вернуться при следующей синхронизации;
- Undo только повторно вставляет локальную запись;
- нет rename/archive/multi-select;
- `collectAsState`, а не lifecycle-aware collection;
- создание сессии предлагает недоступные или фиктивные агенты без предупреждения.

### Редизайн

Активные сессии — максимум две крупные карточки. Недавние — плотные rows с одним главным заголовком и одной строкой metadata. Статус показывать текстом + иконкой, а не несколькими competing chips. Sync/error — inline banner над списком.

## 4.3 Диалог новой сессии

### Проблемы

- agent chips могут не помещаться при узком экране и font scale 200%; нужен `FlowRow`;
- Codex вообще отсутствует, хотя в настройках заявлен;
- Claude может выглядеть доступным, даже когда настоящего backend нет;
- «добавить проект» только закрывает picker;
- выбранный workspace не всегда доходит до фактического создания сессии из Project Detail.

### Требование

Каждый agent item должен показывать `Готов / Требует настройки / Пока не поддерживается`. Нельзя silently fallback на другой backend. Кнопка создания недоступна до валидного сочетания runtime + agent + model + workspace.

## 4.4 Экран чата

### Визуальные проблемы

Пользовательский скриншот подтверждает проблемы исходников:

- top bar разъезжается: крупный номер сессии, статус, avatar и model selector конкурируют;
- message rhythm неравномерный, большие пустоты выглядят как сломанная лента;
- пустой user bubble появляется без текста;
- assistant text висит отдельно без понятной группировки turn;
- composer слишком высокий и визуально тяжелее контента;
- Stop выглядит как чёрный квадрат внутри розового круга без ясной связи с running state;
- нет выразительного состояния streaming/tool execution.

### Архитектурные проблемы

- `syncNodesAndComposer()` пересобирает весь список nodes при каждом update;
- каждый streaming delta делает конкатенацию строки, копию map, запись/merge и новую проекцию списка — риск O(n²) и jank на длинных ответах;
- автоскролл реагирует на `nodes.size`, но не на рост текущего streaming текста;
- `StreamingTail.visibleText` получает последний chunk, а не накопленный текст;
- active diff может отображаться одновременно полноразмерно и summary-node;
- permissions/diff/todos добавляются в конец вместо причинной привязки к tool call;
- details permission state существует, но действие открытия почти не связано с timeline UI;
- Files в top bar — no-op;
- overflow фактически открывает model picker, хотя семантически это другое действие;
- `collectAsState` не учитывает lifecycle.

### Ошибки данных

Оптимистическое user message затем удаляется по совпадению **текста**, а не client request ID. Если пользователь дважды честно отправил «привет», код может удалить не то сообщение. Для Antigravity текущий prompt сначала сохраняется в БД, затем history читается из БД и этот же prompt ещё раз добавляется в request — модель получает его дважды.

### Что исправить

1. Ввести `clientMessageId/requestId` и reconciliation по ID.
2. Хранить streaming buffer отдельно; обновлять UI пакетами 50–100 мс, Room — реже или только checkpoint/final.
3. Использовать immutable per-turn model с stable keys; не перестраивать старые turns.
4. Один causal timeline: `user → assistant text → tool → permission → result → diff`.
5. Composer: стабильная высота controls, `animateContentSize`, 48dp targets, Send↔Stop morph.
6. Автоскролл только если пользователь у конца; на рост tail — coalesced scroll, иначе floating «К новому».
7. Убрать пустые bubbles на уровне renderer и state validation.

## 4.5 Model picker

### Почему он лагает

- список может содержать сотни моделей;
- выбор идентифицируется только `modelId`, без provider namespace;
- модели загружаются несколькими слоями;
- нет evidence профилирования/benchmark;
- capability chips и сложные rows увеличивают стоимость layout;
- hardcoded animations не связаны с общей motion scheme.

### Исправление

Ключ `providerId:modelId`, один owner загрузки, immutable indexed list, debounce search, grouped lazy list со stable keys, favorites отдельно, без тяжёлых измерений в каждой строке. Добавить macrobenchmark на открытие sheet, поиск и выбор при 50/300/1000 моделях.

## 4.6 Tool calls, permissions, diff, todos

Сейчас компоненты визуально существуют, но pipeline неполный:

- OpenCode события частично мапятся;
- Antigravity вообще не генерирует настоящие tools/permissions/diff, хотя capabilities заявлены;
- permission details не доведены до понятного действия;
- diff может дублироваться;
- нет доказанного восстановления pending approval после process death.

Нужна единая state machine узла, persisted event ID, dedup по event ID и восстановление pending states из backend snapshot.

## 4.7 Экран «Проекты»

### Список

Визуально карточки стали лучше, но часть metadata вычисляется эвристически и создаёт ложную уверенность. Архивные записи теряют detail. Git status пересчитывается рекурсивным проходом файлов и может быть дорогим.

### Clone repository — фактически не clone

UI спрашивает URL/token/shallow и показывает стадии, но repository создаёт директорию, README и metadata. `git clone` не запускается, shallow option не используется, стадии verify/unpack не имеют реального процесса.

### Import folder — фактически не import

Нет Storage Access Framework и выбора папки. Вводится имя, затем создаётся новая пустая директория.

### Create project

Переключатель `Initialize Git` не приводит к `git init`. Это ещё один UI control без поведения.

### Что делать

До реализации переименовать действия честно: «Создать локальную папку». Настоящий clone должен иметь credential handling, progress, cancel, cleanup partial directory, branch/depth, certificate errors и retry. Import — через SAF/tree URI с ясным ограничением доступа.

## 4.8 Project Detail

### Overview

Показывает runtime/source type, но не реальное здоровье runtime. «Новый чат» возвращает на Sessions и теряет workspace context.

### Files

Только список корня. Нет открытия, навигации, breadcrumbs, preview/source, поиска, context action.

### Git

Выводятся захардкоженные `src/main/App.kt`, `+14/-2` и демонстрационный diff. Это нужно удалить немедленно: фальшивые изменения опаснее пустого состояния.

### Terminal

Статический декоративный текст, настоящего PTY/процесса нет.

### Delete

Проект удаляется сразу без confirmation; запись БД удаляется раньше файлов, поэтому ошибка filesystem оставляет orphan data без recovery.

### Требование

Пока функции не реализованы, tabs должны быть disabled с подписью «Появится позже». MVP Project Detail: overview + реальный file browser read-only + «начать чат в этом workspace». Git/terminal добавлять только после рабочей backend-реализации.

## 4.9 Настройки

### Что есть

Remote profiles, runtime controls/logs, agents/providers, update screen.

### Недоработки

- один длинный stack карточек без search и adaptive list-detail;
- шесть отдельных Flow subscriptions вызывают широкие recompositions;
- Runtime Center написан, но route отсутствует и экран недостижим;
- onboarding state игнорируется;
- нет appearance/chat/security/accessibility/Git sections из дизайн-плана;
- remote test не использует Basic Auth credentials;
- save разрешён без успешной проверки и строгой URL validation;
- удаление remote profile без confirmation каскадно удаляет workspace/session/message;
- provider key сохраняется без проверки;
- версии runtime захардкожены;
- cards Claude/Codex/Antigravity дают ложную картину готовности.

### Редизайн

Phone: categories screen → detail. Tablet: list-detail. Вверху search. Runtime status — одна hero row; отдельный Runtime Center должен быть доступен по ней. Разделы: Connections, Agents & Models, Chat, Appearance, Notifications, Security & Data, Git, Accessibility, Diagnostics, Updates.

## 4.10 Runtime Center и logs

Экран существует, но недоступен. Значения `1.2.27 (musl-arm64)`, Alpine/PRoot захардкожены и неверны для x86_64/обновлённого runtime. Uptime не обновляется по ticker. Logs содержат критические secrets.

После исправления routing экран должен читать версии из manifest/installed marker, architecture из device/runtime, health и uptime из Supervisor state. Copy/export logs — только после redaction.

## 4.11 OTA Update

### Что исправлено

Последний release реально содержит APK, `SHA256SUMS` и `update.json`; manifest валиден. Поиск релиза через Releases API + Atom fallback лучше старого прямого 404.

### Что остаётся

- Wi‑Fi-only меняет UI, но не ограничивает загрузку;
- нет periodic WorkManager, TTL/ETag, уведомлений и skip version;
- APK verification умеет больше, чем реально вызывает ViewModel: возрастающий versionCode не проверяется;
- package допускается по `startsWith("com.pocketcli")`, а нужен exact match;
- отсутствие package/cert данных местами трактуется как pass;
- нет максимального размера APK;
- fallback `Uri.fromFile` небезопасен;
- канал UI захардкожен как Beta;
- перед установкой не останавливается runtime и не создаётся update marker;
- после публикации workflow не скачивает assets обратно и не валидирует manifest/hash/signature end-to-end;
- release notes шаблонные, а не соответствуют изменениям;
- из-за утёкшего signing key вся OTA-цепочка сейчас недоверенная.

---

## 5. Реальная готовность функций

| Функция | Реальный статус | Комментарий |
|---|---|---|
| Remote OpenCode sessions/chat | Частично работает | основной настоящий backend |
| Local OpenCode runtime | Частично работает | зависимости исправлены, lifecycle/secret logging остаются |
| Claude Code ACP | Не реализовано | forwarding в OpenCode, не ACP/JSON-RPC/stdio |
| Codex ACP | Не реализовано | тот же forwarding, нет настоящего adapter |
| Antigravity chat | Частично работает | direct Gemini text API, не coding agent |
| Antigravity tools/diff/permissions | Не реализовано | capabilities заявлены неверно |
| Clone Git repository | Не реализовано | создаётся локальная папка |
| Import folder | Не реализовано | SAF отсутствует |
| Git status/diff | Демонстрационная заглушка | часть статуса эвристическая, diff фальшивый |
| File browser | Минимальный прототип | только root listing |
| Terminal | Не реализовано | статический UI |
| Attachments | Не реализовано end-to-end | UI labels есть, payload не отправляется |
| Model picker | Работает частично | вероятны jank и collision IDs |
| Permissions | Частично | UI/state есть, causal flow неполный |
| OTA discovery/download | Частично работает | manifest есть, security verification неполная |
| Background update checks | Не реализовано | WorkManager отсутствует |
| Runtime Center | Недостижим | route не подключён |
| Onboarding | Недостижим | state есть, UI не показывается |
| Adaptive tablet UI | Минимально | rail вместо bar, list-detail нет |

### Главный продуктовый вывод

Сейчас UI обещает больше, чем backend умеет. Перед полировкой нужно либо реализовать функции, либо честно скрыть/отключить их. Хороший интерфейс не должен симулировать Git, Terminal или ACP.

---

## 6. Агентные backend’ы

## 6.1 OpenCode

Это единственный полноценно связанный агентный backend. Есть sessions, messages/events, local/remote use. Его следует объявить единственным поддерживаемым backend для следующей beta и довести до стабильности.

## 6.2 Claude Code и Codex

[`AcpAdapter.kt`](https://github.com/SH20FK/PocketCLI/blob/f5541d7894639b1215ce13fb3ff702d85c7021ac/app/src/main/java/com/pocketcli/agent/acp/AcpAdapter.kt) не реализует ACP: нет запуска process, stdio transport, JSON-RPC, initialize/capabilities, cancel, permission roundtrip. Он проксирует вызовы в OpenCode и меняет label/model. При отсутствии delegate некоторые операции даже могут «успешно» завершиться локально без агента.

**Решение:** скрыть Claude/Codex из production UI до настоящего adapter. Fallback между агентами запрещён: выбранный backend либо работает, либо возвращает ясное состояние Unavailable.

## 6.3 Antigravity

Это direct Gemini-like text streaming через Cloud Code endpoints/public fallback. Он не умеет локальные файлы, shell, diff и approval. Тем не менее UI объявляет соответствующие capabilities. Есть дублирование текущего prompt, слабая OAuth-схема, API key в query и логирование частей чувствительного ответа.

**Решение:** переименовать как экспериментальный text provider, capabilities оставить только `TextStreaming` до реальной tool integration. Исправить auth и prompt assembly.

## 6.4 Mixed-agent sessions

Sync зависит от текущего adapter, поэтому локальные сессии разных backend не имеют единого надёжного reconciliation. Нужен repository per profile+agent, а adapter identity должна храниться и проверяться в каждом запросе.

---

## 7. Local runtime

### Положительные изменения

Runtime manifest теперь содержит pinned `libgcc`, `libstdc++` и CA bundle для aarch64/x86_64; installer делает preflight `opencode --version` в очищенном guest env. Это адресует ошибку `Error relocating ... symbol not found`.

### Оставшиеся риски

- health timeout 15 секунд мал для слабых устройств после холодного старта;
- `/etc/resolv.conf` принудительно получает 8.8.8.8/1.1.1.1, что ломает VPN/private DNS/captive/enterprise networks;
- ForegroundService `START_STICKY` после process kill возвращает notification, но не обязательно поднимает сервер;
- idle timer не получает activity heartbeat из чата, поэтому runtime может остановиться во время активной сессии;
- keep-alive/wakelock actions существуют, но вызывающий flow не найден;
- notification text повреждён (`Оста��овить`);
- используются системные иконки вместо brand icon;
- secrets логируются в command line.

### Требуемая state machine

`NotInstalled → Installing(stage/progress) → Preflight → Stopped → Starting → Healthy → IdleCountdown → Stopping → Failed(recoverable/security)`.

Каждый transition должен иметь timestamp, reason и idempotent command. Chat activity обновляет lease, а не вручную дёргает бессвязные actions.

---

## 8. Design system и motion

### Что уже появилось

- spacing/shape tokens;
- `PocketTheme` и motion scheme;
- reusable cards/buttons/pills;
- несколько `AnimatedContent`, `AnimatedVisibility`, infinite transitions;
- компоненты для loading, model picker, composer.

### Почему визуал всё ещё «нейрослоп»

1. **Нет одного layout grammar.** Каждый экран самостоятельно решает отступы, cards, metadata и top bar.
2. **Компоненты конкурируют за акцент.** Model chip, avatar, status, pills и buttons имеют сходный вес.
3. **Много имитации M3 Expressive, мало настоящей иерархии.** Большая округлость сама по себе не создаёт дизайн.
4. **Motion несистемный.** `PocketMotion` существует, но экраны продолжают использовать случайные tween; shared transitions и `animateContentSize` отсутствуют.
5. **Нет screen-level previews и visual regression.** 11 preview в основном на компоненты не позволяют удерживать продукт целиком.
6. **Hardcoded copy.** Найдено около 274 строк интерфейса в Kotlin; RU и EN смешаны.

### Motion audit

- `collectAsStateWithLifecycle`: 0;
- `SharedTransition`: 0;
- `animateContentSize`: 0;
- `AnimatedContent`: 4;
- `AnimatedVisibility`: 9;
- infinite transitions: 4;
- `PocketAnimatedIcon` — crossfade stock icons, не path morph;
- `PocketLottie` не использует Lottie runtime и не является Lottie integration;
- Reduce Motion / animator scale handling отсутствует.

### Целевая motion-система

- 90–120 ms: press/state feedback;
- 160–220 ms: icons/chips;
- 260–340 ms: sheet/content swap;
- 400–520 ms: shared bounds;
- ambient motion только один экземпляр на экране и только при реальной activity;
- `Send → Stop`, `Sync → Check`, `Expand → Collapse` — настоящие path/AVD morph;
- list→detail — shared bounds на tablet/phone с fade-through fallback;
- reduce motion: morph/shared bounds заменяются crossfade, infinite loops выключаются.

---

## 9. Accessibility и читаемость

- встречаются click targets 36dp и 44dp вместо минимум 48dp;
- metadata часто `labelSmall` около 11sp;
- найдено много `contentDescription = null`; часть декоративна, но системного аудита нет;
- нет string resources, plural rules и нормальной локализации;
- нет проверки font scale 200%; chips/dialogs вероятно клипуются;
- TalkBack order для message/tool/permission timeline не определён;
- streaming может озвучиваться слишком часто или непредсказуемо;
- tablet UI — только rail, без настоящего list-detail.

**Definition of Done:** TalkBack сценарий, 200% font, landscape, minimum width, light/dark/dynamic, RU/EN, touch targets ≥48dp и screenshot test каждого состояния.

---

## 10. Производительность

### Главные риски

- full-list rebuild на каждом streaming delta;
- full message list из Room без pagination;
- repeated workspace filesystem scan;
- несколько root-level Flow collectors в Settings;
- model picker на сотнях элементов без benchmark;
- Markdown на растущем streaming тексте;
- infinite animations без единого lifecycle/offscreen control.

### Что измерять

1. time-to-first-frame и startup после process death;
2. открытие чата с 10/100/1000 сообщениями;
3. 5-минутный streaming response;
4. model picker 50/300/1000 моделей;
5. composer expand + IME;
6. project list с 50 workspace по 10 тыс. файлов;
7. runtime install/start на слабом ARM устройстве.

Нужны Macrobenchmark, Baseline Profile и JankStats/FrameMetrics. Пока доказательств плавности нет.

---

## 11. Тесты, сборка и CI/CD

### Состояние

- около 20 JVM test-файлов;
- нет `androidTest`, Compose UI tests, screenshot tests, macrobenchmark;
- нет тестов главных рисков ChatViewModel/AgentSessionRepository reconciliation;
- «Git clone» тесты фактически подтверждают создание папки;
- ACP тесты подтверждают forwarding, а не протокол;
- CI не запускает lint;
- `gradlew` — 34-байтный proxy на установленный `gradle`, настоящего wrapper jar нет;
- release build без minification;
- `contents: write` выдан шире, чем нужен release job;
- быстрый поток beta releases не сопровождается end-to-end QA.

### Release workflow

Workflow действительно создаёт GitHub Release и assets — это лучше простого Actions Artifact. Но необходимо:

- отдельный PR CI без secrets и write permissions;
- отдельный protected release job;
- настоящий Gradle Wrapper с checksum validation;
- `lint`, unit, UI smoke, release assemble, `apksigner verify`;
- после upload скачать assets обратно, проверить SHA, JSON schema, package, version, certificate;
- idempotent behavior при повторном запуске;
- release notes из changelog/commits, не шаблон.

---

## 12. План исправлений

## P0 — 1–3 дня: остановить опасное и ложное

- ротировать signing key и определить migration strategy;
- удалить secrets из logs/commands;
- скрыть Claude/Codex и неподдерживаемые Antigravity capabilities;
- убрать fake Git diff/terminal/clone/import;
- исправить message IDs, duplicate prompt и empty bubble;
- updater: exact package, certificate mandatory, greater versionCode mandatory;
- отключить destructive Room fallback.

## P1 — 1 неделя: сделать один честный vertical slice

Один поддерживаемый flow:

`Onboarding → Local или Remote OpenCode → Workspace → Session → Send → Streaming → Tool → Permission → Result → Resume after process death`.

В него входят:

- новый chat timeline model;
- lifecycle-aware state collection;
- stable composer и model picker;
- Runtime Center route;
- реальный first-run onboarding;
- error/reconnect/offline states;
- UI tests и screenshot matrix.

## P2 — 1–2 недели: проекты и обновления

- настоящий Git clone с cancel/retry;
- SAF import;
- read-only file browser + viewer;
- workspace-aware create session;
- real Git status, затем real diff;
- WorkManager OTA checks, Wi‑Fi constraints, ETag, notifications;
- pre-install checkpoint/runtime stop/post-update health.

## P3 — после стабильного ядра

- настоящий ACP adapter для одного агента;
- terminal/PTY;
- advanced Git;
- tablet list-detail и shared transitions;
- Lottie/AGSL brand motion после performance budget;
- attachments end-to-end.

---

## 13. Acceptance criteria следующей beta

Релиз нельзя считать готовым, пока одновременно не выполнено:

- в Git и APK нет signing secrets;
- release certificate новый и документирован;
- OpenCode local и remote проходят 30-минутный soak test;
- одинаковые user messages не теряются и не дублируются;
- Antigravity не получает prompt дважды;
- process recreation восстанавливает streaming/pending permission без пустых bubbles;
- model picker держит целевой frame time на 300 моделях;
- все доступные buttons что-то делают; заглушки hidden/disabled;
- clone/import/Git/terminal не симулируют результат;
- OTA отвергает wrong package/version/cert/hash;
- Room migration сохраняет историю;
- onboarding и Runtime Center достижимы;
- light/dark/dynamic + RU/EN + 200% font screenshot tests зелёные;
- CI запускает lint, unit, UI smoke и post-release asset verification;
- release APK устанавливается поверх предыдущей **новой доверенной** beta без потери данных.

> **Рекомендуемая стратегия:** не пытаться одновременно полировать все экраны. На ближайший релиз оставить один честный backend — OpenCode — и довести одну вертикаль до ощущения качественного Android-приложения. Всё остальное либо скрыть, либо явно назвать экспериментальным. Это быстрее приведёт PocketCLI к цельному продукту, чем добавление новых карточек и анимаций поверх заглушек.

---

## 14. Визуальный аудит по фактическим скриншотам

> **Вывод:** экраны стали аккуратнее ранней версии, но сейчас это всё ещё набор отдельных M3-компонентов, а не цельная система. Основные проблемы — чрезмерные пустоты, слишком крупные контейнеры, слабая контрастность, дублирование действий, перенос коротких статусов, смешение цветовых ролей и отсутствие единого вертикального ритма. Исправлять нужно не «добавлением ещё анимаций», а повторной компоновкой экранов.

### 14.1 Общие правки для всего приложения

#### Сетка и размеры

- горизонтальный inset телефона: `20 dp`; внутри cards: `16 dp`;
- расстояние title → supporting text: `6–8 dp`, supporting text → первый контент: `20–24 dp`;
- между обычными rows: `8 dp`; между смысловыми секциями: `24–32 dp`;
- не оставлять декоративные пустоты по 80–140 dp, если они не отделяют hero-блок;
- обычная list row: `64–72 dp`; card с двумя строками: `80–88 dp`; текущие сессионные карточки около 125 dp слишком велики;
- interactive targets минимум `48 × 48 dp`, но видимая иконка может быть 24 dp;
- FAB должен иметь один ясный смысл на экране и не перекрывать контент.

#### Типографика

- Large screen title: `32sp`, weight 500–600; не использовать жирность одновременно для всех заголовков;
- section title: `20sp`, weight 600;
- card title: `17–18sp`, weight 600;
- body: `14–16sp`; metadata: минимум `12sp`, предпочтительно `13–14sp`;
- обычный UI не должен выглядеть моноширинным или иметь увеличенный letter spacing;
- поддерживающий текст ограничивать 2–3 строками; протоколы, версии и технические IDs переносить в detail screen;
- унифицировать `OpenCode`, `Локальный`, `Git`, русский интерфейс. Убрать `Search…`, `Shallow clone`, `Personal Access Token`, `Google OAuth` из русской локали либо дать корректный перевод.

#### Цвет

- сейчас одновременно конкурируют тёмно-синий FAB, светло-голубые CTA, фиолетовый selected navigation, синие outlines и отдельный зелёный status;
- выбрать одну dynamic Material color scheme и применять роли: `primary` — главное действие, `secondaryContainer` — selected navigation/filter, `surfaceContainer` — cards, `surfaceContainerHigh` — sheets;
- убрать постоянные голубые обводки вокруг обычных cards: outline нужен для focus/error/selection, а не для каждой поверхности;
- supporting text сейчас местами выглядит disabled. Поднять контраст до `onSurfaceVariant`, disabled использовать только для реально недоступных действий;
- зелёный status должен сопровождаться текстом/иконкой и использоваться точечно, не как плохо читаемая строка на тёмной карте.

#### Формы и иконки

- не делать каждый элемент pill. Cards — radius `20–24 dp`, поля — `12–16 dp`, small chips — `8–12 dp`, FAB — системная форма;
- привести иконки к одному набору Material Symbols и одной оптической толщине;
- archive/download, folder-star и robot icons сейчас считываются неоднозначно — добавить tooltip/content description и использовать ожидаемые символы;
- status chip никогда не должен переносить одно слово на две-три строки. Минимальная ширина по содержимому и `maxLines = 1`.

### 14.2 Экран «Чаты»

#### Что выглядит плохо

- карточки слишком высокие для title + agent + date;
- каждая карточка имеет постоянно видимую корзину, поэтому destructive action визуально равен открытию чата;
- большие закругления и одинаковая заливка превращают список в три тяжёлых блока;
- `Профиль: Локальный агент` и отдельный chip `Локально` дублируют информацию;
- status chip выглядит прижатым к тексту профиля;
- refresh и search имеют высокий визуальный вес, хотя refresh — редкое действие;
- FAB и карточки используют разные оттенки primary/selected navigation.

#### Новая композиция

Top app bar:

- первая строка: `Чаты`, справа Search и overflow;
- refresh убрать в pull-to-refresh/overflow;
- вторая строка: маленький status dot + `Локальный агент · Готов`, вся строка открывает Runtime Center;
- отдельный `Локально` chip удалить.

Session row:

```txt
[agent icon]  Название сессии                       [time]
              Проект · OpenCode · Готов             [chevron]
```

- высота `76–84 dp`;
- корзину убрать: delete/archive через swipe, long press или overflow;
- agent chip заменить простой metadata-строкой;
- карточка либо tonal с radius 20 dp, либо list row с divider — не одновременно огромная card и chips;
- FAB оставить только на экране списка, `Новый чат`; при прокрутке он может схлопываться до круглого.

### 14.3 Поиск чатов

- placeholder должен быть `Поиск чатов`, не `Search…`;
- back и search field должны находиться в одной 64dp app bar, а не образовывать отдельную огромную верхнюю область;
- при фокусе скрывать FAB: создание новой сессии не относится к поисковому состоянию;
- показать clear action, число результатов и empty state по запросу;
- bottom navigation можно оставить, но поле поиска и back должны использовать стандартный `SearchBar`/`AppBarWithSearch` transition;
- переход Search icon → search field: shared bounds 260–340 мс, клавиатура появляется после завершения первого layout, без прыжка списка.

### 14.4 Чат

#### Проблемы на скриншоте

- показаны только user bubbles; без agent turns экран выглядит как отправка сообщений в пустоту;
- расстояния между сообщениями хаотичны: примерно 90 dp между первыми bubbles и ещё больше перед последним;
- большие сообщения имеют чрезмерно широкую колонку текста и слишком высокий line spacing;
- timestamp плавает внутри bubble и конкурирует с последней строкой;
- header хороший по направлению, но File action и overflow визуально крупнее статуса;
- composer занимает почти 170 dp даже для пустого сообщения;
- model selector спрятан внизу отдельной тёмной pill, а Send выглядит permanently disabled;
- `+`, model chip и send не выровнены по одной baseline/центру.

#### Целевая компоновка

Header:

```txt
[Back]  Название
        ● Готов · Локально · OpenCode         [Files] [More]
```

- высота expanded header около `72 dp`, collapsed `56 dp`;
- зелёная точка 8 dp, supporting line 13–14sp;
- Files показывать только если действие реально работает.

Timeline:

- user bubble: max width `82%`, padding `14×10 dp`, radius `20/20/6/20`;
- соседние сообщения одного автора: gap `4–6 dp`; новый turn: `18–24 dp`;
- assistant response — свободный текст без огромной bubble, слева по общей сетке;
- timestamp 11–12sp под bubble либо в actions row, а не в зоне текста;
- пустые и whitespace-only messages не рендерить;
- при running показывать одну компактную activity row: animated mark + `OpenCode анализирует проект`;
- tool/permission/diff размещать непосредственно после фрагмента ответа, который их вызвал.

Composer:

- collapsed height `64–72 dp`, expanded — по тексту максимум до 5 строк;
- первая зона — текст; нижний action row появляется только при attachments/multiline либо остаётся компактным 48 dp;
- model chip показывает реальное имя выбранной модели, не слово `Модель`;
- Send активен только при непустом тексте, но disabled contrast должен оставаться различимым;
- Send → Stop без изменения размеров контейнера;
- `animateContentSize` с spring, IME inset и attachment row не должны двигать message list рывком.

### 14.5 Выбор модели

#### Проблемы

- recommended chips обрезаются (`Ling 3.0…`) и выглядят как горизонтальный случайный набор;
- provider title `Opencode` написан неверно;
- строки моделей не имеют явной selected state, capability/контекста и trailing check;
- bottom sheet показывает много пустого пространства и не объясняет, какую модель пользователь выбирает;
- тяжёлый search field + chips + grouped list дают плохую first-frame стоимость.

#### Исправление

- sheet должен открываться максимум на 85–90% высоты и сразу показывать drag handle, title и текущую модель;
- favorites/recent — горизонтальные compact chips только если помещаются; иначе 2–3 строки списка;
- model row:

```txt
[provider] Big Pickle                         [✓]
           OpenCode · tools · context 128k
```

- minimum row `64 dp`, stable key `providerId:modelId`;
- один selected check, long press для default не нужен — сделать trailing star action;
- поиск debounce 150–200 мс;
- после выбора sheet закрывается за 220–280 мс, chip в composer обновляется после commit;
- добавить skeleton только если первая загрузка реально дольше 200 мс.

### 14.6 Экран «Проекты»

#### Проблемы

- пустое состояние содержит две одинаковые команды `Добавить проект`: центральную CTA и FAB;
- profile text и chip снова дублируются, а `Локально` переносится на три строки;
- в top bar три равнозначные иконки без ясной иерархии;
- filter `Git` при нуле проектов не даёт ценности;
- empty illustration слишком тяжёлая и расположена низко;
- FAB menu реализован как маленький popup поверх empty-state текста: строки обрезаны, touch targets неочевидны.

#### Исправление

- в empty state оставить центральную кнопку; FAB показывать только когда уже существует хотя бы один проект;
- добавление на телефоне открывает modal bottom sheet из трёх полноширинных rows, а не dropdown menu;
- top bar: Search + overflow; Sort/filter объединить в одну filter/sort sheet;
- profile state перенести в одну supporting line;
- segmented filters показывать после появления проектов; при пустом списке они не нужны;
- empty-state блок центрировать в доступной области между app bar и navigation, но на 24–32 dp выше геометрического центра;
- текст сократить: `Клонируйте репозиторий, импортируйте папку или создайте пустой проект.`

### 14.7 Sort sheet

- не использовать чёрный прямоугольник внутри sheet как отдельную поверхность;
- варианты сортировки — radio rows высотой 56 dp с check/radio справа;
- divider перед фильтром внимания;
- `Требуют внимания` — Switch или checkbox с полным 48dp target;
- sheet должен завершаться ниже последней строки с navigation/gesture inset;
- выбранный вариант подсвечивать лёгким tonal background, а не только галочкой.

### 14.8 Clone repository sheet

#### Проблемы

- все text fields примерно 88 dp и напоминают многострочные формы;
- русский и английский текст смешаны;
- supporting text shallow clone ломает ритм и сталкивается со switch;
- disabled CTA слишком тусклая и визуально похожа на поломанную;
- иконка в title выглядит оторванной;
- нет видимого close/back и неочевидно, как sheet поведёт себя с клавиатурой.

#### Новая форма

- title `Клонировать репозиторий`, subtitle `GitHub, GitLab или другой HTTPS-сервер`;
- URL — 56dp single-line field; после валидного URL имя проекта заполняется автоматически;
- `Ветка` и `Глубина` спрятать под `Дополнительно`;
- toggle назвать `Быстрое клонирование`, supporting: `Скачает только последнюю версию`; технический `--depth 1` показать в details;
- token field: `Токен доступа`, supporting `Нужен только для приватного репозитория`;
- CTA `Клонировать`, sticky над IME/navigation inset;
- во время операции form morph → progress surface с этапом, скоростью, cancel и logs details;
- не закрывать sheet swipe-жестом во время активной операции без confirmation.

### 14.9 Настройки

#### Главная проблема

На скриншотах между subtitle и первой карточкой остаётся огромная пустота около 100–150 dp. Ниже экран, наоборот, перегружен большими cards, техническими описаниями и FAB. Возникает ощущение сломанной LazyColumn, а не продуманной иерархии.

#### Что изменить

- после subtitle оставить максимум `24–32 dp`;
- убрать общий FAB `+`: на экране Settings его смысл неоднозначен и он перекрывает agent cards/remote section;
- `Добавить сервер` сделать button внутри секции Remote servers;
- runtime card разделить: компактная hero row + actions sheet. Не держать четыре outlined buttons в 2×2 grid;
- badge `Активен` сделать content-width, one-line; текущий перенос на три строки недопустим;
- зелёный status заменить на `● Работает · порт 41743`, повысить контраст;
- card border убрать, если runtime не selected/error;
- agent cards сократить до title, честного статуса и одной supporting line; protocol/version — в detail;
- неподдерживаемые Claude/Codex показывать `Не реализовано` и disabled action либо полностью скрыть;
- секции Settings сделать через category list. На корневом экране не должно быть всех подробностей одновременно;
- добавить bottom content padding не меньше высоты navigation + 24 dp: сейчас последний текст скрывается нижней панелью.

### 14.10 Диалог «Новый сервер»

- диалог почти во весь экран, но всё равно ведёт себя как modal dialog; на телефоне лучше full-height bottom sheet или отдельный route;
- form должен прокручиваться при IME и сохранять заголовок/CTA;
- `Проверить подключение` не должно конкурировать с `Сохранить` как второе primary action;
- правильный flow: fields → secondary `Проверить` → inline result → primary `Сохранить`;
- `Сохранить` disabled до валидного URL; успешный тест желателен, но не обязателен только при явном предупреждении;
- HTTP switch сопровождается warning container о передаче credentials без TLS;
- password field должен иметь видимость, password-manager semantics и не заполняться повторно после сохранения;
- background navigation под dialog сейчас остаётся визуально заметной; scrim должен сильнее отделять modal layer.

### 14.11 Bottom navigation

- текущая selected pill слишком широкая и фиолетовая, тогда как primary actions синие;
- использовать один `secondaryContainer/onSecondaryContainer` из темы;
- высота navigation должна учитывать gesture inset, но контент обязан получать соответствующий bottom padding;
- labels не должны обрезаться при 200% font; при необходимости adaptive navigation rail;
- смена вкладки: короткий icon/indicator motion 180–220 мс, без пересоздания состояния списков.

### 14.12 Обязательная визуальная приёмка

Перед следующим релизом агент должен приложить в `docs/UI_REVIEW_V2.md`:

1. before/after каждого экрана из этого раздела;
2. spacing overlay с 4dp grid;
3. light, dark и минимум две dynamic palettes;
4. RU и EN;
5. font scale 100%, 130% и 200%;
6. 360×800, 412×915 и tablet layout;
7. состояния empty/loading/offline/error/content;
8. video 60 fps: search transition, open model sheet, select model, send/stop, open add-project sheet;
9. Macrobenchmark/JankStats для model picker и streaming chat;
10. подтверждение, что ни один chip (`Локально`, `Активен`, `Готов`) не переносится.

### 14.13 Приоритет визуальных изменений

**Сначала:**

1. убрать гигантский пробел Settings;
2. исправить wrapping status chips;
3. убрать дублирующиеся CTA/FAB в Projects;
4. заменить project popup на bottom sheet;
5. уплотнить Session cards и спрятать delete;
6. перестроить Chat timeline и composer;
7. исправить model picker rows и search performance;
8. унифицировать color roles, язык и иконки.

**Только потом:** shared transitions, morphing icons, AI orb и декоративные Lottie. Сейчас дополнительная анимация лишь сделает структурные дефекты более заметными.
