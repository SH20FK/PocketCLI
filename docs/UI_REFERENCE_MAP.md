# PocketCLI — UI Reference Map

Карта заимствований и визуальных решений на основе анализа референс-проектов согласно спецификации UI/UX flow.

## 1. Сводная матрица компонентов

| Компонент PocketCLI | Файлы-референсы | Что берём как принцип | Что сознательно не берём |
| --- | --- | --- | --- |
| `PocketChatScreen` | LastChat `ChatPage.kt`, Read You `ReadingPage.kt` | Слои экрана, чтение длинного ответа как документа, плавная клавиатура | Чужую state/business logic и жесткую привязку к мессенджерным пузырькам |
| `PocketComposer` | LastChat `MinimalChatInput.kt` | Состояния (`Empty / Typing / Running / AwaitingPermission / Offline`), чипы вложений, morph Send→Stop | AGPL-код, избыточные web/voice зависимости |
| `ActiveSessionBar` | Metrolist `MiniPlayer.kt`, Podium `FloatingMediaPlayer.kt` | Persistent surface активной сессии над навигацией, статус действия агента, elapsed time, Open/Stop | Музыкальные жесты свайпа трека, обложки альбомов |
| `PocketSettingsScaffold` | LastChat `SettingsAdaptiveScaffold.kt`, Kori `SettingsListPane.kt` / `SettingsDetailPane.kt` | Adaptive list-detail разделение для планшетов и больших экранов | Избыточные desktop-панели и чужие роуты |
| `RuntimeCenter` | RvKernel `HomeScreen.kt`, MD3-Windows widgets | Иерархию технических показателей (uptime, RAM, процессы, температура) без перегруза | Бесконечные графики и превращение приложения в терминал |
| `ProjectCard` | Read You `ArticleItem.kt`, Metrolist tonal cards | Спокойная визуальная иерархия строк, стабильные ключи, статус-глифы | Избыточный masonry-grid и разноцветный визуальный шум |
| `CloneRepositoryScreen` | DialogX `WaitDialog.java`, LastChat modal flows | Пошаговый мастер с карточкой прогресса этапов и валидацией | View-based DialogX библиотеки |
| `FileDiffViewer` | Kori side sheets, Read You code view | Unified diff на смартфонах, сворачивание неизменённого контекста, цветовая подсветка строк | Пакеты со сторонними тяжелыми web-движками |
| `PermissionCard` / Sheet | LastChat `ChatMessageV2.kt`, DialogX `BottomDialog.java` | Контекстное объяснение опасности действия, права на раз/сессию, безопасное отклонение | Стандартный безликий `AlertDialog` |

## 2. Анализ референс-проектов и принципы

### 2.1 LastChat (AGPL-3.0)
- **Изучено**: `ChatPage.kt`, `MinimalChatInput.kt`, `ChatMessageV2.kt`, `ActivityPill.kt`, `ModelList.kt`, `SettingsAdaptiveScaffold.kt`.
- **Заимствовано**: Архитектура состояний композера, разделение сообщения на reasoning, tool call, permission и markdown документ, выбор модели в bottom sheet.
- **Ограничение**: Код не копируется; создается собственная реализация на Kotlin Coroutines, Room v2 и Material 3 Expressive.

### 2.2 Read You (GPL-3.0)
- **Изучено**: `FlowPage.kt`, `ArticleList.kt`, `ArticleItem.kt`, `ReadingPage.kt`, `SettingsPage.kt`.
- **Заимствовано**: Воздух, спокойные списки с tonal container фонами, типографика чтения длинных технических текстов, чистые отступы 4dp/8dp/16dp.
- **Ограничение**: Код не переносится.

### 2.3 Metrolist & Podium (GPL-3.0)
- **Изучено**: `MiniPlayer.kt`, `FloatingMediaPlayer.kt`, `BottomSheet.kt`, `AppNavigation.kt`.
- **Заимствовано**: Метафора «персистентного мини-плеера» превращена в `ActiveSessionBar` — плашку текущей выполняющейся сессии над bottom navigation bar с кнопками Stop/Open.
- **Ограничение**: Исключены музыкальные жесты, drag-to-dismiss для процессов и artwork-поверхности.

### 2.4 Kori (Apache-2.0 / MIT)
- **Изучено**: `AdaptiveNavigationDrawerLayout.kt`, `SettingsListPane.kt`, `NoteSideSheet.kt`.
- **Заимствовано**: Правила адаптивного поведения для compact / medium / expanded размеров экранов.

### 2.5 RvKernel Manager & MD3-Windows
- **Изучено**: `HomeScreen.kt`, `SettingsPreference.kt`, виджеты MD3.
- **Заимствовано**: Компактное представление метрик рантайма (память, статус процесса, батарея) в виде карточек с крупными значениями и подписями.

### 2.6 DialogX (Apache-2.0)
- **Изучено**: `BottomDialog.java`, `WaitDialog.java`.
- **Заимствовано**: UX переходов `loading -> success/error` и неблокирующих floating feedback элементов.
