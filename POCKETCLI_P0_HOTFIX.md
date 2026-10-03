<callout icon="🛑" color="red_bg">P0-блокер. До исправления этих трёх проблем нельзя продолжать визуальный rewrite и нельзя выпускать следующую OTA-сборку: локальный runtime не запускается, updater обращается к несуществующему каналу, а chat одновременно показывает дубли и пустое пользовательское сообщение.</callout>

## Проверенная версия

Разбор выполнен по commit `60cdff30c038672f9e22da32bc751b3504d05a95` и приложенному скриншоту.[^https://github.com/SH20FK/PocketCLI/commit/60cdff30c038672f9e22da32bc751b3504d05a95]

## 1. Локальный OpenCode падает с exit code 127

### Диагноз

Скачивается пакет `opencode-linux-arm64-musl`/`opencode-linux-x64-musl`, но он запускается внутри минимального Alpine rootfs, в который не добавлены runtime-библиотеки C++.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/app/src/main/assets/local-runtime-manifest.json]

Группы отсутствующих символов однозначно указывают на зависимости:

| Символы | Отсутствующая runtime-часть |
| --- | --- |
| `_ZSt*`, `__cxxabiv1*`, `std::cerr`, `__once_proxy` | `libstdc++.so.6` |
| `_Unwind_*`, `__modti3`, `__addtf3`, `__divtf3` | `libgcc_s.so.1` / GCC runtime |

Alpine `minirootfs` специально минимален. Название npm-пакета `musl` означает совместимость с musl libc, но не означает, что C++ runtime статически включён. Текущий installer копирует только бинарник OpenCode в `/usr/bin`; необходимые Alpine packages не устанавливаются.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/runtime/local/src/main/java/com/pocketcli/runtime/local/installer/RuntimeInstaller.kt]

Health-check здесь вторичен: процесс уже завершился с `127`, поэтому ожидание 15 секунд только скрывает исходную ошибку.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/runtime/local/src/main/java/com/pocketcli/runtime/local/supervisor/LocalRuntimeSupervisor.kt]

### Исправление installer

Расширить runtime manifest:

```kotlin
@Serializable
data class ArchArtifacts(
    val rootfs: ArtifactInfo,
    val opencode: ArtifactInfo,
    val packages: List<RuntimePackage> = emptyList(),
)

@Serializable
data class RuntimePackage(
    val name: String,
    val artifact: ArtifactInfo,
)
```

Для каждой архитектуры добавить pinned Alpine 3.21 packages:

- `libstdc++`;
- `libgcc`;
- `ca-certificates-bundle`, если HTTPS внутри OpenCode не проходит следующую smoke-проверку.

Требования:

1. Версии packages должны соответствовать ветке Alpine rootfs `v3.21`.
2. В manifest должны быть точные URL, размер и SHA-256 для обеих архитектур.
3. Каждый `.apk` скачать тем же verified downloader.
4. После rootfs извлечь package payload в staging rootfs.
5. Не использовать `apk add latest` без pinning: сборка должна воспроизводиться.
6. Marker `.pocketcli_ready` создавать только после runtime smoke test.

### Обязательный preflight до запуска сервера

Добавить в `RuntimeInstaller`/`LocalRuntimeSupervisor`:

```text
/usr/bin/opencode --version
```

Условия успеха:

- exit code `0`;
- stdout содержит непустую версию;
- stderr не содержит `Error relocating`, `symbol not found`, `not found`;
- выполнение завершается не дольше 10 секунд.

Если preflight падает:

- не запускать health-check;
- удалить ready marker;
- перевести installer в `Failed(canRetry = true)`;
- показать пользователю: `Локальный runtime установлен неполностью: отсутствуют системные библиотеки`;
- в Details вывести исходный stderr и архитектуру.

### Environment cleanup

Сейчас `LD_LIBRARY_PATH` устанавливается в Android native library directory и наследуется guest-процессом.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/runtime/local/src/main/java/com/pocketcli/runtime/local/proot/ProotEnvironment.kt]

Нужно разделить:

- host environment для запуска PRoot;
- guest environment для `/usr/bin/opencode`.

Guest не должен получать host `LD_LIBRARY_PATH`. Запускать внутри PRoot через `/usr/bin/env -i` с явными `HOME`, `PATH`, `TERM`, `LANG`, provider keys и OpenCode password. Иначе host paths могут влиять на поиск guest libraries.

### Не считать исправлением

- увеличение health timeout;
- бесконечный restart;
- игнорирование exit code 127;
- скачивание glibc-варианта бинарника в Alpine musl;
- копирование случайной `libstdc++.so` из Android;
- создание marker до smoke test.

### Runtime acceptance

- [ ] arm64 physical device: `opencode --version` проходит.
- [ ] x86_64 emulator: `opencode --version` проходит.
- [ ] `opencode serve` отвечает health endpoint.
- [ ] uninstall/reinstall runtime повторяет результат.
- [ ] повреждение `libstdc++` ловится preflight, а не 15-секундным timeout.
- [ ] SHA mismatch package останавливает установку.
- [ ] `readelf -d /usr/bin/opencode` и наличие всех `NEEDED` dependencies сохраняются в CI artifact.

