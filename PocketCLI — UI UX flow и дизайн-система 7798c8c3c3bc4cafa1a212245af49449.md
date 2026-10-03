# PocketCLI — UI/UX flow и дизайн-система

<aside>
🎯

PocketCLI должен ощущаться не как терминал на маленьком экране, а как нативный мобильный пульт управления агентом: спокойный в ожидании, выразительный в ключевых действиях, плотный только там, где пользователь действительно читает код.

</aside>

## 1. Дизайн-направление

### Базовая идея: «живой рабочий стол агента»

- **Нативный Android прежде всего:** edge-to-edge, динамические цвета, системные жесты, правильные insets и предсказуемый Back.
- **Expressive, но не игрушечный:** крупные формы и пружинная motion-система используются для состояния и иерархии, а не как декор.
- **Chat-first:** основная работа происходит в диалоге; файлы, diff, terminal и runtime открываются как связанные рабочие поверхности.
- **Calm by default:** никакого бесконечного пульсирования. Анимация появляется при подключении, отправке, смене состояния и запросе внимания.
- **Progressive disclosure:** в ленте показывается итог действия; полный stdout, reasoning и сложный diff открываются по запросу.
- **Dark-first, не dark-only:** тёмная тема особенно важна для кода, но светлая и dynamic color должны быть полноценными.

