# PocketCLI — Процесс релизов и обновления (OTA Release Process)

## 1. Схема версионирования (SemVer)
- Формат Git-тегов: `vMAJOR.MINOR.PATCH` (например `v1.0.0`) или `vMAJOR.MINOR.PATCH-beta.X` (например `v1.0.0-beta.1`).
- `versionName`: `MAJOR.MINOR.PATCH` (или с суффиксом beta).
- `versionCode`: монотонно возрастающее 32-битное целое число по формуле:
  $$\text{versionCode} = \text{MAJOR} \times 1\,000\,000 + \text{MINOR} \times 1\,000 + \text{PATCH}$$
  Например: `1.0.0` $\rightarrow$ `1000000`, `1.5.0` $\rightarrow$ `1005000`.

## 2. CI/CD сборка и подпись релизов
1. Триггеры:
   - Создание тега с маской `v*`.
   - Ручной запуск `workflow_dispatch` с указанием версии.
2. Безопасность секретов:
   - `RELEASE_KEYSTORE_BASE64`
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
   - Доступны только в защищенной ветке / релизном workflow. При их отсутствии в PR или локальной сборке используется Debug Keystore.

## 3. Артефакты релиза
Каждый GitHub Release публикует три обязательных артефакта:
1. `pocketcli-{versionName}-universal.apk` — подписанный release APK.
2. `SHA256SUMS` — файл с SHA-256 контрольными суммами всех файлов.
3. `update.json` — машиночитаемый манифест обновления для клиента:
```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "versionName": "1.0.0",
  "versionCode": 1000000,
  "tag": "v1.0.0",
  "publishedAt": "2026-10-03T12:00:00Z",
  "minSdk": 28,
  "minSupportedVersionCode": 1000000,
  "critical": false,
  "apk": {
    "name": "pocketcli-1.0.0-universal.apk",
    "url": "https://github.com/SH20FK/PocketCLI/releases/download/v1.0.0/pocketcli-1.0.0-universal.apk",
    "size": 12345678,
    "sha256": "checksum..."
  },
  "notes": [
    "Первый стабильный релиз",
    "Поддержка Material 3 Expressive и PRoot рантайма"
  ]
}
```

## 4. Клиентская валидация перед установкой
Перед передачей скачанного APK системному `PackageInstaller`:
1. Проверка размера файла.
2. Вычисление SHA-256 и сверка с манифестом.
3. Сверка `applicationId` (`com.pocketcli`).
4. Проверка, что `versionCode` строго выше текущей установленной версии.
