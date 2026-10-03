<callout icon="🧩" color="purple_bg">Это не набор пожеланий. Это готовое техническое задание на визуальный rewrite поверх commit `735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1`: точные размеры, состояния, motion, API компонентов, порядок замены файлов и критерии приёмки. Если реализация расходится с этим документом, отклонение должно быть объяснено в PR.</callout>

## 0. Какой продукт строим

### Визуальная метафора

<b>«Спокойный живой документ, который умеет действовать».</b>

PocketCLI не должен выглядеть ни как Telegram с кодом, ни как Termux с карточками, ни как showcase Material-компонентов. В обычном состоянии экран почти неподвижен и очень читаем. Motion появляется только когда:

- пользователь совершил действие;
- агент сменил состояние;
- возник объект, требующий внимания;
- сохраняется пространственная связь между list и detail.

### Характер

- основной контент строгий и плоский;
- controls округлые и тактильные;
- технические поверхности имеют меньший radius и плотнее типографику;
- AI не обозначается роботами, мозгами, искрами и неоном;
- одна фирменная деталь — Pocket Prompt и мягкая живая форма состояния;
- одновременно на экране не больше одной ambient-анимации.

### Запреты

- не делать pill из каждой подписи;
- не использовать постоянное пульсирование online-dot;
- не анимировать каждый token;
- не показывать фальшивый progress;
- не размещать длинное имя модели в top app bar;
- не добавлять новый wrapper только ради названия компонента;
- не смешивать русский и английский интерфейс;
- не принимать экран без preview и screenshot state matrix.

## 1. Токены: вставить до работы над экранами

### 1.1 Сетка и отступы