## 2. Почему OTA возвращает 404

### Фактическое состояние GitHub

В репозитории есть только prerelease-релизы `v1.0.0-beta.1` и `v1.0.1-beta.1`; stable release отсутствует.[^https://github.com/SH20FK/PocketCLI/releases]

Последний beta release действительно содержит `update.json`, APK и `SHA256SUMS`.[^https://github.com/SH20FK/PocketCLI/releases/tag/v1.0.1-beta.1]

Но клиент использует:

- Stable: `/releases/latest/download/update.json`;
- Beta: `raw.githubusercontent.com/.../main/update.json`.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/core/update/src/main/java/com/pocketcli/core/update/GitHubReleaseApi.kt]

Оба пути сейчас неверны:

1. GitHub `releases/latest` игнорирует prerelease, а stable release нет — получается 404.
2. Workflow прикрепляет `update.json` к Release, но не коммитит его в `main` — beta raw URL тоже 404.

### Правильная схема клиента

Не использовать redirect-магические URL как источник выбора версии. Использовать GitHub Releases API:

```text
GET https://api.github.com/repos/SH20FK/PocketCLI/releases?per_page=20
```

Алгоритм:

```text
releases
→ удалить draft
→ stable: удалить prerelease
→ beta: оставить stable + prerelease
→ отсортировать по published_at
→ взять первый подходящий
→ найти asset с name == update.json
→ скачать его browser_download_url
→ проверить, что manifest.tag == release.tag_name
→ найти APK asset с именем из manifest
```

Новые результаты API:

```kotlin
sealed interface ManifestFetchResult {
    data class Found(val manifest: UpdateManifest) : ManifestFetchResult
    data class NoRelease(val channel: UpdateChannel) : ManifestFetchResult
    data class MissingAsset(val tag: String, val asset: String) : ManifestFetchResult
    data class InvalidManifest(val tag: String, val reason: String) : ManifestFetchResult
    data class NetworkError(val reason: String) : ManifestFetchResult
}
```

UI не должен показывать сырой `HTTP 404`. Для `NoRelease(STABLE)`:

> Стабильных выпусков пока нет. Можно переключиться на Beta или открыть страницу релизов.

### Канал по умолчанию

- если установлен `versionName` с `-beta`, `-alpha` или `-rc`, default channel = Beta;
- stable build default channel = Stable;
- пользовательский выбор сохраняется;
- beta build не должен молча проверять несуществующий stable endpoint.

### Workflow

Текущий workflow создаёт manifest правильно, но маскирует ошибку публикации через `|| echo "Release already exists or skipped"`.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/.github/workflows/android.yml]

Исправить:

- убрать `|| echo`;
- если tag существует — явно обновить assets через `gh release upload --clobber` либо завершить job с ошибкой;
- после публикации сделать smoke step:
  - запросить release по tag;
  - скачать `update.json`;
  - проверить JSON schema;
  - проверить URL APK;
  - скачать первые bytes/HEAD APK;
  - сравнить size и SHA metadata;
- beta.1/beta.2 одной базовой версии должны иметь разные возрастающие `versionCode`;
- release job считается успешным только после успешного updater smoke test.

### OTA acceptance

- [ ] beta build находит `v1.0.1-beta.1` без raw-main файла.
- [ ] stable channel при отсутствии stable не показывает 404.
- [ ] missing `update.json` превращается в `MissingAsset`, а не generic network error.
- [ ] manifest tag совпадает с выбранным release.
- [ ] prerelease никогда не приходит в stable channel.
- [ ] CI реально скачивает опубликованный manifest после release.

## 3. Что сломано на экране чата

### 3.1 Верхняя панель физически не помещается

Сейчас в `TopAppBar` одновременно размещены:

- back;
- title `133`;
- workspace `Локально`;
- полноценный status pill;
- огромный model `FilterChip`.

На ширине телефона actions забирают большую часть app bar. Status pill сжимается почти до круга, текст клипается и превращается в обрывки рядом с terminal icon. Это видно на скриншоте. Причина содержится непосредственно в текущей компоновке.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/feature/chat/src/main/java/com/pocketcli/feature/chat/ChatScreen.kt#L55-L125]

Исправление:

- model chip полностью убрать из top bar;
- top bar: Back | title + `project · status` | overflow;
- выбор модели перенести в нижнюю action row composer;
- status в header — icon 8 dp + короткий текст, не отдельная pill;
- title max 1 line, supporting line max 1 line;
- проверить ширины 320/360/412 dp и font scale 200%.

### 3.2 Дубли пользовательских сообщений

`sendPrompt()` сначала сохраняет локальное сообщение с ID вида `msg_<timestamp>`, затем server reconcile сохраняет тот же пользовательский текст с серверным message ID. Room считает их разными сущностями, поэтому появляются дубли.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/feature/chat/src/main/java/com/pocketcli/feature/chat/ChatViewModel.kt#L130-L185][^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/data/opencode/src/main/java/com/pocketcli/data/opencode/repository/AgentSessionRepository.kt#L198-L210]

Исправление — один из двух допустимых вариантов:

