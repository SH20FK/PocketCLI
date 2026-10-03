# PocketCLI — Спецификация иконки приложения (Pocket Prompt)

## 1. Концепция знака
Знак **Pocket Prompt** объединяет форму открытого кармана (Pocket) и терминальный шеврон с курсором (`>_`).
- Никаких роботов, мозгов, искр AI или букв `P`.
- Чистая геометрия, читаемая одной линией толщиной `2.25dp` на сетке `24 × 24`.
- Все окончания линий и стыки: `StrokeCap.Round`, `StrokeJoin.Round`.

## 2. Геометрия на координатной сетке 24 × 24

```
Карман (Pocket):
M5 8 V16 C5 18.2 6.8 20 9 20 H15 C17.2 20 19 18.2 19 16 V8

Шеврон (Prompt):
M8.5 10.5 L11.5 13 L8.5 15.5

Курсор (Cursor):
M13 15.5 H16
```

## 3. Слои Adaptive Icon (108 × 108 dp)
- **Safe Zone**: центральный квадрат `66 × 66 dp` (от `(21, 21)` до `(87, 87)`).
- **Масштаб знака**: примерно `58 × 58 dp`, центрирован в Safe Zone.
- **Слои**:
  1. `ic_launcher_background.xml`: сплошной глубокий индиго/фиолетовый фон (`#21182F` / `#4F378B`).
  2. `ic_launcher_foreground.xml`: светлый линейный знак Pocket Prompt (`#F7F2FF` / `#DCC2FF`).
  3. `ic_launcher_monochrome.xml`: силуэт знака без фона для системной тонировки Android 13+ Monet Themed Icons.

## 4. Цветовые пары
| Вариант | Background | Foreground | Назначение |
| --- | --- | --- | --- |
| Indigo Brand | `#4F378B` | `#F7F2FF` | Основной launcher icon |
| Dark-first | `#21182F` | `#DCC2FF` | Глубокая тёмная тема |
| Monochrome | `@android:color/transparent` | Системный Monet tint | Themed Icons |
