# Устаревший локальный прокси

Каталог `backend/` содержит прежний FastAPI-прототип и больше не является
рабочим backend приложения.

Актуальный Kotlin/Ktor-сервис находится по пути `BACKEND_PATH` из корневого
`local.properties`. Его API:

- `POST /v1/auth/rustore` — обмен `purchaseId` и `productId` на access token;
- `POST /v1/ai/summarize` — краткое содержание;
- `POST /v1/ai/extract` — извлечение реквизитов;
- `POST /v1/ai/contract` — анализ договора;
- `GET /health` — проверка доступности.

Android debug-сборка получает URL из `DEBUG_BACKEND`, release-сборка — из
`RELEASE_BACKEND`. Оба ключа задаются в `local.properties`; для CI доступны
одноимённые переменные окружения.