```kotlin
object PocketSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

Правила:

- горизонтальный padding телефона: `16.dp`;
- узкий технический контент внутри карточки: `12.dp`;
- расстояние между смысловыми секциями: `24.dp`;
- расстояние между title и supporting text: `4.dp`;
- минимальная интерактивная область: `48.dp`;
- composer от краёв: `8.dp`, внутренний padding `12.dp`;
- bottom content padding = navigation inset + composer/active-bar height + `12.dp`.

### 1.2 Формы

```kotlin
object PocketShapes {
    val action = RoundedCornerShape(28.dp)
    val container = RoundedCornerShape(20.dp)
    val compact = RoundedCornerShape(14.dp)
    val technical = RoundedCornerShape(10.dp)
}
```

- `action`: composer, primary FAB, hero action;
- `container`: project/session cards, sheets;
- `compact`: user messages, tool summaries, settings groups;
- `technical`: code, diff, terminal, logs;
- status pill допускается только для реального статуса или выбора;
- вложенные контейнеры не должны иметь одинаковые цвет и elevation.

### 1.3 Типографика

| Роль | Стиль | Использование |
| --- | --- | --- |
| Screen title | `headlineMedium`, 28sp | верхний заголовок list screen |
| Detail title | `titleLarge`, 22sp | чат, проект, runtime |
| Section title | `titleMedium`, 16sp semibold | смысловая секция |
| Primary body | `bodyLarge`, 16sp/24sp | ответы агента, основной текст |
| Secondary | `bodyMedium`, 14sp/20sp | metadata и пояснения |
| Label | `labelLarge`, 14sp | кнопки, tabs |
| Compact label | `labelMedium`, 12sp | время, branch, duration |
| Code | 14sp/20sp monospace | код и terminal; регулируется 14–20sp |

Не использовать `labelSmall` для информации, которую пользователь обязан прочитать.

### 1.4 Цветовые роли

- app background: `surface`;
- list grouping: `surfaceContainerLow`;
- обычная card: `surfaceContainer`;
- modal/sheet: `surfaceContainerHigh`;
- user message: `secondaryContainer`;
- approval: `tertiaryContainer`;
- error: `errorContainer`;
- code: отдельный `PocketCodeScheme`, не случайные цвета Material theme;
- success: semantic token с contrast ≥ 4.5:1 для текста;
- dynamic color включён по умолчанию, но screenshot baseline делается и для fallback Indigo.

### 1.5 Elevation

- list card: 0 dp, разделение цветом;
- composer: tonal container + 1–3 dp только над scrolling content;
- bottom sheet: Material default;
- floating toolbar: 3 dp;
- не использовать тени внутри карточек.

## 2. Motion system: готовые значения

Material motion должен быть централизован; случайные `tween(300)` запрещены.[^https://m3.material.io/styles/motion]

```kotlin
@Immutable
data class PocketMotionScheme(
    val instant: FiniteAnimationSpec<Float> = tween(100),
    val quick: FiniteAnimationSpec<Float> = tween(180, easing = FastOutSlowInEasing),
    val standard: FiniteAnimationSpec<Float> = tween(280, easing = FastOutSlowInEasing),
    val emphasized: FiniteAnimationSpec<Float> = tween(420, easing = LinearOutSlowInEasing),
    val spatial: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 500f,
    ),
    val expressive: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 420f,
    ),
    val gesture: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = 800f,
    ),
)
```

Предоставлять через `CompositionLocal`, учитывать системный animator scale и настройку Reduce motion.

### 2.1 Таблица хореографии

| Событие | Полная motion | Reduce motion |
| --- | --- | --- |
| Sessions → Chat | shared bounds заголовка 420 мс + fade контента 180 мс | fade-through 180 мс |
| Project → Detail | container transform 420 мс | fade-through |
| Composer 1→5 строк | spring spatial, baseline неподвижен | мгновенный resize |
| Send → Stop | path morph 180 мс + scale 1→0.92→1 | crossfade 100 мс |
| Attachment появляется | fade + slide 8 dp, 180 мс | fade 100 мс |
| Tool pending→running | icon morph 180 мс | crossfade |
| Tool running→success | check draw 220 мс, один раз | статичный check |
| Permission | scale 0.98→1 + tonal pulse один раз, 280 мс | статичный container |
| Model sheet | жестовый spring, scrim fade | стандартный sheet без overshoot |
| New-message pill | slide снизу 12 dp + fade 180 мс | fade |
| Error | horizontal offset ±3 dp, два цикла за 220 мс | цвет + иконка |
| Offline | saturation/color 280 мс | мгновенная смена |

### 2.2 Что удалить

- infinite pulse из `ProjectCard.kt`;
- декоративные infinite transitions вне AI activity indicator;
- текущий `PocketAnimatedIcon`, если он остаётся только fade-обёрткой;
- название `PocketLottie`, пока внутри нет Lottie runtime.

## 3. Application shell

### Phone

`NavigationSuiteScaffold` остаётся корнем, но detail screens скрывают bottom navigation. Active session surface встраивается в scaffold и резервирует высоту — не рисуется overlay поверх списка. Adaptive navigation предназначена именно для переключения bar/rail по размеру окна.[^https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation]

Слои снизу вверх:

1. background;
2. screen content;
3. active-session bar, если пользователь не внутри этой сессии;
4. navigation bar;
5. snackbar host;
6. modal sheets/dialogs.

### Tablet/expanded

- rail 80 dp;
- list pane 320–360 dp;
- detail pane занимает остаток;
- optional context pane 320 dp для diff/file outline;
- разделитель 1 dp `outlineVariant`;
- chat composer находится только в detail pane;
- back сначала закрывает context pane, затем detail selection.

## 4. Главный vertical slice: Chat

Текущий монолитный `ChatScreen.kt` необходимо разделить. Источник проблемы — общий screen state и тяжёлый picker внутри той же композиции.[^https://github.com/SH20FK/PocketCLI/blob/735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1/feature/chat/src/main/java/com/pocketcli/feature/chat/ChatScreen.kt]

### 4.1 Файловая структура

```text
feature/chat/
  ChatRoute.kt
  ChatScreen.kt
  ChatViewModel.kt
  ChatUiState.kt
  model/ChatNode.kt
  model/ComposerState.kt
  components/ChatTopBar.kt
  components/ChatTimeline.kt
  components/UserMessage.kt
  components/AssistantMessage.kt
  components/StreamingTail.kt
  components/ToolTimelineItem.kt
  components/PermissionTimelineItem.kt
  components/DiffSummaryItem.kt
  components/ChatComposer.kt
  components/AttachmentStrip.kt
  modelpicker/ModelPickerRoute.kt
  modelpicker/ModelPickerSheet.kt
  modelpicker/ModelPickerViewModel.kt