<b>Предпочтительный:</b>

1. Отправить prompt.
2. Получить/извлечь server message ID из ответа API.
3. Сохранить optimistic row с этим ID либо заменить local client ID на server ID транзакцией.
4. Reconcile делает upsert того же ключа.

<b>Fallback, если API не возвращает ID:</b>

- хранить `clientRequestId` и `deliveryState` отдельно;
- при reconcile матчить только pending local user message той же сессии по `clientRequestId`; временной/text heuristic допускается лишь как fallback с коротким окном;
- после match заменять ID в транзакции вместе с tool relations.

Нельзя просто дедуплицировать одинаковый текст: пользователь имеет право отправить `привет` несколько раз.

### 3.3 Пустой голубой bubble

`message.updated` превращается в `MessageStarted` даже для USER и создаёт пустой `StreamingMessageState`. UI безусловно рисует user bubble, даже если `message.text` пуст. Отсюда пустой контейнер в правой части скриншота.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/data/opencode/src/main/java/com/pocketcli/data/opencode/adapter/OpenCodeAdapter.kt#L59-L73][^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/data/opencode/src/main/java/com/pocketcli/data/opencode/repository/AgentSessionRepository.kt#L211-L224]

Исправление:

- не создавать visible user in-flight row из одного `message.updated`;
- streaming state держать по `messageId`, а не один объект на `sessionId`;
- показывать message node, только если есть text, reasoning, tool call или explicit placeholder для ASSISTANT;
- USER без content никогда не попадает в timeline;
- повторный `message.updated` не должен обнулять уже собранный текст.

### 3.4 Composer

Проблемы скриншота:

- высота около 138 dp при пустом поле;
- placeholder переносится на две строки;
- focused caret торчит перед placeholder;
- Stop — чёрный квадрат внутри розового круга и визуально не связан с остальным UI;
- `+`, input и Stop имеют разные визуальные веса;
- model selector отсутствует в правильном месте.

Текущий composer использует вложенный `TextField` с собственными paddings внутри ещё одного Surface; это раздувает высоту. Stop slot всего 40 dp, меньше целевого touch target.[^https://github.com/SH20FK/PocketCLI/blob/60cdff30c038672f9e22da32bc751b3504d05a95/feature/chat/src/main/java/com/pocketcli/feature/chat/ChatScreen.kt#L260-L390]

Replacement:

```text
Collapsed composer: 64 dp вместе с внешними полями
Surface margin: 8 dp
Inner horizontal: 8 dp
Add slot: 48 dp
Text field: min 48 dp, max 120 dp
Send/Stop slot: 48 dp
Attachment strip: отдельные 48 dp сверху
Model chip: нижняя строка только в expanded/typing state либо компактно перед Send
```

Использовать `BasicTextField` + decoration box или корректно обнулить все content paddings Material TextField. Placeholder в одну строку: `Сообщение агенту…`. При пустом unfocused поле caret отсутствует. Send → Stop занимает одну и ту же 48 dp позицию; stop glyph 18–20 dp, container error только во время реального running.

### 3.5 Timeline

- горизонтальные поля 16 dp;
- user bubble максимум 88% viewport, не фиксированные 320 dp;
- assistant answer остаётся document-style, но получает единый turn spacing;
- расстояние между user и assistant turn 20–24 dp;
- последовательные user messages группируются с интервалом 6 dp, но не сливаются;
- пустые сообщения фильтруются до LazyColumn;
- `key` и `contentType` обязательны;
- автоскролл не запускает `animateScrollToItem` на каждом token.

### 3.6 Немедленный визуальный результат

После hotfix на том же наборе данных должно быть:

```text
←  133                              ⋮
   Локально · Выполняет

                         [ привет ]

Привет! Чем помочь?

                         [ привет ]

                         [ привет ]

┌──────────────────────────────────┐
│ +  Сообщение агенту…   Model  ■ │
└──────────────────────────────────┘
```

Это только структура, не ASCII-макет для буквального копирования.

## 4. Порядок исправления

1. Runtime dependencies + preflight.
2. OTA release selection через API.
3. Message identity и пустые in-flight rows.
4. Top bar и composer replacement.
5. Buffered streaming/autoscroll.
6. Только после этого — motion и остальные экраны.

## 5. Обязательные regression tests

### Runtime

- missing `libstdc++`;
- missing `libgcc`;
- wrong ABI;
- corrupted package;
- successful `--version` and health.

### OTA

- only prereleases exist;
- no releases exist;
- stable + prerelease coexist;
- release without manifest;
- manifest/APK mismatch.

### Chat

- optimistic user + server echo = одна строка;
- два одинаковых пользовательских текста подряд = две строки;
- empty USER `message.updated` = ни одного bubble;
- assistant streaming = один tail;
- screen width 320 dp;
- font scale 200%;
- 300-model picker не влияет на timeline recomposition.

<callout icon="✅" color="green_bg">Готовность P0 подтверждается не словами «собирается»: нужен видеозапуск local runtime, успешная beta OTA-проверка и screenshot того же чата без дублей, пустого bubble, сломанного header и раздутого composer.</callout>