Jetpack Compose поддерживает Material You и Material 3 Expressive, включая обновления тем, компонентов, motion и типографики; система также рассчитана на динамические цвета и визуальный язык Android 16.[[1]](https://developer.android.com/develop/ui/compose/designsystems/material3)

## 2. Что взять из референсов

<aside>
📌

Этот раздел предназначен прямо для агента-исполнителя. Не ограничиваться README и скриншотами: открыть перечисленные файлы, проследить композицию, состояния, insets и motion. Из GPL/AGPL-проектов не копировать код — только воспроизводить идеи собственной реализацией.

</aside>

### Порядок изучения

1. Сначала **LastChat**: чат, composer, сообщения и adaptive settings.
2. Затем **Read You**: ритм списков, top bars и спокойная визуальная иерархия.
3. Затем **Metrolist/Podium**: persistent active-session surface, navigation и bottom sheets.
4. Затем **Kori**: tablet/list-detail и side sheets.
5. **RvKernel Manager**: технический dashboard.
6. **DialogX и MD3-Windows**: только motion, формы и визуальные состояния.
7. **Musify**: только общие UX-идеи; это Flutter, не переносить архитектуру в Compose.

### LastChat — основной референс чата

Ветка репозитория: `LastChat`.

- [ChatPage.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt) — изучить каркас полного chat screen: top bar, список сообщений, состояние текущего диалога, drafts, обработку клавиатуры и связывание composer с лентой. Для PocketCLI отсюда нужен общий порядок слоёв экрана, но без копирования бизнес-логики.
- [MinimalChatInput.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/ai/MinimalChatInput.kt) — главный референс composer: attachments, voice, отправка, pending tool approval и разные состояния ввода. Агент должен отдельно выписать состояния composable и сделать для PocketCLI собственную state machine `Empty / Typing / WithAttachments / Running / AwaitingPermission / Offline`.
- [MinimalChatInputAttachmentSupport.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/ai/MinimalChatInputAttachmentSupport.kt) — preview и lifecycle вложений. Использовать как чек-лист для изображений, файлов и удаления вложения до отправки.
- [ChatMessageV2.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/chat/ChatMessageV2.kt) — группировка message nodes в turns, rich content и составные сообщения. Для PocketCLI адаптировать идею к `text + reasoning + tool calls + permission + diff`, не делать один гигантский composable.
- [ChatMessageActions.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt) — действия над сообщением. Изучить порядок появления copy/retry/branch и не выводить все действия постоянно.
- [ActivityPill.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/chat/ActivityPill.kt) — компактное представление активности. Использовать как референс для `Думает / Выполняет команду / Ждёт разрешения`.
- [ActivityTimelineSheet.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/chat/ActivityTimelineSheet.kt) — история активности в sheet. Для PocketCLI сюда ложится полный список tool calls и runtime events.
- [InputPickerSheets.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/ai/InputPickerSheets.kt) — организация picker-sheet для контекста и вложений.
- [ModelList.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt) — список моделей, поиск и presentation модели. Перенести паттерн в model picker PocketCLI.
- [SettingPage.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt) — структура корневых настроек.
- [SettingsAdaptiveScaffold.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingsAdaptiveScaffold.kt) — обязательный референс для phone/tablet list-detail настроек.
- [SecureOutlinedTextField.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/SecureOutlinedTextField.kt) — UX секретных полей. Использовать при вводе API keys и паролей серверов.
- [Theme.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt), [Type.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt) и [CodeColor.kt](https://github.com/Cocolalilal/LastChat/blob/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/CodeColor.kt) — посмотреть разделение общей темы, типографики и цветов кода. В PocketCLI эти слои тоже должны быть раздельными.

### Read You — ритм, списки и спокойный Material You

- [FlowPage.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/flow/FlowPage.kt) — главный референс для списка сессий: top-level scaffold, прокрутка, FAB, состояния ленты и взаимодействие с панелями.
- [ArticleList.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/flow/ArticleList.kt) — плотный длинный список. Изучить spacing, stable keys и поведение больших коллекций.
- [ArticleItem.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/flow/ArticleItem.kt) — визуальная иерархия одной строки. Использовать как основу идеи `SessionRow`, заменив metadata статьи на проект, модель, статус и время.
- [SearchBar.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/flow/SearchBar.kt) — поисковое состояние списка без отдельного тяжёлого экрана.
- [ReadingPage.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/reading/ReadingPage.kt) — чтение длинного контента. Это референс для ответа агента и Markdown-документа в чате.
- [TopBar.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/reading/TopBar.kt) и [BottomBar.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/home/reading/BottomBar.kt) — контекстные действия, которые не должны постоянно занимать место.
- [SettingsPage.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/settings/SettingsPage.kt), [SettingItem.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/settings/SettingItem.kt) и [SelectableSettingGroupItem.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/settings/SelectableSettingGroupItem.kt) — референс спокойной структуры настроек и повторно используемых setting rows.
- [Theme.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/theme/Theme.kt) — dynamic color, dark theme и единая точка применения темы.
- [RYDialog.kt](https://github.com/ReadYouApp/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/component/base/RYDialog.kt) — собственная обёртка над dialog. В PocketCLI нужен аналогичный `PocketDialog`, чтобы не размазывать размеры и отступы по экранам.

### Metrolist — активная сессия, navigation и sheets

- [AppNavigation.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/component/AppNavigation.kt) — содержит реализации navigation rail и navigation bar. Использовать для проверки adaptive navigation, selected state и отступов.
- [MiniPlayer.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/player/MiniPlayer.kt) — ключевой референс persistent surface. В PocketCLI вместо трека показывать одну активную сессию: проект, текущее действие, progress/status и Stop. Не копировать player gestures буквально.
- [Player.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt) — переход от компактной поверхности к подробной. Изучить container transition и вложенные состояния; адаптировать к `active session → chat`.
- [BottomSheet.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/component/BottomSheet.kt) и [BottomSheetPage.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/component/BottomSheetPage.kt) — механика expand/collapse/dismiss и вложенной sheet-page. Это референс для tool output, model picker и permission details.
- [BottomSheetMenu.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/component/BottomSheetMenu.kt) — menu в нижней шторке; проверить touch targets и группировку destructive actions.
- [SettingsScreen.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/screens/settings/SettingsScreen.kt) и [Material3SettingsGroup.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/component/Material3SettingsGroup.kt) — категории настроек и reusable settings containers.
- [AppearanceSettings.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/screens/settings/AppearanceSettings.kt) и [ThemeScreen.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/screens/settings/ThemeScreen.kt) — выбор dynamic color, палитр и dark mode. Не переносить все опции: PocketCLI нужен короткий понятный набор.

### Kori — adaptive layout и side sheets

- [AdaptiveNavigationDrawerLayout.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/main/AdaptiveNavigationDrawerLayout.kt) — изучить переключение drawer/layout по размеру окна.
- [NavigationDrawerContent.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/main/NavigationDrawerContent.kt) — иерархия разделов и выбранного пункта.
- [NoteSideSheet.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/note/NoteSideSheet.kt) — прямой референс для outline/metadata панели файла или diff на планшете.
- [SettingsListPane.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/setting/SettingsListPane.kt) и [SettingsDetailPane.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/setting/SettingsDetailPane.kt) — list-detail настройки на широком экране.
- [SortOptionBottomSheet.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/component/dialog/SortOptionBottomSheet.kt) — компактный single-choice sheet для сортировки сессий/файлов.
- [Theme.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/commonMain/kotlin/org/yangdai/kori/presentation/theme/Theme.kt) и [Theme.android.kt](https://github.com/YangDai2003/Kori/blob/master/composeApp/src/androidMain/kotlin/org/yangdai/kori/presentation/theme/Theme.android.kt) — полезный пример разделения общей и Android-specific темы.

### Podium — свежие M3 Expressive компоненты

- [SwitchableDynamicMaterialExpressiveTheme.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/component/common/SwitchableDynamicMaterialExpressiveTheme.kt) — посмотреть реальное включение expressive theme и dynamic color.
- [Theme.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/theme/Theme.kt) — базовые theme tokens приложения.
- [Navigation.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/navigation/Navigation.kt) — структура destination и правило скрытия navigation bar на detail-экранах.
- [FloatingMediaPlayer.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/component/media/FloatingMediaPlayer.kt) — второй референс для persistent active-session bar; сравнить с Metrolist и сделать собственную более компактную реализацию.
- [MediaPlayerBottomSheet.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/dialog/bottomsheet/media/MediaPlayerBottomSheet.kt) — full-height sheet со сложным состоянием.
- [SettingsPane.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/route/settings/SettingsPane.kt) и [SettingsAppearancePane.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/route/settings/pane/SettingsAppearancePane.kt) — современная организация settings detail.
- [SettingsListItem.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/component/settings/SettingsListItem.kt), [SettingsSwitchListItem.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/component/settings/SettingsSwitchListItem.kt) и [SettingsSliderListItem.kt](https://github.com/aimok04/podium/blob/main/app/src/main/java/app/podiumpodcasts/podium/ui/component/settings/SettingsSliderListItem.kt) — reusable settings components.

### RvKernel Manager — технический dashboard

- [HomeScreen.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/home/HomeScreen.kt) — посмотреть, как раскладываются технические показатели, версии и состояния устройства. В PocketCLI применять только внутри Runtime Center.
- [NavigationBar.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/navigation/NavigationBar.kt) и [NavigationRoute.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/navigation/NavigationRoute.kt) — простой референс bottom navigation и destination model.
- [SettingsScreen.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/settings/SettingsScreen.kt) и [SettingsPreference.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/settings/SettingsPreference.kt) — технические настройки и preference rows.
- [Theme.kt](https://github.com/Rve27/RvKernel-Manager/blob/main/app/src/main/java/com/rve/rvkernelmanager/ui/theme/Theme.kt) — M3 Expressive/dynamic theme проекта.

### Musify — идеи navigation, mini-player и настроек

Musify написан на Flutter. Файлы используются только как визуальный/UX-референс; Compose-реализацию строить самостоятельно.

- [bottom_navigation_page.dart](https://github.com/gokadzev/Musify/blob/master/lib/screens/bottom_navigation_page.dart) — bottom navigation и сосуществование navigation с mini-player.
- [mini_player.dart](https://github.com/gokadzev/Musify/blob/master/lib/widgets/mini_player.dart) — размеры, drag behavior, artwork/metadata/control zones. Для PocketCLI заменить их на status icon/project/current action/Stop.
- [home_page.dart](https://github.com/gokadzev/Musify/blob/master/lib/screens/home_page.dart) — секции главного экрана и горизонтальные подборки.
- [settings_page.dart](https://github.com/gokadzev/Musify/blob/master/lib/screens/settings_page.dart) — длинные настройки; использовать скорее как список того, как не перегружать один экран.
- [app_themes.dart](https://github.com/gokadzev/Musify/blob/master/lib/theme/app_themes.dart) — набор light/dark/dynamic theme decisions.
- [flutter_bottom_sheet.dart](https://github.com/gokadzev/Musify/blob/master/lib/utilities/flutter_bottom_sheet.dart) и [bottom_sheet_bar.dart](https://github.com/gokadzev/Musify/blob/master/lib/widgets/bottom_sheet_bar.dart) — presentation и drag handle шторок.

### DialogX — состояния dialog/sheet и motion

DialogX в основном View-based. Не использовать его как UI-зависимость для Compose; изучать поведение и переходы.

- [BottomDialog.java](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/java/com/kongzue/dialogx/dialogs/BottomDialog.java) — lifecycle bottom dialog, drag/intercept и кнопки.
- [WaitDialog.java](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/java/com/kongzue/dialogx/dialogs/WaitDialog.java) и [TipDialog.java](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/java/com/kongzue/dialogx/dialogs/TipDialog.java) — переход `loading → success/warning/error`. Нужен как motion-референс для Test Connection и установки runtime.
- [PopTip.java](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/java/com/kongzue/dialogx/dialogs/PopTip.java) — неблокирующее сообщение с action; аналог для in-app event banner.
- [GuideDialog.java](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/java/com/kongzue/dialogx/dialogs/GuideDialog.java) — spotlight onboarding. Использовать максимум один раз для объяснения composer/tool approvals.
- [MaterialYouStyle.java](https://github.com/kongzue/DialogX/blob/master/DialogXMaterialYou/src/main/java/com/kongzue/dialogxmaterialyou/style/MaterialYouStyle.java) — формы, порядок кнопок и Material You styling.
- [anim_dialogx_bottom_enter.xml](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/res/anim/anim_dialogx_bottom_enter.xml) и [anim_dialogx_bottom_exit.xml](https://github.com/kongzue/DialogX/blob/master/DialogX/src/main/res/anim/anim_dialogx_bottom_exit.xml) — только timing/easing reference, не переносить XML-анимацию в Compose.

### MD3-Windows — только композиция status-виджетов

Это Rainmeter, не Android. Не копировать реализацию и не пытаться превратить PocketCLI в desktop dashboard.

- [Battery/Dark.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Battery/Battery%201/Dark.ini) и [Battery/Light.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Battery/Battery%201/Light.ini) — соотношение крупного значения, подписи и tonal container. Применимо к temperature/RAM/storage в Runtime Center.
- [Music 1/Dark.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Music/Music%201/Dark.ini) и [Music 1/Light.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Music/Music%201/Light.ini) — композиция active-state widget; использовать только как дополнительный референс active session card.
- [Weather 1/Dark.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Weather/Weather%201/Dark.ini) — пример hero-value и secondary metadata.
- [WidgetSettings.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Settings/WidgetSettings.ini) и [HomeSettings.ini](https://github.com/Runixe786/MD3-Windows/blob/main/Settings/HomeSettings.ini) — посмотреть, как настройки сгруппированы визуально; саму структуру Rainmeter не переносить.

### Обязательный результат изучения для агента

Перед реализацией агент должен создать в репозитории `docs/UI_REFERENCE_MAP.md` с таблицей:

| Компонент PocketCLI | Файлы-референсы | Что берём | Что сознательно не берём |
| --- | --- | --- | --- |
| `PocketChatScreen` | LastChat `ChatPage.kt`, Read You `ReadingPage.kt` | слои экрана, чтение длинного контента | чужую state/business logic |
| `PocketComposer` | LastChat `MinimalChatInput.kt` | состояния и attachments | AGPL-код и лишние AI-функции |
| `ActiveSessionBar` | Metrolist `MiniPlayer.kt`, Podium `FloatingMediaPlayer.kt` | persistent surface и переход в detail | музыкальные gestures/artwork |
| `PocketSettingsScaffold` | LastChat `SettingsAdaptiveScaffold.kt`, Kori settings panes | adaptive list-detail | конкретные destinations |
| `RuntimeCenter` | RvKernel `HomeScreen.kt`, MD3-Windows widgets | иерархию технических значений | desktop dashboard и постоянные графики |

После этого сделать собственные wireframes/Compose previews. Реализацию начинать только после фиксации карты, чтобы дизайн не свёлся к компонентам «по названию».

| Референс | Что заимствуем как принцип | Чего не копируем |
| --- | --- | --- |
| Read You | Воздух, крупные заголовки, tonal surfaces, спокойные списки, понятная структура настроек | Конкретные компоненты и код: GPL-3.0 |
| Metrolist | Иммерсивная активная поверхность, mini-player как аналог статуса выполняющейся сессии, богатая тема | Музыкальную метафору целиком и код GPL-3.0 |
| Musify | Чистые bottom sheets, динамические палитры, простую навигацию | Реализацию Flutter и код GPL-3.0 |
| DialogX | Плавные переходы loading → success/error, неблокирующие уведомления, качественные bottom sheets | Подключение библиотеки без необходимости: проект в основном View-based |
| MD3-Windows | Модульные status-виджеты, крупные числовые показатели, выразительные формы | Desktop-компоновку и перегруженный dashboard |
| LastChat | Ближайший референс для AI-чата: rich content, настройки провайдеров, локальный PRoot | Копирование AGPL-кода |
| Kori | Адаптивность, side sheet для длинного контента, клавиатура/мышь, многоформатный редактор | Полную desktop-плотность на телефоне |
| RvKernel Manager | Представление технических метрик без превращения интерфейса в терминал | Постоянные панели с телеметрией на главном экране |
| Podium | Современные M3 Expressive navigation и media-like motion | Большие artwork-поверхности без функционального смысла |

Read You построен на Compose и Material You.[[2]](https://github.com/ReadYouApp/ReadYou) Metrolist использует Material 3, dynamic color и несколько палитр.[[3]](https://github.com/MetrolistGroup/Metrolist) LastChat — особенно полезный AI-chat референс с Material 3 Expressive, Markdown, кодом и локальным PRoot.[[4]](https://github.com/Cocolalilal/LastChat) Kori показывает M3 Expressive в адаптивном приложении с side sheet и поддержкой клавиатуры/мыши.[[5]](https://github.com/YangDai2003/Kori)

> **Лицензии:** Read You, Metrolist, Musify, RvKernel Manager и Podium используют GPL-3.0; LastChat — AGPL-3.0. Из них берём визуальные идеи и UX-паттерны, но не переносим код. DialogX — Apache-2.0.[[6]](https://github.com/kongzue/DialogX)
> 

## 3. Информационная архитектура

### Основная навигация

На телефоне — три постоянных раздела:

1. **Чаты** — активные и недавние сессии.
2. **Проекты** — рабочие папки и Git-репозитории.
3. **Настройки** — runtime, подключения, агенты, модели и внешний вид.

Не выносить «Runtime» в четвёртую вкладку. Его статус показывается компактной пилюлей в top app bar, а подробности находятся в настройках и карточке проекта.

### Адаптивное поведение

- **Compact:** bottom navigation; каждый экран занимает всю ширину.
- **Medium:** navigation rail; списки и detail могут сосуществовать.
- **Expanded/tablet:** permanent rail + list/detail; чат справа, список сессий слева; diff может открываться третьей панелью или поверх detail.
- **Landscape:** composer остаётся снизу справа; tool output не перекрывает весь чат без необходимости.
- **Keyboard/mouse:** `/` — поиск, `Ctrl+N` — новая сессия, `Ctrl+Enter` — отправка, `Esc` — закрыть sheet/остановить выделение.

`NavigationSuiteScaffold` предназначен для выбора подходящего navigation-компонента в зависимости от доступного размера окна.[[7]](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)

## 4. Сквозной пользовательский flow

### Первый запуск

`Splash → Welcome → Выбор способа работы → Настройка runtime/сервера → Проверка → Проект → Первый чат`

#### 4.1 Splash и восстановление

- Логотип PocketCLI по центру, без длинной обязательной анимации.
- Форма логотипа мягко morph-ится в expressive loading indicator только если загрузка дольше 300 мс.
- Под логотипом появляется конкретное состояние: «Восстанавливаем локальный runtime», «Проверяем Home PC», «Синхронизируем историю».
- При проблеме сразу доступны `Повторить`, `Открыть офлайн`, `Диагностика`.

#### 4.2 Welcome

Один экран, не карусель из пяти onboarding-страниц:

- заголовок «Агент для кода — прямо в телефоне»;
- две большие selectable-карточки:
    - **Подключиться к компьютеру или VPS**;
    - **Работать на этом устройстве**;
- текст о приватности и отсутствии собственного backend;
- primary action `Продолжить`.

#### 4.3 Remote setup

1. Выбор: `Сканировать QR`, `Найти в локальной сети`, `Ввести вручную`.
2. Поля: название, URL, логин, пароль, разрешение HTTP.
3. Кнопка `Проверить подключение`.
4. Loading indicator превращается в success/error state.
5. При успехе показываются версия OpenCode, latency, auth и предупреждения.

#### 4.4 Local setup

- Экран объясняет размер загрузки, свободное место и ожидаемое время.
- Стадии установки представлены одним progress-контейнером: `Загрузка → Проверка SHA-256 → Распаковка → Установка инструментов → Запуск`.
- Технический лог свёрнут; раскрывается по `Показать детали`.
- Ошибка всегда содержит: человеческое объяснение, шаг, кнопку повтора и экспорт диагностики.

#### 4.5 Выбор проекта

- `Открыть существующую папку`;
- `Клонировать Git-репозиторий`;
- `Создать пустой проект`;
- последние проекты — horizontal carousel только здесь, где он действительно ускоряет выбор.

#### 4.6 Первый чат

Пустое состояние показывает:

- название проекта и активный runtime;
- 3 стартовых действия: `Объяснить проект`, `Найти проблему`, `Начать задачу`;
- composer сразу в фокусе только после явного тапа, чтобы клавиатура не закрывала контент при входе.

## 5. Экран «Чаты»

### Top app bar

- Large/TwoRows top app bar: `Чаты` + строка состояния активного runtime.
- Слева при необходимости avatar проекта; справа `Поиск` и overflow.
- При прокрутке app bar сжимается до компактного.

### Содержимое

1. **Продолжаются сейчас** — максимум 2 крупные карточки.
    - проект;
    - агент и модель;
    - короткое текущее действие;
    - elapsed time;
    - `Открыть` и компактный `Стоп`.
2. **Требуется действие** — отдельный блок только при наличии approvals/errors.
3. **Недавние** — плотный список сессий по проектам и дате.
4. Empty state — иллюстрация из Material shapes, текст и `Новый чат`.

### Gestures

- Свайп сессии: архивировать; destructive action требует undo-snackbar.
- Long press: multi-select с contextual floating toolbar.
- FAB `Новый чат`; на широких экранах — extended FAB.

## 6. Экран «Проекты»

<aside>
📁

Проект — постоянная сущность с собственным `workspaceId`, файлами, Git-состоянием, runtime-профилем и сессиями. Это не одноразовый выбор пути перед чатом. Пользователь сначала узнаёт проект визуально, затем проваливается в его рабочую поверхность.

</aside>

### 6.1 Основной flow

```
Проекты
→ открыть существующий проект
→ Project Detail
→ Новый чат / Файлы / Git / Terminal

Проекты
→ FAB «Добавить»
→ Клонировать / Создать / Импортировать копию
→ подготовка workspace
→ Project Detail
→ Новый чат
```

При создании сессии из раздела «Чаты» показывается компактный project picker: последние 4–6 проектов, поиск и действие `Все проекты`. После выбора сессия сохраняет `workspaceId`, а не только строковый путь.

### 6.2 Экран списка проектов

#### Top app bar

Использовать `TwoRowsTopAppBar`:

- крупный заголовок `Проекты`;
- вторая строка: `6 проектов · 2 активны` либо состояние выбранного runtime;
- справа: поиск и overflow;
- при прокрутке заголовок сжимается, но поиск остаётся доступным;
- runtime status-pill открывает Runtime Center, а не переключает профиль случайным тапом.

#### Верхний блок

Если есть активные задачи, первой показывать компактную секцию `Работают сейчас`, максимум две карточки:

- проект;
- короткое действие агента;
- elapsed time;
- кнопки `Открыть` и `Стоп`;
- секция исчезает полностью, когда активных задач нет.

Не превращать экран в dashboard. Основной контент — список проектов.

#### Поиск, фильтры и сортировка

Под app bar располагается компактная строка:

- search state раскрывается через `AppBarWithSearch`;
- connected `ButtonGroup`: `Все`, `Локальные`, `Удалённые`;
- сортировка в bottom sheet: `Недавно открытые`, `По имени`, `По активности`;
- фильтр `Требуют внимания` появляется только при missing workspace, clone error или проблеме runtime;
- выбранные фильтры сохраняются, поисковый запрос — нет.

### 6.3 Карточка проекта

На телефоне использовать вертикальный список, не masonry-grid. Карточка занимает ширину контейнера и содержит три визуальных уровня.

#### Первая строка

- слева — shape-аватар проекта с folder/repository glyph;
- по центру — имя проекта, максимум две строки;
- под именем — сокращённый путь или owner/repository;
- справа — status icon и overflow;
- статус не кодировать одним цветом: иконка + короткая подпись.

#### Вторая строка

Небольшие metadata chips без превращения всей карточки в набор pills:

- branch: `main`;
- Git: `Чисто` либо `7 изменений`;
- runtime: `Локально`, `Home PC` или `VPS`;
- для non-Git workspace branch-chip не показывать вообще.

#### Нижняя строка

- `2 активные сессии` либо `Последний чат 3 ч назад`;
- trailing action `Открыть` не нужен: вся карточка кликабельна;
- если идёт clone/import, нижняя строка превращается в determinate progress с текущим этапом;
- если агент работает, использовать одну спокойную animated status-line, а не пульсацию всей карточки.

#### Состояния карточки

| Состояние | Вид |
| --- | --- |
| Ready | обычная tonal card |
| Active | accent status-line и текст текущего действия |
| Dirty | branch + число изменений, без error-цвета |
| Cloning/Importing | progress, этап и Cancel |
| Runtime offline | нейтральный offline icon, проект остаётся доступен для просмотра |
| Missing | warning container и действие `Найти папку` |
| Error | inline-причина и `Повторить` |
| Archived | только в отдельном фильтре, приглушённый вид |

#### Жесты

- tap — открыть Project Detail;
- long press — selection mode;
- swipe — архивировать с Undo, но не удалять файлы;
- overflow: переименовать, сменить runtime, закрепить, архивировать, удалить;
- `Удалить` открывает preview с отдельными флажками `Удалить запись проекта` и `Удалить локальные файлы`;
- проект с активной задачей нельзя удалить без предварительной остановки.

### 6.4 Empty state

Если проектов нет:

- крупная Material Shape с Pocket Prompt/folder glyph;
- заголовок `Добавьте первый проект`;
- пояснение: `Клонируйте Git-репозиторий, создайте пустой workspace или импортируйте копию папки`;
- primary action `Добавить проект`;
- ниже две компактные text actions: `Как это работает` и `Открыть демо-проект`, только если демо реально поставляется.

Не показывать сразу три одинаково тяжёлые карточки выбора: первый tap открывает единый Add Project sheet.

### 6.5 FAB «Добавить проект»

На compact screen — обычный FAB с `+`; после первого scroll или на пустом экране он может быть extended: `Добавить`. На tablet — extended FAB постоянно.

FAB раскрывает вертикальное expressive menu:

1. **Клонировать Git-репозиторий** — основной сценарий;
2. **Создать пустой проект**;
3. **Импортировать копию папки**.

Пункты имеют иконку, название и однострочное объяснение. Menu закрывается predictive back, tap по scrim и повторным tap по FAB.

### 6.6 Flow «Клонировать репозиторий»

На телефоне использовать отдельный full-screen flow, а не маленький dialog. На tablet допустим широкий modal sheet.

#### Шаг 1. Репозиторий

- поле `HTTPS URL` с paste-action;
- автоматический preview: host, owner, repository;
- имя проекта заполняется из URL, но редактируется;
- destination показывается как read-only preview;
- inline validation вместо ошибки после нажатия кнопки;
- primary action `Продолжить`.

SSH можно показывать disabled-row `Появится позже`, но не принимать URL, который Stage 2 не умеет корректно клонировать.

#### Шаг 2. Доступ

Если репозиторий публичный, этот шаг автоматически пропускается после проверки. Для приватного:

- выбор `Без авторизации` / `Personal access token`;
- secure token field с show/hide;
- checkbox `Сохранить для github.com` выключен по умолчанию;
- пояснение, что token не будет записан в remote URL и Git output;
- кнопка `Проверить доступ` показывает loading → success/error внутри формы.

Не называть это «паролем GitHub»: основной сценарий — token.

#### Шаг 3. Параметры

Основные:

- имя проекта;
- runtime/profile;
- branch: `Default`;
- shallow clone: включён по умолчанию.

Под toggle `Дополнительно`:

- depth;
- конкретная branch/tag;
- recursive submodules;
- Git LFS, только если реально поддержан окружением.

#### Шаг 4. Progress

Одна крупная progress-card с этапами:

```
Проверка URL
→ Подключение
→ Получение объектов
→ Распаковка
→ Проверка Git
→ Готово
```

- determinate progress использовать только при наличии реального процента;
- иначе показывать текущий этап и объём полученных данных;
- технический stdout находится под `Показать журнал`;
- `Отменить` сначала завершает git-процесс, затем удаляет incomplete workspace;
- при ошибке сохранять введённые поля и предлагать `Повторить`, `Изменить данные`, `Экспортировать журнал`;
- после успеха primary action `Открыть проект`, secondary `Начать чат`.

### 6.7 Flow «Создать пустой проект»

Один экран:

- название;
- slug/path preview;
- runtime/profile;
- switch `Инициализировать Git` — включён;
- initial branch: `main`;
- optional `Создать README.md`;
- шаблоны не добавлять в MVP, если для них нет реального генератора.

Кнопка `Создать проект` блокируется при пустом имени, конфликте пути или недопустимых символах. После создания открыть Project Detail и один раз подсветить действие `Новый чат`.

### 6.8 Flow «Импортировать папку»

Android picker выбирает папку через SAF, после чего PocketCLI показывает экран подтверждения:

- имя исходной папки;
- примерный размер и количество файлов, если их можно быстро получить;
- destination в app-private workspace;
- предупреждения для очень большого проекта, symlinks или недостатка места;
- primary action `Импортировать копию`.

В UI всегда писать `Импортировать копию`, а не `Открыть на месте`: runtime, Git и proot работают с app-private копией. Во время импорта показывать текущий путь и progress. Ошибка одного файла не должна оставлять проект в статусе Ready.

### 6.9 Project Detail

Переход из карточки — shared bounds/container transform. На compact screen открывается отдельный экран; на expanded — detail справа от списка.

#### Header

- Back либо выбранный item в list-detail;
- shape-аватар;
- название;
- сокращённый путь;
- branch и dirty state;
- runtime status-pill;
- overflow: переименовать, сменить runtime, настройки проекта, архивировать, удалить.

Под header — primary action `Новый чат` и connected `ButtonGroup`:

- `Обзор`;
- `Файлы`;
- `Git`;
- `Terminal`.

На телефоне группа прокручивается горизонтально с корректным edge fade; не прятать основные четыре пункта в overflow.

#### Вкладка «Обзор»

Порядок секций:

1. **Активные сессии** — только если есть;
2. **Продолжить работу** — последние 3 сессии;
3. **Git** — branch, clean/dirty, ahead/behind и summary изменений;
4. **Быстрые команды** — `test`, `build`, `lint`, только если обнаружены или настроены;
5. **Последние файлы**;
6. **Workspace** — размер, runtime, local path, remote URL.

Каждая секция имеет максимум одно primary действие. Не выводить графики CPU/RAM здесь — они принадлежат Runtime Center.

#### Вкладка «Файлы»

Использовать file browser из раздела 11, уже scoped текущим `workspaceId`. Top app bar не дублировать: tab content получает собственную search/action row.

#### Вкладка «Git»

Для Stage 2 достаточно:

- branch;
- clean/dirty;
- список изменённых файлов;
- pull/fetch status, если доступен;
- открыть diff;
- advanced commit/stage actions вводить только после готовности раздела 10.

#### Вкладка «Terminal»

Терминал сразу открывается в cwd проекта. Перед запуском показать runtime state; если runtime остановлен, действие `Запустить и открыть Terminal` выполняется как единый flow.

### 6.10 Project picker при создании сессии

Bottom sheet содержит:

- search field;
- блок `Недавние` — до 6 проектов;
- статус runtime у каждой строки;
- текущий выбранный проект отмечен check;
- действия `Все проекты` и `Добавить проект`;
- long press и destructive actions здесь отсутствуют.

После выбора sheet закрывается, header новой сессии обновляется, но чат создаётся только после первого сообщения либо явного `Создать`. Это не плодит пустые сессии при случайном просмотре проектов.

### 6.11 Адаптивная компоновка

- **Compact:** список → отдельный detail screen; Add Project — full-screen flow.
- **Medium:** navigation rail; список проектов шириной 320–360dp и detail рядом.
- **Expanded:** permanent rail + project list + detail; Files/Git могут открыть третью pane.
- При ширине detail меньше комфортной не показывать одновременно overview и file viewer.
- При аппаратной клавиатуре: `Ctrl+P` — project picker, `Ctrl+Shift+N` — новый проект, `/` — поиск.

### 6.12 Motion

- ProjectCard → Project Detail: shared bounds 400–520 мс;
- FAB → Add Project menu: stagger 25–40 мс между пунктами;
- clone progress: `AnimatedContent` между этапами, без фальшивого процента;
- Ready: progress container мягко morph-ится в success-card, затем в header проекта;
- dirty state меняется только icon/color crossfade, без shake;
- missing/error дают один короткий accent pulse, затем остаются статичными;
- при Reduce Motion все переходы заменяются fade-through.

### 6.13 Accessibility и состояния

- TalkBack читает карточку одной фразой: `Project PocketCLI, ветка main, 7 изменений, локальный runtime готов, последний раз открыт 2 часа назад`;
- path не должен читаться посимвольно до отдельного действия `Прочитать путь`;
- progress сообщает этапы, но не повторяет каждое изменение процента;
- touch targets минимум 48dp;
- при font scale 200% metadata переносится на новые строки, а не обрезается;
- цвет не является единственным признаком dirty/offline/error.

Обязательные preview/test states:

- empty;
- 1, 10 и 100 проектов;
- long repository name;
- local/remote/offline runtime;
- clean/dirty;
- cloning/importing/cancelled;
- auth required;
- disk full;
- duplicate name/path;
- missing workspace;
- archived;
- active session;
- RU/EN, light/dark/dynamic color, font scale 200%.

### 6.14 Компоненты для реализации

```
ProjectsRoute
ProjectsScreen
ProjectsTopBar
ProjectFilters
ProjectCard
ProjectStatusLine
ProjectsEmptyState
AddProjectFabMenu
CloneRepositoryRoute
CloneProgressCard
CreateProjectRoute
ImportWorkspaceRoute
ProjectDetailRoute
ProjectHeader
ProjectSection
ProjectPickerSheet
```

Перед реализацией агент должен сделать Compose preview matrix для списка, трёх add-flows и Project Detail. Не считать экран готовым, если реализован только selector папки в dialog: обязательны отдельный раздел «Проекты», стабильный `workspaceId`, Project Detail и выбор проекта при создании сессии.

## 7. Экран чата — главная поверхность

### Header

- Back;
- имя сессии;
- подзаголовок `Проект · runtime`;
- живая status-пилюля: `Готов`, `Думает`, `Выполняет`, `Ждёт разрешения`, `Офлайн`;
- кнопка файлов;
- overflow: переименовать, модель, экспорт, новая ветка сессии, удалить.

### Лента

Не использовать классические разноцветные «пузырьки мессенджера» для всего.

- **Сообщение пользователя:** tonal container, выравнивание вправо, ширина до 88%.
- **Ответ агента:** преимущественно свободный текст на background, чтобы длинный Markdown читался как документ.
- **Reasoning:** компактная строка `Думал 18 с` с раскрытием; по умолчанию закрыто.
- **Tool call:** одна строка summary + иконка + статус. Например `Terminal · ./gradlew test · успешно, 24 с`.
- **Permission:** заметный tertiary/error container с понятным последствием, а не сырой командой.
- **Diff:** summary-карточка `3 файла · +48 −12`; полный diff — отдельная поверхность.
- **Ошибки:** inline-карточка рядом с действием, которое упало; глобальный snackbar только для UI-событий.

### Streaming

- Новые токены обновляются с throttle; никакой анимации каждого символа.
- Внизу ответа — маленький expressive loading indicator.
- Незакрытый code block рисуется нейтрально без тяжёлой подсветки.
- Автоскролл только пока пользователь находится возле конца.
- Если пользователь ушёл вверх, появляется floating pill `↓ К новому · 3`.

### Composer

Состояния:

1. **Collapsed:** однострочная округлая поверхность.
2. **Expanded:** до 5 строк, затем внутренний scroll.
3. **Attachment preview:** чипы файлов/изображений над текстом.
4. **Running:** Send morph-ится в Stop.
5. **Offline:** текст сохраняется как draft, вместо Send — `В очередь`.

Элементы:

- `+` открывает FAB menu: файл, изображение, камера, вставить путь, контекст проекта;
- поле ввода;
- компактный model chip;
- split action: основная часть `Отправить`, дополнительная — `Отправить позже`, `Новый контекст`, другие подтверждённые режимы;
- voice input — отдельный icon button только если функция включена.

Клавиатура не должна прыгать при появлении Stop или attachment row. Высота composer меняется пружинно, но baseline текста остаётся стабильным.

## 8. Permission flow

Permission — не обычный AlertDialog, потому что пользователю нужен контекст.

### Inline preview

- что агент собирается сделать;
- цель человеческим языком;
- scope: файл, директория, host;
- риск: обычный / внешний доступ / destructive;
- кнопки `Отклонить` и `Разрешить`.

### Details bottom sheet

- точная команда или payload;
- рабочая директория;
- инициировавший tool call;
- варианты разрешения: `Один раз`, `Для этой сессии`, если агент/API это поддерживает;
- destructive action выделяется цветом только на кнопке и ключевом предупреждении.

При свёрнутом приложении действие приходит notification action. После ответа карточка в чате меняет статус на approved/rejected без дублирования.

## 9. Tool output и терминал

### Tool output bottom sheet

- заголовок команды;
- exit code, длительность и объём;
- поиск;
- wrap on/off;
- копировать;
- сохранить в файл;
- моноширинный `LazyColumn` с виртуализацией;
- sticky-пилюля `К концу` при просмотре live output.

### Полноэкранный terminal

- terminal — дополнительный инструмент, не главный UI;
- верхняя панель: session, cwd, connection status;
- floating toolbar: Ctrl, Esc, Tab, стрелки, paste;
- защита от случайного закрытия активного процесса;
- для TalkBack доступен упрощённый текстовый журнал вместо canvas-only интерфейса.

## 10. Diff и Git

### Diff overview

- список файлов, отсортированный по значимости: конфликтные → изменённые → generated;
- фильтры в ButtonGroup: `Все`, `Изменены`, `Добавлены`, `Удалены`;
- summary по строкам и статус CI/test, если доступен.

### File diff

- phone: unified diff;
- tablet: split diff при достаточной ширине;
- sticky header с путём файла;
- collapse неизменённого контекста;
- переход к следующему hunk через floating toolbar;
- inline comment/`Спросить агента` на выделенном диапазоне;
- syntax highlighting только для видимого диапазона;
- большие diff открываются порциями.

### Git actions

Split button:

- primary: `Создать commit`;
- menu: stage all, выбрать файлы, discard, создать branch.

Destructive операции требуют preview затрагиваемых файлов, но не многоступенчатого wizard.

## 11. Файлы

### File browser

- breadcrumbs в horizontal scroll;
- поиск по имени;
- сортировка и hidden files в overflow;
- папки сначала, затем файлы;
- статус Git на trailing edge;
- long press включает multi-select toolbar.

### Viewer/editor

- режимы `Preview / Source` через connected ButtonGroup;
- outline side sheet для Markdown/кода на планшете;
- поиск по файлу;
- копирование пути;
- `Добавить в контекст` как основное действие;
- полноценный редактор не делать центром MVP: сначала просмотр и небольшие правки.

## 12. Runtime Center

Открывается из status-пилюли или настроек.

### Overview

Крупная shape-карточка:

- `Локальный runtime работает`;
- uptime;
- версия агента;
- память, температура и батарея;
- активные процессы;
- `Остановить`.

Ниже:

- компоненты runtime и версии;
- storage usage;
- проверки окружения;
- последние ошибки;
- обновление runtime;
- `Экспортировать диагностику`.

Графики не нужны в MVP: текущие значения и короткие trends достаточны. Предупреждение о температуре появляется только при реальной проблеме.

### Remote profile detail

- URL, transport, auth;
- latency и health;
- Test connection;
- cleartext warning;
- QR export без показа пароля по умолчанию;
- удалить профиль.

## 13. Агенты, провайдеры и модели

### Agents

Карточки OpenCode, Claude Code, Gemini/Antigravity и Codex:

- установлен/доступен;
- local/remote capabilities;
- версия;
- auth status;
- `Настроить`.

### Provider setup

- provider picker с поиском;
- API key вводится на отдельном secure screen;
- ключ никогда повторно не показывается;
- Test credentials;
- base URL и advanced fields скрыты под toggle;
- OAuth/device-code flow показывает URL, код, timeout и состояние ожидания.

### Model picker

- bottom sheet с search;
- favorites наверху;
- группировка по provider;
- capability chips: vision, tools, context;
- цена/контекст только если данные достоверны и обновляемы;
- long press — сделать моделью по умолчанию для проекта.

## 14. Activity и уведомления

Не обязательная отдельная вкладка. Открывается по иконке в Home.

Группы:

- требуется действие;
- завершено;
- ошибки;
- системные события runtime.

Каждый event ведёт в конкретное место сессии. `Mark all read` находится в overflow. Повторяющиеся runtime-события агрегируются, чтобы не создавать шум.

## 15. Настройки

### Главный экран

1. **Подключения и runtime**
    - Local runtime;
    - Remote profiles;
    - default runtime;
    - background behavior.
2. **Агенты и модели**
    - установленные агенты;
    - providers;
    - модели по умолчанию;
    - MCP servers позже.
3. **Чат и редактор**
    - Markdown;
    - code font size;
    - line wrap;
    - reasoning visibility;
    - smart autoscroll;
    - draft behavior.
4. **Уведомления**
    - approvals;
    - completion;
    - runtime errors;
    - sound/vibration;
    - quiet hours.
5. **Внешний вид**
    - System/Light/Dark/AMOLED;
    - Dynamic color;
    - preset palette fallback;
    - contrast;
    - motion: full/reduced/system;
    - app icon.
6. **Безопасность и данные**
    - biometric lock;
    - скрывать содержимое recent-apps;
    - credential storage;
    - cleartext policy;
    - history retention;
    - database export/import;
    - очистить cache/runtime.
7. **Git**
    - имя/email;
    - SSH keys;
    - default branch behavior;
    - safe directory list.
8. **Доступность**
    - увеличенный touch target;
    - повышенный contrast;
    - отключение syntax colors;
    - TTS;
    - haptics.
9. **Диагностика и About**
    - версии приложения/runtime/агентов;
    - device info;
    - export logs с автоматической редакцией секретов;
    - licenses;
    - privacy;
    - GitHub/issues.
10. **Обновления приложения**
- текущая версия и канал обновлений;
- автоматическая фоновая проверка;
- обновление через GitHub Releases;
- скачивание только по Wi‑Fi;
- пропущенная версия;
- история изменений;
- ручная кнопка `Проверить обновления`.

Поиск по настройкам обязателен после появления более 25 пунктов.

## 16. Material 3 Expressive: где применять

| Компонент | Применение в PocketCLI |
| --- | --- |
| TwoRowsTopAppBar | Чаты, Проекты, Настройки |
| AppBarWithSearch | Поиск сессий, файлов и настроек |
| ButtonGroup | Project detail, фильтры diff, Preview/Source |
| SplitButton | Send options, commit actions, runtime start options |
| FloatingToolbar | Diff navigation, terminal modifiers, contextual selection |
| FAB/FAB Menu | Новый чат и вложения |
| LoadingIndicator | Подключение, запуск runtime, streaming tail |
| Expressive menus | Model picker и action overflow |
| Morphing icon buttons | Send → Stop, expand/collapse, connect state |
| MaterialShapes | Empty states, runtime hero, avatar агента |
| Carousel | Только recent projects/onboarding examples, не для основных списков |

В Compose Material 3 1.5 часть Expressive API уже продвигается из experimental: ButtonGroup стал stable, SplitButton и FloatingToolbar переведены в non-experimental, а LoadingIndicator и MaterialShapes всё ещё требуют осторожности.[[8]](https://developer.android.com/jetpack/androidx/releases/compose-material3)

### Правило зависимости

Каждый новый Expressive-компонент оборачивается в собственный `Pocket*` composable. Если alpha API изменится, приложение меняет одну обёртку, а не десятки экранов.

## 17. Визуальные токены

### Цвет

- Dynamic color включён по умолчанию на Android 12+.
- Brand fallback: холодный violet/indigo seed, чтобы интерфейс не выглядел копией терминала.
- `surfaceContainerLow` — фон списков;
- `surfaceContainer` — user messages и обычные cards;
- `surfaceContainerHigh` — sheets и выделенные области;
- `tertiaryContainer` — approvals;
- `errorContainer` — только реальные ошибки/destructive state;
- success — не вводить собственный кислотно-зелёный: использовать поддерживаемый semantic token с проверенным contrast.

### Shape

- Composer и ключевые action containers: очень округлые.
- Обычные cards: medium/large radius.

![image.png](image.png)

- Code, logs и diff: меньший radius, чтобы технические блоки ощущались точными.
- Не делать каждую строку отдельной pill.

### Иконка приложения: Pocket Prompt

Рекомендуемый знак — **открытый карман с терминальным prompt внутри**. Он одновременно передаёт название PocketCLI и назначение приложения, оставаясь читаемым одной линией.

<aside>
✏️

Рисуй сначала только чёрным по белому. Если знак не узнаётся без цвета, градиента и launcher-маски — геометрия ещё не готова. Главный образ: не «сумка с ручками», а открытый сверху карман, внутри которого виден короткий терминальный `>_`.

</aside>

#### Как нарисовать master-знак вручную

Удобнее всего делать в Figma, Penpot, Illustrator или Inkscape. Ниже размеры указаны для артборда `24 × 24`; затем вектор можно масштабировать без изменений.

#### Шаг 1. Подготовить сетку

1. Создать квадратный frame `24 × 24`.
2. Включить pixel grid и обычную сетку с шагом `1`.
3. Провести направляющие по `x = 5`, `x = 12`, `x = 19`, `y = 8`, `y = 16` и `y = 20`.
4. Все элементы пока рисовать без заливки, чёрным stroke `2.25`, с `Round cap` и `Round join`.
5. Не переводить stroke в outline, пока не закончена оптическая правка.

#### Шаг 2. Нарисовать карман

Карман — одна незамкнутая линия, начинающаяся сверху слева и заканчивающаяся сверху справа.

1. Поставить первую точку в `(5, 8)`.
2. Провести вертикаль до `(5, 16)`.
3. Из неё сделать плавный нижний левый угол к `(9, 20)`. Это не четверть круга: вертикаль должна переходить в кривую мягко, а нижняя часть — стать горизонтальной примерно к `x = 9`.
4. Провести ровное дно от `(9, 20)` до `(15, 20)`.
5. Симметрично поднять правый угол к `(19, 16)`.
6. Провести правую вертикаль до `(19, 8)`.
7. Верх не соединять. Получится широкая буква `U` с коротким плоским дном и открытым верхом.

Если инструмент поддерживает SVG path, вставить основу:

```
<path d="M5 8V16C5 18.2 6.8 20 9 20H15C17.2 20 19 18.2 19 16V8"
      fill="none" stroke="currentColor" stroke-width="2.25"
      stroke-linecap="round" stroke-linejoin="round"/>
```

Что проверить глазами:

- левая и правая стенки одинаковой высоты;
- дно визуально находится по центру;
- нижние углы не похожи ни на прямоугольник, ни на идеальную полукруглую чашу;
- расстояние между стенками достаточно большое, чтобы prompt не прилипал к ним;
- открытый верх сразу отличает знак от обычного terminal window.

#### Шаг 3. Нарисовать знак prompt

Prompt состоит из двух диагональных отрезков и не является текстовым символом шрифта.

1. Поставить вершину в `(11.5, 13)`.
2. Верхний хвост поставить в `(8.5, 10.5)`.
3. Нижний хвост поставить в `(8.5, 15.5)`.
4. Соединить их одной ломаной `(8.5,10.5) → (11.5,13) → (8.5,15.5)`.
5. Использовать тот же stroke `2.25`, Round cap и Round join.

```
<path d="M8.5 10.5L11.5 13L8.5 15.5"
      fill="none" stroke="currentColor" stroke-width="2.25"
      stroke-linecap="round" stroke-linejoin="round"/>
```

Prompt специально немного смещён влево: вместе с cursor он образует единую композицию, центр которой находится почти на оси `x = 12`.

#### Шаг 4. Нарисовать cursor

1. Начало — `(13, 15.5)`.
2. Конец — `(16, 15.5)`.
3. Stroke и окончания те же, что у остальных линий.
4. Cursor должен быть коротким штрихом, а не длинным подчёркиванием.

```
<path d="M13 15.5H16"
      fill="none" stroke="currentColor" stroke-width="2.25"
      stroke-linecap="round"/>
```

#### Шаг 5. Собрать и оптически выровнять

- Сгруппировать pocket, prompt и cursor, но оставить их отдельными paths.
- Геометрический bounding box знака: примерно `x = 3.875…20.125`, `y = 6.875…21.125` с учётом stroke.
- Не центрировать каждый внутренний элемент отдельно. Центрировать всю пару `>_` внутри кармана.
- Если знак кажется тяжёлым снизу, поднять всю группу `>_` на `0.25–0.5` единицы, а не уменьшать stroke.
- Если cursor визуально перевешивает композицию вправо, укоротить его конец с `x = 16` до `15.5`.
- Если карман похож на магнит, немного укоротить верхние вертикали: начать их с `y = 8.5`.
- Если карман похож на букву U, слегка расширить нижнее плоское дно до диапазона `x = 8.5…15.5`.
- Допустимое отклонение от исходных координат — до `0.5` единицы; дальше образ начнёт распадаться.

#### Шаг 6. Проверить силуэт в маленьком размере

Сделать четыре копии: `16 × 16`, `24 × 24`, `32 × 32` и `48 × 48` px.

- На `16 px` сначала проверить вариант без cursor: pocket + `>`.
- На `24 px` три части должны уже читаться отдельно.
- На `32–48 px` не должно появляться ощущения слишком тонких пустот.
- Смотреть не только при 800% zoom, но и на реальном масштабе 100%.
- Сделать blur-test: слегка расфокусировать взгляд или применить Gaussian blur `1 px`. Силуэт всё ещё должен выглядеть как один компактный знак.

#### Шаг 7. Подготовить master SVG

Финальный исходник хранить как `design/app-icon/pocket-prompt-master.svg` с `viewBox="0 0 24 24"`. Рекомендуемая структура:

```
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
  <g fill="none" stroke="currentColor" stroke-width="2.25"
     stroke-linecap="round" stroke-linejoin="round">
    <path id="pocket" d="M5 8V16C5 18.2 6.8 20 9 20H15C17.2 20 19 18.2 19 16V8"/>
    <path id="prompt" d="M8.5 10.5L11.5 13L8.5 15.5"/>
    <path id="cursor" d="M13 15.5H16"/>
  </g>
</svg>
```

Не использовать clipping mask, bitmap, blur, shadow и embedded font. После утверждения сделать отдельную копию, перевести stroke в outlines и выполнить `Union/Combine` только для экспорта в Android VectorDrawable. Исходный stroke-master сохранить редактируемым.

#### Шаг 8. Перенести знак в adaptive icon 108 × 108

1. Создать новый frame `108 × 108`.
2. Поместить внутрь квадрат safe-zone `66 × 66` по центру: от `(21,21)` до `(87,87)`.
3. Масштабировать master-знак примерно до `58 × 58` и поставить по центру safe-zone.
4. Начальная позиция foreground: около `x = 25`, `y = 24`; затем поднять на 1–2 единицы, если знак кажется низким.
5. За пределы safe-zone может выходить только декоративный запас линии, но не prompt и не cursor.
6. Не добавлять собственный круг или squircle: launcher сам применит маску.

Рекомендуемые слои:

- `Foreground` — только светлый или тёмный линейный Pocket Prompt;
- `Background` — сплошной цвет либо очень мягкий radial gradient без мелкой фактуры;
- `Monochrome` — цельный белый/чёрный силуэт того же знака, без background.

#### Шаг 9. Выбрать базовые цвета

Сначала подготовить три тестовых цветовых пары:

| Вариант | Background | Foreground | Назначение |
| --- | --- | --- | --- |
| Indigo | `#4F378B` | `#F7F2FF` | основной кандидат |
| Violet dark | `#21182F` | `#DCC2FF` | dark-first вариант |
| Light tonal | `#E8DEF8` | `#352A4A` | светлая витрина/сайт |

Финальный цвет выбирать только после сравнения на настоящем launcher рядом с Termux, GitHub, Android Studio и системными приложениями. Не использовать кислотно-зелёный «terminal color»: он слишком ожидаемый и хуже поддерживает спокойный M3-образ.

#### Шаг 10. Сделать monochrome/themed версию

- Скопировать только геометрию foreground.
- Удалить все цвета, gradient и прозрачность.
- Перевести stroke в замкнутую залитую форму, чтобы системная тонировка не дала артефактов на стыках.
- Проверить, что после заливки внутренние просветы между `>`, cursor и карманом не слиплись.
- Если на маленьком preview cursor пропадает, использовать simplified monochrome: pocket + `>`; цветная версия может сохранить полный `>_`.

#### Шаг 11. Подготовить экспорт

Нужно отдать агенту не один PNG, а набор исходников:

```
design/app-icon/pocket-prompt-master.svg
design/app-icon/pocket-prompt-outlined.svg
design/app-icon/pocket-prompt-monochrome.svg
design/app-icon/adaptive-icon-108.svg
design/app-icon/previews/launcher-masks.png
design/app-icon/previews/monet-palettes.png
```

Экспортировать PNG/WebP для legacy launcher из adaptive preview, а не из маленького `24 × 24` master. Перед передачей агенту зафиксировать, какой вариант является финальным: `A — полный >_` или `B — упрощённый >`.

Состав знака:

- внешний контур — симметричная U-образная линия с открытым верхом, похожая на карман;
- внутри — маленький prompt `>`;
- справа от prompt — короткий горизонтальный cursor `_`;
- никаких букв `P`, роботов, мозга, звёзд AI и мелких декоративных деталей;
- все окончания и соединения округлённые.

Пример конструкции на сетке `24 × 24`:

```
Pocket:
M5 8 V16 C5 18.2 6.8 20 9 20 H15 C17.2 20 19 18.2 19 16 V8

Prompt:
M8.5 10.5 L11.5 13 L8.5 15.5

Cursor:
M13 15.5 H16
```

Параметры рисунка:

- stroke: `2.0–2.25`;
- `StrokeCap.Round`;
- `StrokeJoin.Round`;
- одинаковая визуальная толщина pocket, prompt и cursor;
- оптический центр prompt на 0.5–1 единицу выше геометрического центра кармана;
- минимальный зазор между prompt/cursor и контуром — не меньше двойной толщины линии;
- сначала проверить читаемость в `16`, `24`, `32` и `48 dp`.

#### Варианты

1. **Основной:** pocket + `>_` — рекомендуемый.
2. **Упрощённый:** pocket + один `>` — для совсем маленьких размеров.
3. **Brand animation:** линия кармана рисуется снизу вверх, затем появляется `>`, cursor мигает один раз. Полный цикл 550–750 мс.

#### Цветная adaptive icon

- background layer: однотонный `primaryContainer` или спокойный двухцветный radial gradient `primaryContainer → tertiaryContainer`;
- foreground layer: светлый/тёмный линейный знак с высоким contrast;
- знак занимает примерно 56–60 единиц внутри контейнера `108 × 108`;
- весь смысловой рисунок должен оставаться внутри центральной safe zone `66 × 66`, которую OEM-маска не обрезает.[[1]](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- не рисовать собственный круг/squircle вокруг знака: форму launcher применит сам.

#### Monochrome/Themed icon для Monet

Обязательно подготовить отдельный `monochrome` vector drawable из того же pocket + `>_`, без градиентов, теней и opacity tricks. Android использует этот слой для системной тонировки themed icons в цветах обоев пользователя.[[2]](https://developer.android.com/distribute/aep/aep-req-theme-app-icons)

Набор ресурсов:

```
mipmap-anydpi-v26/ic_launcher.xml
mipmap-anydpi-v26/ic_launcher_round.xml
drawable/ic_launcher_foreground.xml
drawable/ic_launcher_background.xml
drawable/ic_launcher_monochrome.xml
mipmap-*/ic_launcher.webp          # legacy fallback
mipmap-*/ic_launcher_round.webp    # legacy round fallback
```

Adaptive icon XML должен содержать три слоя:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

Оба adaptive-слоя проектировать в контейнере `108 × 108`; foreground и background маскируются формой launcher, а внешний inset используется системой для эффектов вроде parallax.[[3]](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable)

#### Обязательная проверка

- circle, squircle, rounded square, teardrop и OEM irregular masks;
- light/dark launcher;
- минимум 4 разные Monet palette;
- themed icons включены/выключены;
- normal, round и legacy fallback;
- иконка рядом с GitHub, Termux, Android Studio и другими developer apps — не должна теряться;
- monochrome preview должен читаться без background brand color.

Для агента: создать `docs/APP_ICON_SPEC.md`, положить туда SVG/source path, размеры, цвета, safe-zone overlay и скриншоты всех mask/Monet previews. Не генерировать финальную иконку случайно из текста: владелец вручную рисует master SVG по этой геометрии, агент только переводит его в Android resources.

### Typography

- Display/Headline — только onboarding, empty states и крупные section headers.
- Title — экран и карточки.
- Body — чат.
- Label — статусы и metadata.
- Код — системный monospace или JetBrains Mono при допустимой лицензии и размере APK.
- Минимум 14sp для code viewer; пользователь может увеличить до 20sp.

### Spacing

Сетка 4dp; основные интервалы 8/12/16/24/32. Touch target минимум 48dp. Composer и bottom navigation учитывают gesture inset.

## 18. Motion и haptics

- Общая motion-тема: spring с умеренным bounce для смены формы; fade/scale для появления вторичного контента.
- Shared-axis переход: список сессий → чат.
- Container transform: project card → project detail.
- Send → Stop: morph без изменения позиции.
- Tool success/error: короткая смена иконки и цвета, без конфетти.
- Haptic:
    - лёгкий — send и раскрытие toolbar;
    - средний — approval;
    - сильный — destructive confirmation/error.
- При `Reduce motion` morph заменяется на crossfade, а бесконечные decorative loops отключаются.

## 19. Состояния, которые должны быть спроектированы заранее

Для каждого экрана обязательны:

- loading;
- empty;
- offline;
- stale data;
- partial failure;
- permission denied;
- authentication expired;
- reconnecting;
- content too large;
- storage full;
- runtime killed by system;
- unsupported capability.

Нельзя оставлять эти случаи на стандартный `Snackbar("Something went wrong")`.

## 20. Accessibility

- Все icon-only actions имеют content description и tooltip.
- Цвет никогда не единственный носитель статуса: иконка + текст.
- TalkBack читает streaming update не по токенам, а завершёнными смысловыми блоками.
- Code line numbers исключаются из основного reading order.
- Tool cards сообщают имя, состояние и длительность одной фразой.
- При включённом touch exploration floating toolbar остаётся раскрытым; подобное поведение предусмотрено и в Material 3 API.[[8]](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- Проверка font scale 200%, landscape и smallest supported width входит в Definition of Done.

## 21. Порядок реализации

### UI foundation

1. `PocketTheme`: color, type, shape, motion, elevations.
2. `PocketScaffold`: adaptive navigation и insets.
3. Базовые компоненты: status pill, session row, tool card, permission card, runtime card, composer.
4. Preview matrix: light/dark/dynamic, RU/EN, font scale.

### Vertical slice

1. Чаты.
2. Пустой чат.
3. Отправка сообщения.
4. Streaming.
5. Tool call.
6. Permission.
7. Завершение/ошибка.
8. Background → возврат → reconcile.

### Затем

1. Projects + files.
2. Diff.
3. Runtime Center.
4. Providers/models.
5. Settings.
6. Tablet/list-detail.
7. Terminal и advanced Git.

## 22. Definition of Done для каждого экрана

- Есть compact и expanded layout.
- Есть loading/empty/error/offline состояния.
- Back и predictive back работают корректно.
- Нет обрезания при RU-тексте и font scale 200%.
- TalkBack проходит все действия в логичном порядке.
- Touch targets не меньше 48dp.
- Скролл не конфликтует с sheet/navigation gestures.
- Screenshot tests: light, dark, dynamic color.
- Performance: списки не пересобирают тяжёлый Markdown вне viewport.
- Анимации отключаемы и не блокируют взаимодействие.

<aside>
✨

Главная визуальная метафора PocketCLI: активная сессия — это не бесконечный терминальный лог, а спокойный живой документ. Техническая глубина всегда рядом, но открывается только тогда, когда она нужна.

</aside>

## 23. Animation system и каталог ресурсов

<aside>
🎞️

Анимации PocketCLI должны объяснять состояние, сохранять пространственную связь и придавать агенту характер. Они не должны замедлять работу, отвлекать от кода или превращать приложение в набор случайных Lottie-файлов.

</aside>

### 23.1 Технологический приоритет

Использовать технологии в следующем порядке:

1. **Нативные Compose-анимации** — layout, navigation, изменение состояния компонентов, drag и gestures.
2. **Material 3 MotionScheme** — единые spring/easing tokens для всего приложения.
3. **AnimatedVectorDrawable/Canvas/Path morph** — короткие icon transitions.
4. **Lottie Compose** — только иллюстративные one-shot состояния: onboarding, empty, success/error, установка runtime.
5. **AGSL shader** — необязательная «живая» AI-сфера на Android 13+, обязательно со статическим fallback.
6. **Rive** — только если понадобится интерактивный mascot/orb со state machine. Не подключать ради одной loop-анимации.

Material 3 предлагает стандартную и expressive motion-схемы; motion можно переопределять глобально или локально на уровне отдельного composable.[[1]](https://m3.material.io/styles/motion) Практический разбор physics-based motion для Compose находится в официальном материале Material.[[2]](https://m3.material.io/blog/m3-expressive-motion-theming)

### 23.2 Главные исходники Compose motion

#### Официальные материалы

- [Compose animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide) — базовый справочник по `animate*AsState`, `AnimatedVisibility`, `AnimatedContent`, `Animatable` и infinite transitions.
- [Animation composables and modifiers](https://developer.android.com/develop/ui/compose/animation/composables-modifiers) — выбор между high-level composables и modifiers.
- [Official Animation Codelab — Home.kt](https://github.com/googlecodelabs/android-compose-codelabs/blob/main/AnimationCodelab/finished/src/main/java/com/example/android/codelab/animation/ui/home/Home.kt) — готовые паттерны expand/collapse, content size и state transitions.
- [MotionScheme.kt](https://android.googlesource.com/platform/frameworks/support/+/43a3dc60a427d3fffa9cbc3f83e5f9a1c5cec29a/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MotionScheme.kt) — первоисточник набора motion specs Material 3.

#### Compose Animations от skydoves — Apache-2.0

Репозиторий содержит отдельные самодостаточные composable-файлы с вынесенными параметрами duration, stiffness и damping.[[3]](https://github.com/skydoves/compose-animations)

- [AnimationExample1.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample1.kt) — изменение размера. Применить к composer, reasoning и tool-card expansion.
- [AnimationExample2.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample2.kt) — сочетание slide/fade/scale. Применить к inline errors и attachment preview.
- [AnimationExample4.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample4.kt) — spring morph FAB. Применить к `Send → Stop` и FAB menu.
- [AnimationExample8.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample8.kt) — кастомный loading spinner. Использовать только как учебный пример; основной loading должен использовать M3 Expressive `LoadingIndicator`.
- [AnimationExample11.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample11.kt) — Path morph иконки. Применить к `Send/Stop`, `Expand/Collapse`, `Play/Pause runtime`.
- [AnimationExample12.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample12.kt) — `SharedTransitionLayout`. Применить к `SessionRow → Chat`, `ProjectCard → ProjectDetail`, `DiffSummary → FileDiff`.
- [AnimationExample14.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample14.kt) — radial FAB menu. Использовать только как принцип stagger; для вложений PocketCLI лучше компактное вертикальное FAB menu.
- [AnimationExample17.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample17.kt) — мягкое поле волн. Возможный fallback для AI activity background.
- [AnimationExample18.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample18.kt) — metaballs. Возможный прототип AI orb.
- [AnimationExample22.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample22.kt) — интерактивный soap bubble drag. Полезен для изучения touch-driven deformation, но в продукт напрямую не переносить.

### 23.3 Lottie: runtime, примеры и правила

Официальный Android runtime Airbnb Lottie имеет Apache-2.0 и отдельный модуль для Compose.[[4]](https://github.com/airbnb/lottie-android)

Обязательные файлы для агента:

- [BasicUsageExamplesPage.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/examples/BasicUsageExamplesPage.kt) — локальный asset и базовый `LottieAnimation`.
- [AnimatableExamplesPage.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/examples/AnimatableExamplesPage.kt) — управляемый progress, pause/resume и one-shot playback.
- [DynamicPropertiesExamplesPage.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/examples/DynamicPropertiesExamplesPage.kt) — замена цветов под dynamic color PocketCLI.
- [TransitionsExamplesPage.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/examples/TransitionsExamplesPage.kt) — согласование Lottie с Compose transitions.
- [Loader.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/composables/Loader.kt) — loading wrapper и задержка показа.
- [CachingExamplesPage.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/main/java/com/airbnb/lottie/sample/compose/examples/CachingExamplesPage.kt) — caching; важно не парсить JSON заново при каждой recomposition.
- [InfiniteAnimationTest.kt](https://github.com/airbnb/lottie-android/blob/master/sample-compose/src/androidTest/java/com/airbnb/lottie/samples/InfiniteAnimationTest.kt) — тестирование infinite animations.

#### Когда Lottie допустим

- onboarding illustration;
- empty project/session state;
- одноразовый success/error после длительной операции;
- этапы установки runtime;
- offline/reconnect illustration;
- экспорт/импорт и backup.

#### Когда Lottie не использовать

- navigation transitions;
- каждое сообщение в чате;
- постоянно вращающийся индикатор во всех tool cards;
- интерактивные кнопки Send/Stop;
- gesture-driven sheets;
- фон всего chat screen.

Все production-анимации хранить локально в APK. Не загружать JSON/CDN во время работы приложения. Ограничение на один asset: ориентир до 100 КБ, до 60 fps, без bitmap assets, blur и сложных masks, если без них можно обойтись.

### 23.4 dotLottie и state machines

Если понадобится один asset с несколькими состояниями `idle / thinking / success / error`, рассмотреть официальный dotLottie Android runtime вместо четырёх отдельных JSON-файлов:

- [dotlottie-android](https://github.com/LottieFiles/dotlottie-android) — официальный runtime.
- [MainActivity.kt](https://github.com/LottieFiles/dotlottie-android/blob/main/sample-compose/src/main/java/com/lottiefiles/example/MainActivity.kt) — Compose integration.
- [StateMachinesExample.kt](https://github.com/LottieFiles/dotlottie-android/blob/main/sample-compose/src/main/java/com/lottiefiles/example/StateMachinesExample.kt) — переключение состояний и inputs.
- [ThemeDataExample.kt](https://github.com/LottieFiles/dotlottie-android/blob/main/sample-compose/src/main/java/com/lottiefiles/example/ThemeDataExample.kt) — theme data и recoloring.
- [MultiInstanceStressTest.kt](https://github.com/LottieFiles/dotlottie-android/blob/main/sample-compose/src/main/java/com/lottiefiles/example/MultiInstanceStressTest.kt) — оценка нескольких экземпляров.

Не подключать одновременно Airbnb Lottie и dotLottie без конкретной причины: сначала сделать spike и выбрать один runtime.

### 23.5 AI orb и shader-анимации

#### Mirage — AGSL, Apache-2.0

Mirage предоставляет GPU AGSL shaders для Compose; анимированные shaders требуют Android 13/API 33, поэтому fallback обязателен.[[5]](https://github.com/AndroidPoet/mirage)

Полезные реализации:

- [MeshGradient.kt](https://github.com/AndroidPoet/mirage/blob/master/mirage/src/commonMain/kotlin/io/androidpoet/mirage/MeshGradient.kt) — спокойный flowing gradient для состояния `thinking`.
- [Metaballs.kt](https://github.com/AndroidPoet/mirage/blob/master/mirage/src/commonMain/kotlin/io/androidpoet/mirage/Metaballs.kt) — органическая AI-сфера.
- [NeuroNoise.kt](https://github.com/AndroidPoet/mirage/blob/master/mirage/src/commonMain/kotlin/io/androidpoet/mirage/NeuroNoise.kt) — использовать очень осторожно: подходит для onboarding/hero, но слишком шумный для постоянного фона.
- [PulsingBorder.kt](https://github.com/AndroidPoet/mirage/blob/master/mirage/src/commonMain/kotlin/io/androidpoet/mirage/PulsingBorder.kt) — возможная граница активной runtime-card или permission, но только один короткий pulse.
- [StaticMeshGradient.kt](https://github.com/AndroidPoet/mirage/blob/master/mirage/src/commonMain/kotlin/io/androidpoet/mirage/StaticMeshGradient.kt) — дешёвый fallback без per-frame animation.
- Demo-файлы: [MeshGradientEntry.kt](https://github.com/AndroidPoet/mirage/blob/master/demo/src/commonMain/kotlin/io/androidpoet/miragedemo/entries/MeshGradientEntry.kt), [MetaballsEntry.kt](https://github.com/AndroidPoet/mirage/blob/master/demo/src/commonMain/kotlin/io/androidpoet/miragedemo/entries/MetaballsEntry.kt), [PulsingBorderEntry.kt](https://github.com/AndroidPoet/mirage/blob/master/demo/src/commonMain/kotlin/io/androidpoet/miragedemo/entries/PulsingBorderEntry.kt).

#### Предлагаемая state machine AI-индикатора

| Состояние | Визуальное поведение |
| --- | --- |
| Idle | статичная мягкая форма, без infinite loop |
| Connecting | медленное вращение/перетекание, 0.6–0.8× скорости |
| Thinking | две-три массы плавно соединяются, без резких вспышек |
| Tool execution | направленная волна или orbit; рядом всегда текст действия |
| Permission required | один accent pulse, затем статичное состояние |
| Success | короткое сжатие → check, один раз |
| Error | короткое нарушение формы → error icon, без постоянного shaking |
| Offline | десатурация и остановка motion |

AI orb не должен появляться возле каждого ответа. Использовать его в splash/recovery, пустом чате и компактном activity indicator.

### 23.6 Rive — только для интерактивного mascot/orb

Rive Android runtime распространяется по MIT и поддерживает artboards, state machines, inputs, events и data binding.[[6]](https://github.com/rive-app/rive-android)

- [Android runtime docs](https://rive.app/docs/runtimes/android/android) — интеграция.
- [State machines](https://rive.app/docs/runtimes/state-machines) — интерактивные состояния.
- [Data binding](https://rive.app/docs/runtimes/data-binding) — передача состояния приложения в animation.
- [AI Orb Mascot](https://rive.app/community/files/28088-53050-ai-orb-mascot) — визуальный референс, не готовый production asset без отдельной проверки лицензии файла.
- [Breathing Animation](https://rive.app/community/files/5532-10908-breathing-animation) — референс спокойного idle motion.

Решение для MVP: **не подключать Rive**. Вернуться к нему, только если AI orb станет интерактивным элементом бренда и Lottie/Compose окажутся недостаточны. Rive добавляет native runtime и усложняет размер APK/ABI packaging.

### 23.7 Animated icons

- [Animated vector images in Compose](https://developer.android.com/develop/ui/compose/animation/vectors) — официальный подход для AVD.
- [Google Material Symbols](https://github.com/google/material-design-icons) — источник базовых символов; Material Icons library в Compose больше не рекомендуется обновлять, направление развития — Material Symbols.[[7]](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [morphicons playground](https://www.morphicons.com/) и [репозиторий](https://github.com/guillermolg00/morphicons) — хороший математический/UX-референс morphing stroke icons, но готовой Jetpack Compose-обёртки нет. Не добавлять JS/React Native runtime; при необходимости реализовать 3–5 пар иконок нативно через Path interpolation.
- [AnimationExample11.kt](https://github.com/skydoves/compose-animations/blob/main/app/src/main/kotlin/com/skydoves/hotreloadanimations/animations/AnimationExample11.kt) — основной Android-пример для собственной реализации.

Обязательные morph-пары PocketCLI:

- Send → Stop;
- Play runtime → Pause/Stop;
- Expand → Collapse;
- Mic → Send;
- Sync → Check;
- Eye → Eye off для секретов.

Не morph-ить любые случайные иконки: переход должен сохранять смысл и положение действия.

### 23.8 Готовые Lottie-assets для просмотра

Это shortlist для визуального отбора, а не команда автоматически добавить всё в приложение:

- [Simple loading & AI thinking](https://lottiefiles.com/free-animation/simple-loading-ai-thinking-O283a21B9W) — кандидат на activity indicator.
- [AI Sphere](https://lottiefiles.com/free-animation/ai-sphere-3jqqWKfSX9) — референс AI orb.
- [AI Thinking Loader](https://lottiefiles.com/free-animation/ai-thinking-loader-D8jxJfOgTs) — ещё один вариант thinking.
- [Code is Loading](https://lottiefiles.com/free-animation/code-is-loading-SGS8Ucy98s) — маленький coding-themed loader.
- [Terminal coding](https://lottiefiles.com/free-animation/terminal-coding-zAg70hdbA4) — onboarding/empty state.
- [Loading → success/error](https://lottiefiles.com/free-animation/loading-animation-with-success-and-error-K2tYPTbs5Q) — Test Connection и установка runtime.
- [Loading Success Fail](https://lottiefiles.com/free-animation/loading-success-failed-mAwdRlkUWK) — более компактная альтернатива.
- [Cloud sync](https://lottiefiles.com/free-animation/cloud-sync-yFw9vXclGC) — remote reconcile/backup.
- [Upload file](https://lottiefiles.com/free-animation/upload-file-icon-animation-qBUMesDbQN) — attachments/import.
- [Something went wrong](https://lottiefiles.com/free-animation/something-went-wrong-QIbNbBNyQh) — full-screen recoverable error.

Free-анимации LottieFiles обычно доступны по Lottie Simple License для коммерческого использования и модификации без обязательной атрибуции, но запрещена перепродажа/распространение исходного asset как отдельного продукта; лицензию необходимо проверять на странице каждого файла.[[8]](https://help.lottiefiles.com/animation-licensing-basics-)

Для каждого выбранного asset записать в `THIRD_PARTY_NOTICES.md`:

- название;
- автор;
- исходный URL;
- дата скачивания;
- лицензия;
- внесённые изменения;
- локальный путь в проекте.

### 23.9 Собственные Lottie-assets

Для splash/logo и главного AI-indicator лучше сделать собственную анимацию, а не использовать стандартного робота или мозг.

Инструменты:

- [LottieFiles Creator](https://lottiefiles.com/ai) — создание и редактирование;
- [LottieLab](https://lottielab.io/) — timeline-based authoring;
- [kagura-agent/lottie-studio](https://github.com/kagura-agent/lottie-studio) — open-source studio для создания и preview;
- [spemer/lottie-animations-json](https://github.com/spemer/lottie-animations-json) — MIT-набор минимальных JSON/AEP примеров: tab transition, FAB menu, favorite и pagination.[[9]](https://github.com/spemer/lottie-animations-json)

Направление собственного логотипа:

1. Pocket/terminal shape складывается из двух Material shapes.
2. В центре появляется короткий cursor blink.
3. При загрузке контур превращается в мягкую orbit/точки.
4. При готовности возвращается в статичный логотип.
5. Полный цикл максимум 900 мс; infinite loop только если реальная загрузка продолжается.

### 23.10 Карта анимаций по flow

| Момент | Анимация | Технология | Ограничение |
| --- | --- | --- | --- |
| Холодный старт | logo reveal; после 300 мс переход в loading | собственный Lottie или Compose Path | не задерживать первый кадр |
| Welcome | одна hero animation | Lottie/Rive asset | один цикл, затем покой |
| Выбор Remote/Local | selected card morph | Compose + M3 spring | 180–300 мс |
| Test Connection | button → loader → check/error | Compose/Lottie segment | результат остаётся текстом |
| Установка runtime | progress + смена этапов | Compose AnimatedContent | не маскировать реальный прогресс |
| Открытие проекта | shared bounds card → detail | SharedTransitionLayout | fallback fade-through |
| Открытие чата | SessionRow → Chat header | shared bounds/shared axis | не анимировать всю историю |
| Отправка | Send → Stop + лёгкое сжатие composer | Path morph + spring | без layout jump |
| Thinking | компактный living indicator | M3 LoadingIndicator/AGSL | останавливать вне viewport |
| Tool call | status icon pending → running → result | AVD/Path morph | one-shot |
| Permission | один pulse container | Compose color/scale | затем статично |
| Bottom sheet | spring slide + scrim fade | Material sheet | следовать жесту пальца |
| Diff open | summary card → file header | shared bounds | unified diff появляется fade |
| Success | короткий check morph | AVD/Lottie | без конфетти |
| Error | shake 2–4 dp + error morph | Compose | один раз, не loop |
| Offline | saturation/fade → static offline | Compose color transition | не скрывать данные |
| Reconnect | slow rotation, затем check | Compose/Lottie | timeout показывает действие |

### 23.11 Motion tokens PocketCLI

Создать единый объект `PocketMotion`:

- `instant`: 90–120 мс — pressed/hover feedback;
- `quick`: 160–220 мс — icon morph, chips, small visibility;
- `standard`: 260–340 мс — sheets, cards, content swap;
- `emphasized`: 400–520 мс — shared bounds и onboarding;
- `slowAmbient`: 1800–3200 мс — только AI orb/ambient motion;
- `springSpatial`: medium-low stiffness, no/low bounce;
- `springExpressive`: medium stiffness, low/medium bounce;
- `springGesture`: high stiffness, low bounce.

Не хранить случайные `tween(300)` по всему коду. Все экраны получают motion через `PocketMotion` или Material `MotionScheme`.

### 23.12 Performance и QA

- Любая infinite animation должна останавливаться при `Lifecycle` ниже STARTED и вне viewport.
- Не запускать одновременно больше одной крупной ambient-анимации на экране.
- Lottie composition кэшируется; JSON не парсится при каждой recomposition.
- Shader-анимации профилируются на среднем устройстве, а не только на флагмане.
- Проверить 60/90/120 Гц, Battery Saver и системный animator scale 0×.
- `Reduce motion`: shared transitions → fade; orb → static gradient; Lottie → последний информативный кадр; decorative loops отключены.
- Screenshot tests используют детерминированный progress.
- Macrobenchmark: startup, открытие чата, composer expand, большой список сообщений, открытие diff.
- Animation не должна блокировать tap или отложенное navigation action.

### 23.13 Обязательный результат для агента

До внедрения создать:

1. `docs/MOTION_SPEC.md` — таблица flow из этого раздела с durations и fallbacks.
2. `docs/ANIMATION_ASSETS.md` — asset, автор, лицензия, URL, размер, назначение.
3. `PocketMotion.kt` — централизованные specs.
4. `PocketAnimatedIcon.kt` — API для утверждённых morph-пар.
5. `PocketLottie.kt` — единая обёртка с lifecycle, reduced motion, dynamic recoloring и preview.
6. Compose preview/demo screen со всеми animation states.

Не добавлять случайную анимацию непосредственно в feature-модуль до внесения её в `MOTION_SPEC.md`.

## 24. OTA-обновление приложения через GitHub Releases

<aside>
⬆️

Под OTA здесь понимается управляемое обновление APK из GitHub Releases. Это не тихая установка: обычное Android-приложение скачивает подписанный APK, проверяет его и передаёт системному установщику; финальное подтверждение делает пользователь. Без участия пользователя обновляться могут только специальные владельцы устройства/профиля.[[1]](https://developer.android.com/reference/android/content/pm/PackageInstaller)

</aside>

### 24.1 Точка входа и навигация

Экран находится в `Настройки → Обновления приложения`. Дополнительные входы:

- строка `Доступно обновление` в About;
- неблокирующий banner на главном экране после фоновой проверки;
- системное уведомление, если новая версия найдена в фоне;
- действие `Подробнее` ведёт на экран обновления, а не сразу начинает скачивание.

Не показывать полноэкранный dialog при каждом запуске. Для обычного релиза — banner/setting badge; блокирующий экран допустим только для критической несовместимости протокола или опасной уязвимости.

### 24.2 Экран «Обновление приложения»

#### Состояние: проверка

- large top app bar `Обновление приложения`;
- текущая версия: например `1.4.2 (104002)`;
- активный канал: `Stable` или `Beta`;
- expressive loading indicator и текст `Проверяем GitHub Releases…`;
- проверка не должна длиться бесконечно: после timeout показать retry и ссылку на страницу релизов.

#### Состояние: последняя версия установлена

Hero-card:

- иконка Pocket Prompt с коротким morph `sync → check`;
- заголовок `У вас последняя версия`;
- установленная версия и дата последней проверки;
- secondary button `Проверить ещё раз`;
- ниже — настройки автоматической проверки и канала.

#### Состояние: найдена новая версия

Верхняя карточка:

- `PocketCLI 1.5.0`;
- номер сборки `105000`;
- размер APK;
- дата публикации;
- label `Stable`, `Beta` или `Pre-release`;
- short release summary: максимум 3–5 пунктов;
- expandable section `Полный changelog`;
- primary button `Скачать обновление`;
- secondary actions `Позже` и `Пропустить 1.5.0`.

Если релиз помечен critical, вместо `Пропустить` показывать спокойное предупреждение, почему обновление желательно. Не блокировать доступ без технической необходимости.

#### Состояние: скачивание

- determinate progress bar;
- скачано/всего, скорость и примерное оставшееся время;
- кнопка `Отменить`;
- уход с экрана не отменяет загрузку;
- progress синхронизирован с foreground notification;
- при потере сети загрузка переходит в `Приостановлено` и может продолжиться с Range request, если GitHub/CDN это поддерживает;
- при включённом `Только по Wi‑Fi` мобильная сеть не запускает загрузку без отдельного согласия.

#### Состояние: проверка файла

После скачивания показать один контейнер этапов:

`APK скачан → SHA-256 совпал → подпись совпала → готово к установке`

Пользователь не должен видеть сырой hash по умолчанию; он доступен в `Технические детали`. При несовпадении checksum или сертификата файл немедленно удалить и показать красную карточку `Проверка безопасности не пройдена` без кнопки обхода.

#### Состояние: установка

- primary action `Установить` запускает системный Package Installer;
- перед передачей APK сохранить черновики и состояние активных сессий;
- если установка приложений из этого источника запрещена, показать объяснение и кнопку `Разрешить установку`, ведущую в системные настройки конкретно для PocketCLI;
- после возврата повторно проверить разрешение и продолжить только по явному нажатию;
- Android защищает установку приложений из источников вне Google Play, поэтому пользователь может увидеть системное подтверждение.[[2]](https://developer.android.com/distribute/marketing-tools/alternative-distribution)

После успешного обновления приложение показывает одноразовую карточку `Обновлено до 1.5.0` с тремя главными изменениями и кнопкой `Готово`.

### 24.3 Настройки обновлений

| Настройка | Значение по умолчанию | Поведение |
| --- | --- | --- |
| Автоматически проверять | Включено | не чаще одного раза в 24 часа |
| Канал | Stable | Beta включает GitHub pre-release |
| Только по Wi‑Fi | Включено | мобильная загрузка требует подтверждения |
| Уведомлять о версии | Включено | одно уведомление на release tag |
| Автоматически скачать | Выключено | никогда не устанавливать автоматически |
| Пропущенная версия | Нет | хранить tag, не скрывать более новые версии |

Фоновую проверку выполнять через unique periodic WorkManager с network constraint. Не будить приложение при каждом запуске и не проверять чаще установленного TTL. ETag/If-None-Match сохранять, чтобы GitHub мог вернуть `304 Not Modified`.

### 24.4 Формат release metadata

Для клиента нужен небольшой asset `update.json`, прикреплённый к каждому GitHub Release:

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "versionName": "1.5.0",
  "versionCode": 105000,
  "tag": "v1.5.0",
  "publishedAt": "2026-10-03T12:00:00Z",
  "minSdk": 26,
  "minSupportedVersionCode": 100000,
  "critical": false,
  "apk": {
    "name": "pocketcli-1.5.0-universal.apk",
    "url": "https://github.com/OWNER/REPO/releases/download/v1.5.0/pocketcli-1.5.0-universal.apk",
    "size": 12345678,
    "sha256": "LOWERCASE_HEX_SHA256"
  },
  "notes": [
    "Краткое изменение 1",
    "Краткое изменение 2"
  ]
}
```

Правила:

- клиент сравнивает `versionCode`, а `versionName` только показывает пользователю;
- принимать только HTTPS URL на `github.com` или `objects.githubusercontent.com`;
- redirect разрешать только на HTTPS;
- ограничить максимальный размер ответа metadata и APK;
- GitHub Release API возвращает release assets с `browser_download_url`, который можно использовать для загрузки файла.[[3]](https://docs.github.com/en/rest/releases/releases)
- stable-клиент игнорирует draft и pre-release; beta-клиент может принимать pre-release;
- `minSupportedVersionCode` используется только для предупреждения о несовместимости, а не для скрытой блокировки.

### 24.5 Безопасность и целостность

Перед запуском Package Installer обязательно проверить:

1. скачанный размер совпадает с metadata;
2. SHA-256 совпадает с `update.json`/`SHA256SUMS`;
3. package name APK совпадает с `applicationId` PocketCLI;
4. `versionCode` строго больше установленного;
5. signing certificate APK совпадает с сертификатом установленного приложения;
6. APK является release build, а не debug;
7. файл находится в app-private cache и выдаётся установщику через ограниченный content URI/PackageInstaller session.

Android требует, чтобы APK обновления имел то же имя пакета и тот же signing certificate; `PackageInstaller` поддерживает upgrade существующего приложения, но commit обычно требует действия пользователя.[[1]](https://developer.android.com/reference/android/content/pm/PackageInstaller)

Ключ подписи нельзя хранить в репозитории, workflow artifacts или release assets. Keystore и пароли — только GitHub Actions Secrets. Сделать резервную офлайн-копию keystore: потеря ключа навсегда лишит возможности обновлять уже установленные APK.

### 24.6 Поведение при активном runtime

Обновление APK не должно удалять rootfs, проекты, историю или credentials.

Перед установкой:

1. закончить запись БД;
2. сохранить drafts и pending approvals;
3. корректно остановить локальный OpenCode/process supervisor;
4. записать marker `update_pending` с предыдущей и новой версиями;
5. после запуска новой версии выполнить migration и health check;
6. очистить скачанный APK только после подтверждённого запуска новой версии.

Если migration не прошла, показать recovery screen с экспортом диагностики. Автоматический downgrade APK не делать: Android и схема данных могут его не поддерживать.

### 24.7 CI/CD: автоматический GitHub Release

<aside>
🤖

Агент должен изменить существующий `.github/workflows/android.yml` либо разделить его на `android-ci.yml` и `android-release.yml`. CI для pull request остаётся без секретов и только проверяет код; публикация запускается исключительно тегом релиза или вручную из защищённой ветки.

</aside>

#### Схема версий

Использовать SemVer:

- Git tag: `vMAJOR.MINOR.PATCH`, например `v1.5.0`;
- `versionName`: `MAJOR.MINOR.PATCH`;
- `versionCode`: `MAJOR × 1 000 000 + MINOR × 1 000 + PATCH`;

![image.png](image%201.png)

- пример: `v1.5.0 → versionName 1.5.0 → versionCode 1005000`;
- pre-release: `v1.5.0-beta.1`, публикуется с флагом prerelease; Android `versionCode` для каждой beta обязан расти и не конфликтовать со stable.

Чтобы не ошибаться вручную, release workflow извлекает версию из тега, валидирует SemVer, вычисляет `versionCode` и передаёт оба значения Gradle через environment/project properties. В репозитории не должно быть трёх независимо редактируемых номеров версии.

#### Trigger и permissions

```yaml
on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'
      - 'v[0-9]+.[0-9]+.[0-9]+-*'
  workflow_dispatch:
    inputs:
      version:
        description: 'SemVer без v, например 1.5.0'
        required: true

permissions:
  contents: write
```

Для создания GitHub Release встроенному `GITHUB_TOKEN` требуется `contents: write`; права следует задавать минимально необходимыми на уровне release job.[[4]](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/controlling-permissions-for-github_token)

#### Release job — обязательный порядок

1. `actions/checkout` с полной историей тегов.
2. Установка JDK, Android SDK и Gradle cache.
3. Извлечение/валидация SemVer из `github.ref_name` или manual input.
4. Вычисление `VERSION_NAME`, `VERSION_CODE`, имени APK и prerelease-флага.
5. Декодирование release keystore из secret во временный файл.
6. Запуск unit tests, lint и обязательных verification tasks.
7. Сборка `./gradlew :app:assembleRelease` с release signing и вычисленной версией.
8. Проверка APK через `apksigner verify --verbose --print-certs`.
9. Проверка, что package/version внутри APK совпадают с ожидаемыми.
10. Переименование в `pocketcli-${VERSION_NAME}-universal.apk`.
11. Вычисление SHA-256 и создание `SHA256SUMS`.
12. Генерация `update.json` из фактического файла, размера, hash и версии — не из введённых вручную значений.
13. Генерация release notes из changelog/commits.
14. Создание GitHub Release и загрузка APK, `SHA256SUMS` и `update.json`.
15. Публикация stable или prerelease в зависимости от суффикса версии.
16. Удаление временного keystore через `if: always()`.

GitHub CLI создаёт релиз командой `gh release create TAG` и позволяет прикрепить бинарные assets.[[5]](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository?tool=cli)

Пример финального шага:

```yaml
- name: Publish GitHub Release
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    gh release create "v${VERSION_NAME}" \
      "dist/pocketcli-${VERSION_NAME}-universal.apk" \
      "dist/SHA256SUMS" \
      "dist/update.json" \
      --title "PocketCLI ${VERSION_NAME}" \
      --generate-notes \
      ${{ contains(env.VERSION_NAME, '-') && '--prerelease' || '' }}
```

Агент не должен копировать этот фрагмент вслепую: expression для prerelease лучше вычислить отдельным step/output, чтобы YAML и shell были предсказуемыми.

#### Secrets

Создать и документировать:

```
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

- secrets не доступны workflow из чужих pull requests;
- не печатать env и параметры подписи в лог;
- release job разрешён только для protected environment `production`;
- manual publish требует approval, если GitHub plan это поддерживает;
- PR workflow никогда не собирает подписанный production APK.

### 24.8 Предлагаемая архитектура Android

```
core/update/
  UpdateChannel.kt
  UpdateManifest.kt
  UpdateRepository.kt
  GitHubReleaseApi.kt
  UpdateChecker.kt
  ApkDownloader.kt
  ApkVerifier.kt
  ApkInstaller.kt
  UpdateWorkScheduler.kt

feature/settings/update/
  UpdateRoute.kt
  UpdateViewModel.kt
  UpdateUiState.kt
  UpdateScreen.kt
  UpdateComponents.kt
```

State machine:

```
Idle
→ Checking
→ UpToDate | Available
→ Downloading(progress)
→ Verifying(step)
→ ReadyToInstall
→ AwaitingUnknownSourcesPermission
→ AwaitingSystemInstaller
→ InstalledOnNextLaunch

Любое состояние → RecoverableError | SecurityError
```

Сетевой слой не должен доверять названию asset. Искать APK и metadata по явному соглашению имён; при дубликатах или отсутствии обязательного asset возвращать понятную ошибку.

### 24.9 Тесты и Definition of Done

#### Unit tests

- SemVer и сравнение `versionCode`;
- stable/beta filtering;
- JSON schema и неизвестные поля;
- redirect/domain validation;
- SHA-256 success/failure;
- APK package/version/signature checks;
- skipped version;
- ETag/304;
- resume/cancel download;
- migration marker после перезапуска.

#### UI tests

- up-to-date;
- update available;
- changelog expand/collapse;
- downloading и rotation/process recreation;
- Wi‑Fi-only warning;
- verification failure;
- unknown-sources permission;
- system installer cancel;
- RU/EN, dark/light/dynamic color, font scale 200%.

#### CI acceptance

- push обычного commit не создаёт Release;
- PR из fork не получает signing secrets;
- tag `v1.5.0` создаёт stable Release;
- tag `v1.5.0-beta.1` создаёт prerelease;
- assets имеют точные имена и доступны для скачивания;
- `update.json` соответствует APK;
- checksum проверяется;
- APK подписан production certificate;
- повторный запуск workflow не создаёт конфликтующий release молча;
- установленная предыдущая версия обновляется поверх без потери runtime и данных.

### 24.10 Порядок внедрения для агента

1. Сначала зафиксировать схему versioning и signing в `docs/RELEASE_PROCESS.md`.
2. Затем обновить Gradle, чтобы `versionName/versionCode` принимались из CI и имели безопасный local fallback.
3. Разделить обычный CI и release publish либо чётко разделить jobs/conditions в текущем `android.yml`.
4. Добиться воспроизводимого подписанного APK и GitHub Release на тестовом prerelease tag.
5. Только после этого реализовать updater client и экран.
6. Прогнать update-path: старая release APK → новая release APK, включая сохранность local runtime.
7. В `docs/PROGRESS.md` приложить URL тестового release, SHA-256, certificate fingerprint и результаты установки.

<aside>
🚫

Не считать задачу выполненной, если workflow только загружает APK в Actions Artifacts. Требуется именно GitHub Release с тегом, номером версии, подписанным APK, checksum и `update.json`, который реально видит экран обновления.

</aside>

![image.png](image%202.png)