```

### 4.2 Экран 360 × 800 dp

#### Top bar — 64 dp

- back: 48 × 48;
- центральная колонка: session title, ниже `project · runtime`;
- справа: files и overflow, по 48 × 48;
- статус агента не отдельная огромная pill: маленькая status icon 8 dp + текст во второй строке;
- model chip убрать из app bar.

При тапе по заголовку открывается Session info sheet. Выбор модели доступен через composer chip и overflow.

#### Timeline

- горизонтальные поля 16 dp;
- расстояние между turns 24 dp;
- внутри составного agent turn — 8 dp;
- user message справа, ширина `min(intrinsic, 88% viewport)`, `secondaryContainer`, radius 18 dp;
- agent answer без bubble, занимает доступную ширину;
- code block отдельным technical container;
- actions под сообщением скрыты до long press/tap или завершения stream;
- reasoning — одна строка `Думал 18 с` с chevron; контент раскрывается без отдельной карточки внутри карточки;
- permission и tool calls стоят в том месте timeline, где возникли, а не в конце экрана.

#### Streaming tail

```kotlin
@Immutable
data class StreamingTailUi(
    val messageId: String,
    val visibleText: String,
    val phase: AgentPhase,
    val currentTool: ToolSummary? = null,
    val startedAt: Instant,
)
```

- UI snapshot обновлять раз в 32–50 мс;
- Markdown parse throttled 200 мс или после `completed`;
- маленький living indicator находится после последней строки;
- TalkBack получает завершённые смысловые блоки, а не каждый delta;
- при уходе пользователя вверх tail продолжает обновляться, но scroll не двигается.

#### Composer

Высота:

- collapsed: 56 dp;
- typing: 56–136 dp;
- attachments: +52 dp strip;
- bottom inset включён;
- outer margin 8 dp.

Расположение:

1. attachment strip;
2. text field;
3. нижняя action row: `+`, model chip, flexible spacer, send/stop.

Состояния:

```kotlin
sealed interface ComposerMode {
    data object Empty : ComposerMode
    data object Typing : ComposerMode
    data object WithAttachments : ComposerMode
    data object Sending : ComposerMode
    data object Running : ComposerMode
    data object AwaitingPermission : ComposerMode
    data object OfflineDraft : ComposerMode
}
```

- Send активен только при тексте/attachment;
- Running заменяет Send на Stop в том же 48 dp slot;
- модель показывается коротко: `Sonnet 4`, полное имя — в sheet;
- `+` открывает вертикальное FAB menu: файл, изображение, камера, путь, контекст;
- действия должны вызывать реальные Activity Result APIs; никаких `file_1.kt`.[^https://github.com/SH20FK/PocketCLI/blob/735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1/feature/chat/src/main/java/com/pocketcli/feature/chat/ChatScreen.kt#L350-L375]

### 4.3 Tool item

Collapsed row 48–56 dp:

- leading 24 dp semantic icon;
- `Terminal` / `Read file` / `Edit`;
- одна строка человеческого summary;
- trailing duration + state icon;
- background только при running/error/permission;
- stdout не показывается inline.

Tap открывает Tool Output sheet. Running row может иметь один animated progress stroke, но не spinner возле каждого текста.

### 4.4 Permission item

- `tertiaryContainer`, radius 14 dp;
- заголовок: действие человеческим языком;
- scope одной строкой;
- risk icon + label;
- `Отклонить` tonal, `Разрешить` filled;
- destructive разрешение использует error только на risk и primary action;
- после ответа карточка схлопывается в read-only result row за 280 мс.

### 4.5 Diff summary

- `3 файла · +48 −12`;
- до трёх путей файлов;
- кнопка `Открыть diff`;
- tap: shared bounds summary → diff header;
- сам diff не вставлять целиком в chat timeline.

## 5. Model Picker — полный replacement

### 5.1 UI state отдельно от Chat

```kotlin
@Immutable
data class ModelPickerUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val favourites: ImmutableList<ModelRow> = persistentListOf(),
    val groups: ImmutableList<ProviderGroup> = persistentListOf(),
    val selectedId: String? = null,
    val error: UiText? = null,
)
```

Chat хранит только `selectedModelId`. Открытие sheet не должно создавать новый список messages.

### 5.2 Компоновка

Phone sheet:

- initial height 72%; expandable до full;
- drag handle;
- title `Выбор модели`;
- search field 56 dp;
- favorites horizontal group только если ≤4; иначе обычная секция списка;
- provider header sticky;
- row 64 dp: model name, provider/supporting info, selected check;
- capability icons максимум три и только с tooltip;
- trailing radio button не нужен одновременно с check и selected container — выбрать один паттерн;
- offline cached models доступны, рядом `Данные могут быть устаревшими`.

Search:

- normalize lowercase once;
- debounce 120 мс;
- stable keys: `${providerId}:${modelId}`;
- `contentType`: header/model/error;
- empty state без иллюстрации: `Ничего не найдено` + `Очистить поиск`.

### 5.3 Анимация

- sheet следует пальцу;
- секции не stagger-анимировать при каждом поисковом символе;
- selected state меняет container color 180 мс и рисует check 180–220 мс;
- после выбора sheet закрывается только когда ViewModel подтвердил изменение;
- при ошибке остаётся открыт и показывает inline error.

## 6. Sessions

### Верх

- Large top app bar `Чаты`;
- subtitle — статус default runtime;
- actions: search, activity;
- FAB `Новый чат`.

### Контент

1. `Продолжаются` — максимум две active cards;
2. `Требуется действие` — только approvals/errors;
3. `Недавние` — плотные rows, а не большие cards.

Session row 72 dp:

- leading project/avatar shape 40 dp;
- title одна строка;
- summary одна строка;
- trailing time и status glyph;
- divider начинается после avatar;
- swipe archive с undo;
- long press multi-select.

Active card 104–120 dp:

- проект и session title;
- текущее действие;
- elapsed;
- `Открыть` и icon Stop;
- никакой looping pulse; progress виден только при реальной работе.

## 7. Projects

### Project list

Project card сократить до трёх вертикальных уровней:

1. title + overflow;
2. path/branch;
3. status summary + last activity.

Не показывать одновременно отдельными pills branch, runtime, sessions, dirty и online. Правило приоритета:

- строка 2: `main · 3 изменения`;
- строка 3: `Локальный runtime · 2 активные сессии · 5 мин`;
- online/error — icon + semantic color, не пульсирующая точка.

Tap делает container transform в Project Detail. `Новый чат` находится в detail или contextual action, а не маленькой кнопкой 32 dp на каждой карточке.

### Project detail

- hero header: name, path, branch, runtime state;
- connected group: Chat / Files / Git / Terminal;
- default section: active sessions, uncommitted changes, recent files, project commands;
- команды показываются list actions с последним result, а не декоративные chips;
- phone переключает внутренние destinations;
- tablet оставляет project list слева и detail справа.

Clone/import не показывать как успешные до реальной операции. Текущий staged delay необходимо удалить.[^https://github.com/SH20FK/PocketCLI/blob/735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1/feature/projects/src/main/java/com/pocketcli/feature/projects/ProjectsViewModel.kt#L235-L335]

## 8. Onboarding

Это отдельный navigation graph и first-run state, а не dialog из Settings. Текущий запуск сразу в Sessions заменить.[^https://github.com/SH20FK/PocketCLI/blob/735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1/app/src/main/java/com/pocketcli/MainActivity.kt]

Flow:

`Splash recovery → Welcome → Local/Remote choice → Setup → Verification → Project → First chat`

Welcome:

- logo/AI shape 96 dp;
- headline максимум 2 строки;
- две selectable cards по 112 dp;
- одна primary CTA;
- никакой carousel.

Local setup:

- размер загрузки, свободное место, ожидаемое время;
- один progress container;
- этапы меняются `AnimatedContent`;
- лог скрыт под Details;
- при ошибке человеческое объяснение + retry + diagnostics.

Remote setup:

- QR / LAN / Manual;
- Test connection: button → progress → success/error в том же контейнере;
- показать URL, latency, auth, server version;
- password/API key никогда не возвращать в UI.

## 9. Settings, Runtime и OTA

Settings root:

- search появляется после >25 пунктов;
- категории — обычные grouped rows, не карточка на каждую строку;
- tablet — list-detail;
- каждый row 64–72 dp;
- supporting text максимум две строки;
- switch только для немедленного boolean setting.

Runtime Center:

- одна hero card состояния;
- 2×2 metrics grid только на ширине ≥360 dp;
- temperature/battery показывать лишь если доступны;
- процессы — list, не chip cloud;
- start/stop меняет state с одним controlled transition.

OTA:

- states: Checking / UpToDate / Available / Downloading / Verifying / Ready / Error;
- download progress determinate;
- verification steps последовательны, но не притворяются прогрессом;
- install запускает системный installer;
- никаких custom installation success screen до подтверждённого запуска новой версии.

## 10. Компонентные API

Создать или заменить:

```kotlin
@Composable
fun PocketScreenScaffold(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    activeSession: ActiveSessionUi? = null,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
)
```

```kotlin
@Composable
fun PocketStatus(
    state: PocketStatusState,
    compact: Boolean = true,
)
```

```kotlin
@Composable
fun PocketAnimatedStateIcon(
    from: StateGlyph,
    to: StateGlyph,
    contentDescription: String,
    reduceMotion: Boolean,
)
```

```kotlin
@Composable
fun PocketTechnicalSurface(
    modifier: Modifier = Modifier,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
)
```

```kotlin
@Composable
fun PocketAsyncButton(
    state: AsyncActionState,
    onClick: () -> Unit,
    label: String,
    successLabel: String,
    errorLabel: String,
)
```

Все components обязаны принимать `Modifier`, не хранить ViewModel, не запускать network/file I/O и иметь Preview минимум для default/loading/error/large font.

## 11. Что именно заменить в текущем проекте

| Текущий файл | Действие |
| --- | --- |
| `ChatScreen.kt` | разделить по структуре раздела 4; оставить только orchestration layout |
| `ChatViewModel.kt` | отделить stable messages, streaming tail, composer и picker state |
| `MarkdownText.kt` | добавить streaming/plain mode, throttled parse и completed mode |
| `PocketAnimatedIcon.kt` | заменить на утверждённые path/vector transitions или честный crossfade API |
| `PocketLottie.kt` | удалить/переименовать; подключить Lottie только после выбора реальных assets |
| `PocketMotion.kt` | превратить в CompositionLocal scheme и использовать во всех компонентах |
| `Theme.kt` | предоставить motion, code colors, reduced-motion state |
| `ActiveSessionBar.kt` | встроить в scaffold с reserved inset |
| `ProjectCard.kt` | переписать по трёхуровневой иерархии, удалить infinite pulse |
| `ProjectsViewModel.kt` | убрать fake delays; добавить настоящие operation states |
| `MainActivity.kt` | отдельные onboarding/main graphs, detail navigation transitions |
| `SettingsScreen.kt` | grouped rows + adaptive list-detail, lifecycle-aware collection |
| все screens | `collectAsStateWithLifecycle`, strings resources, stable content keys |

Файловый I/O из композиции удалить; текущий `File.listFiles()` в Project Detail должен перейти в repository на `Dispatchers.IO`.[^https://github.com/SH20FK/PocketCLI/blob/735ebe3d6fc33b0397cd24f9eb61f80536ebbcb1/feature/projects/src/main/java/com/pocketcli/feature/projects/ProjectDetailScreen.kt#L242-L255]

## 12. Assets

В MVP нужны только:

1. Pocket Prompt adaptive icon + monochrome;
2. logo reveal 550–750 мс;
3. один AI activity asset/state shape;
4. empty projects static/vector illustration;
5. success/error animated state, если невозможно качественно сделать AVD.

Не добавлять набор случайных Lottie. Production assets локальные, без bitmap, с зафиксированной лицензией. Compose animation API использовать для layout и state transitions.[^https://developer.android.com/develop/ui/compose/animation/quick-guide]

## 13. Performance architecture

### Streaming

- delta ingest: background channel;
- mutable buffer внутри repository;
- UI flush: 32–50 мс;
- stable history и tail — разные flows;
- Markdown: plain during fast stream, parse snapshot ≤5 раз/с, final parse once;
- database: batch/checkpoint, не запись каждого token;
- autoscroll только при `isAtBottom == true`.

### Model picker

- cache per connection;
- immutable prepared rows;
- isolated state holder;
- stable keys/content types;
- no message list invalidation;
- no network request on every open.

### Lists

- `key` и `contentType` в каждом `LazyColumn`;
- immutable UI models;
- тяжёлый Markdown только для visible completed nodes;
- code highlighting только visible range;
- file operations только IO dispatcher.

## 14. Preview и тестовая матрица

Каждый screen обязан иметь:

- compact 360×800;
- compact 412×915;
- expanded 1280×800;
- light;
- dark;
- fallback Indigo;
- dynamic color sample;
- RU;
- EN;
- font scale 1.0 и 2.0;
- loading, empty, error, offline и populated.

Screenshot tests:

- Sessions;
- Chat empty;
- Chat streaming;
- Chat with tool + permission + diff;
- Model picker 5/50/300 models;
- Projects populated/empty;
- Project detail;
- Runtime;
- Settings;
- OTA available/downloading/error.

Macrobenchmark:

- startup;
- Sessions → Chat;
- open model picker;
- scroll 200 messages;
- stream 1 000 deltas;
- composer expand;
- open 1 000-file project.

## 15. Порядок внедрения

### PR 1 — foundation

- tokens, theme, motion local;
- lifecycle-aware state;
- screen scaffold;
- remove fake pulse;
- previews infrastructure.

### PR 2 — streaming engine

- stable history + buffered tail;
- smart autoscroll;
- Markdown modes;
- benchmark before/after.

### PR 3 — chat UI

- timeline nodes;
- composer state machine;
- tool/permission/diff;
- actual attachments;
- screenshots.

### PR 4 — model picker

- isolated ViewModel;
- cache/search/grouping;
- sheet/side sheet;
- benchmark.

### PR 5 — navigation and motion

- transitions;
- adaptive list-detail;
- active session scaffold integration;
- reduced motion.

### PR 6 — Projects/Settings/Runtime/OTA

- redesign по этому документу;
- удалить fake flows;
- добавить реальные operation states.

## 16. Definition of Done

- [ ] Ни одного fake-success сценария.
- [ ] Ни одного tap target меньше 48 dp.
- [ ] Ни одной user-facing строки вне resources.
- [ ] Model picker не пересобирает timeline.
- [ ] Streaming не запускает scroll animation на каждом delta.
- [ ] Motion соответствует таблице и отключается.
- [ ] Active session bar не перекрывает content.
- [ ] Chat/Project используют spatial transition либо documented fallback.
- [ ] Есть screenshot matrix и macrobenchmark report.
- [ ] TalkBack читает состояния смысловыми блоками.
- [ ] 200% font scale не обрезает действия.
- [ ] Release CI не публикует debug fallback.

<callout icon="✅" color="green_bg">Агенту не нужно «придумывать дизайн». Его задача — реализовать эту спецификацию в указанном порядке, приложить before/after screenshots и результаты benchmark. Любой новый компонент вне документа сначала добавляется сюда с назначением, состояниями и motion.</callout>
