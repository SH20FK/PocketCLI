# PocketCLI — Спецификация системы анимации (Motion Spec)

## 1. Принципы Motion
- **Информативность:** каждая анимация объясняет смену состояния, пространственную связь и обратную связь.
- **Calm by default:** отсутствие бесконечных декоративных циклов и пульсаций.
- **Physics-based:** пружинные переходы Material 3 с умеренным демпфированием.
- **Reduce Motion:** уважение системной настройки доступности `transition_animation_scale = 0` / Reduce Motion с заменой morph/scale на моментальный crossfade.

## 2. Единые токены времени и пружин (PocketMotion)

| Токен | Длительность / Параметры | Назначение |
| --- | --- | --- |
| `instant` | `90–120 мс` | Feedback при нажатии (ripple, scale down) |
| `quick` | `160–220 мс` | Смена иконок (morph/crossfade), переключение чипов, tooltip |
| `standard` | `260–340 мс` | Раскрытие bottom sheet, карточек, аккордеонов (reasoning, details) |
| `emphasized` | `400–520 мс` | Shared bounds, переход в проект или чат, онбординг |
| `slowAmbient` | `1800–3200 мс` | Медленное дыхание AI-индикатора при фоновом ожидании |
| `springSpatial` | `stiffness = MediumLow, damping = NoBounce` | Перемещение элементов в пространстве, репозиционирование |
| `springExpressive` | `stiffness = Medium, damping = MediumBounce` | Интерактивные переходы, Send → Stop morph |
| `springGesture` | `stiffness = High, damping = NoBounce` | Прямое следование за пальцем (bottom sheet drag, swipe) |

## 3. Таблица переходов состояний по flow

| Момент | Анимация | Технология | Reduce Motion Fallback |
| --- | --- | --- | --- |
| Холодный старт | Logo reveal → morph в индикатор после 300мс | Compose Path / AnimatedVector | Статичный логотип |
| Выбор Remote / Local | Morph выбранной карточки | Compose M3 Spring | Мгновенный crossfade |
| Test Connection | Кнопка → Spinner → Check/Error | AnimatedContent / Vector | Прямая смена иконки |
| Установка runtime | Progress bar + плавная смена шагов | AnimatedContent | Мгновенная смена текста |
| Открытие проекта | Card → Project Detail header | Shared Bounds / Fade-through | Fade-through |
| Открытие чата | SessionRow → Chat Screen | Shared Axis / Fade | Мгновенный переход |
| Отправка сообщения | Send morph в Stop + фиксация baseline | Path morph + Spring | Мгновенная замена иконки |
| Thinking (думает) | Компактный живой индикатор в status pill | Infinite Transition (ограничено viewport) | Статичная точка |
| Tool execution | Pending → Running → Result | AnimatedContent / Path | Мгновенная иконка |
| Запрос разрешения | Одиночный акцентный pulse контейнера | Compose Scale / Color | Статичная карточка |
| Diff navigation | Floating toolbar переходит к следующему hunk | Smooth Scroll + highlight fade | Мгновенный скролл |
