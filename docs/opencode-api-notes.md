# Заметки по OpenCode HTTP & SSE API (v1.2.27)

Результаты исследования живого сервера `opencode serve` в рамках **Этапа 0** проекта PocketCLI.

---

## 1. Базовые принципы и аутентификация

- **Запуск сервера**: `opencode serve --port 4096 --hostname 127.0.0.1` (или `0.0.0.0`).
- **Спецификация OpenAPI**: доступна по адресу `GET http://<host>:<port>/doc`.
- **Аутентификация**: HTTP Basic Auth.
  - Логин по умолчанию: `opencode` (настраивается переменной `OPENCODE_SERVER_USERNAME`).
  - Пароль: значение переменной `OPENCODE_SERVER_PASSWORD`.
  - Передаётся в заголовке `Authorization: Basic <base64(username:password)>`.
- **CORS / Cleartext**: на стороне сервера по умолчанию включен CORS для локальных клиентов. В Android требуется явная поддержка Cleartext HTTP (для локальных IP/Tailscale).

---

## 2. Ключевые REST эндпоинты

### `GET /global/health`
Проверка доступности и версии сервера:
```json
{
  "healthy": true,
  "version": "1.2.27"
}
```

### `POST /session`
Создание новой сессии.
- Тело запроса:
```json
{
  "title": "Название сессии",
  "workspaceID": "wrk...", // опционально
  "parentID": "ses..."     // опционально
}
```
- Ответ (`200 OK`): объект `Session`:
```json
{
  "id": "ses_efffa1e35ffedQowb7drhsWwNr",
  "slug": "mighty-garden",
  "version": "1.2.27",
  "projectID": "5c8646e58a81383ce3ce0fa8bf565d392452f944",
  "directory": "C:\\Users\\...",
  "title": "Название сессии",
  "time": {
    "created": 1791001747914,
    "updated": 1791001747914
  }
}
```

### `GET /session`
Список активных сессий (массив объектов `Session`).

### `POST /session/{sessionID}/message`
Отправка сообщения/промпта агенту:
```json
{
  "parts": [
    {
      "type": "text",
      "text": "Покажи структуру проекта"
    }
  ],
  "model": {               // опционально
    "providerID": "...",
    "modelID": "..."
  }
}
```

### `GET /session/{sessionID}/message` (Reconcile)
Возвращает полную историю сообщений сессии со всеми частями (текст, reasoning, tool calls):
```json
[
  {
    "info": {
      "id": "msg_ast_1",
      "sessionID": "ses_123",
      "role": "assistant",
      "time": { "created": 1791001800000 }
    },
    "parts": [
      { "id": "prt_1", "type": "reasoning", "text": "Рассуждение..." },
      { "id": "prt_2", "type": "text", "text": "Текст ответа..." },
      {
        "id": "prt_3",
        "type": "tool",
        "callID": "call_1",
        "tool": "bash",
        "state": {
          "status": "completed",
          "input": { "command": "ls" },
          "output": "..."
        }
      }
    ]
  }
]
```

### `POST /session/{sessionID}/abort`
Прерывание текущего выполнения (кнопка Стоп).
Ответ: `true` (boolean).

### `POST /permission/{requestID}/reply`
Ответ на запрос прав (Approve / Reject):
```json
{
  "reply": "once" // варианты: "once" | "always" | "reject"
}
```

---

## 3. Server-Sent Events (SSE): `GET /global/event`

Поток Server-Sent Events имеет вид `data: <json>\n\n`.
Формат JSON:
```json
{
  "directory": "...", // опционально
  "payload": {
    "type": "<event_type>",
    "properties": { ... }
  }
}
```

### Основные типы событий и их маппинг в `AgentEvent`:

1. **`server.connected`**:
   - Начальное событие при установлении SSE-подключения.

2. **`session.status`**:
   - Свойства: `{ "sessionID": "ses_...", "status": { "type": "busy" | "idle" | "retry" } }`
   - Маппинг: `AgentEvent.SessionStatus(id, state)` (BUSY, IDLE, ERROR).

3. **`session.idle`**:
   - Свойства: `{ "sessionID": "ses_..." }`
   - Сигнализирует о полном завершении текущего шага агента.

4. **`message.updated`**:
   - Свойства: `{ "info": { "id": "msg_...", "sessionID": "...", "role": "assistant" } }`
   - Маппинг: `AgentEvent.MessageStarted(sessionId, messageId, role)`.

5. **`message.part.delta`**:
   - Свойства: `{ "sessionID": "...", "messageID": "...", "partID": "...", "field": "text" | "reasoning", "delta": "..." }`
   - Маппинг:
     - `field == "text"` -> `AgentEvent.TextDelta(messageId, delta)`
     - `field == "reasoning"` -> `AgentEvent.ReasoningDelta(messageId, delta)`

6. **`message.part.updated`**:
   - Свойства: `{ "part": { "id": "...", "type": "tool", "callID": "...", "tool": "bash", "state": { "status": "running" | "completed" | "error", "input": {...}, "output": "..." } } }`
   - Маппинг: `AgentEvent.ToolCallUpdate(messageId, callId, name, status, input, output)`.

7. **`permission.asked`**:
   - Свойства: `{ "id": "per_req_1", "sessionID": "...", "permission": "bash", "patterns": [...], "always": [...], "tool": { "messageID": "...", "callID": "..." } }`
   - Маппинг: `AgentEvent.PermissionRequested(requestId, callId, title, options)`.

---

## 4. Особенности реализации сетевого клиента

1. **Таймауты**: OkHttp-клиент для SSE ОБЯЗАН использовать `.readTimeout(0, TimeUnit.MILLISECONDS)`. Любой ненулевой таймаут приведёт к обрыву потока при ожидании следующего токена или инструмента.
2. **Толерантность к новым событиям**: в `payload.type` могут приходить `lsp.updated`, `pty.created`, `todo.updated` и т.д. Клиент обязан использовать `ignoreUnknownKeys = true` в kotlinx.serialization и логировать неизвестные события без выброса исключений.
3. **Reconcile**: при реконнекте SSE или возврате из фонового режима клиент делает `GET /session/{sessionID}/message` и синхронизирует состояние в Room.
