# PocketCLI — Каталог анимационных ресурсов и лицензии

## 1. Политика внедрения ресурсов
1. **Нативный приоритет:** Все критические UI-переходы, морфинг иконок и индикаторы статусов строятся на чистом Compose Animation и Android VectorDrawables.
2. **Локальность:** Все ресурсы хранятся внутри сборки приложения, внешняя подгрузка по CDN во время работы запрещена.
3. **Производительность:** Любая циклическая анимация останавливается, если компонент уходит из viewport или жизненный цикл экрана переходит в `STOPPED`.

## 2. Утверждённые пары морфинга иконок (`PocketAnimatedIcon`)

| Пара | Состояние A | Состояние B | Назначение |
| --- | --- | --- | --- |
| Send / Stop | `Send` | `Stop` | Композер чата во время выполнения задачи агентом |
| Play / Stop | `PlayArrow` | `Stop` | Запуск и остановка локального OpenCode PRoot процесса |
| Expand / Collapse | `ExpandMore` | `ExpandLess` | Раскрытие reasoning, шагов диффа и деталей команд |
| Mic / Send | `Mic` | `Send` | Голосовой ввод / отправка |
| Sync / Check | `Sync` | `Check` | Проверка подключения к серверу и проверка обновлений |
| Visibility | `Visibility` | `VisibilityOff` | Просмотр паролей и API-токенов |

## 3. Реестр сторонних анимаций и лицензий

| Ресурс | Назначение | Технология | Лицензия | Fallback |
| --- | --- | --- | --- | --- |
| Pocket Prompt Logo | Загрузка и Splash | Compose Path Vector | Проприетарный / Встроенный | Статичный логотип |
| Thinking Waveform | Индикатор размышления в пилюле | Compose Canvas / Spring | MIT | Три статичные точки |
| Checkmark Morph | Подтверждение операции | AnimatedVectorDrawable | Apache-2.0 | Иконка `Icons.Default.Check` |
| Error Shake | Ошибка выполнения | Compose Offset Animation | Apache-2.0 | Иконка `Icons.Default.Error` |
