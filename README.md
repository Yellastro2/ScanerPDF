# Сканер документов AI: PDF

Android-приложение для сканирования бумажных документов: съёмка с живым контуром
документа, автоматическое (OpenCV) и ручное выделение границ, исправление
перспективы, фильтры с выравниванием освещения, многостраничный PDF,
офлайн OCR (Tesseract, rus+eng) и AI-анализ текста через backend-прокси.
Работает **без Google Play Services**; целевой магазин — RuStore.

## Сборка

Требуется JDK 17 и Android SDK (compileSdk 35). CI (`.github/workflows/android.yml`),
три джоба:
- `build`: `clean` → `test` → `detekt` → `assembleDebug`, debug APK артефактом;
- `instrumented-tests`: `connectedDebugAndroidTest` на эмуляторе AOSP (api 30,
  без Google Play Services), логи и XML/HTML-отчёты артефактом
  `instrumented-test-logs`;
- `release-build`: `assembleRelease` + `bundleRelease` (R8, shrinkResources,
  ABI arm64-v8a/armeabi-v7a); подпись — из GitHub Secrets (`KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), без них артефакты unsigned.

```bash
./gradlew clean
./gradlew test                       # unit-тесты (JVM)
./gradlew :composeApp:detekt         # статический анализ
./gradlew :composeApp:assembleDebug  # APK: composeApp/build/outputs/apk/debug/
./gradlew :composeApp:connectedDebugAndroidTest  # инструментальные (нужен эмулятор)
```

URL backend задаются в локальном `local.properties`, который не попадает в Git:

```properties
BACKEND_PATH=C\:\\Users\\Turbo\\StudioProjects\\LLMProxy
DEBUG_BACKEND=http://192.168.0.107:8000
RELEASE_BACKEND=https://your-backend.example.com
```

`DEBUG_BACKEND` попадает только в debug-сборку, `RELEASE_BACKEND` — только
в release. Для CI те же значения можно передать переменными окружения или
Gradle properties `debugBackend` / `releaseBackend`.

В debug используется mock RuStore: нажатие покупки создаёт фиктивный
`purchaseId`, обменивает его через `POST /v1/auth/rustore` на серверный токен
и сохраняет сессию локально. Сами AI-запросы при этом реальные и идут на
`/v1/ai/summarize`, `/v1/ai/extract`, `/v1/ai/contract` с Bearer-токеном.
Полностью локальный AI mock включается только вручную через
`-PuseMockAi=true`.

В debug-сборке сетевой обмен можно смотреть в Logcat по тегу `LLMProxy`.
Лог содержит request ID, endpoint, попытку, HTTP-статус, длительность,
размер ответа и количество распарсенных элементов. Тексты документов,
JSON-тела, `purchaseId` и access token в Logcat не выводятся.

Release использует настоящий RuStore Billing и после покупки или
восстановления также обменивает `purchaseId` на серверный токен. Значение
`RELEASE_BACKEND` должно быть HTTPS URL.

## Состояние функций (честно)

**Реализовано и работает:**
- Съёмка CameraX с живым контуром документа (ImageAnalysis, backpressure
  KEEP_ONLY_LATEST, троттлинг, переключатель Авто/Вручную), защита от
  параллельных снимков.
- Автодетект границ на OpenCV (Canny → контуры → выпуклые четырёхугольники →
  оценка площади/углов → лучший кандидат); fallback с отступами только если
  достоверного контура нет. EXIF-ориентация учитывается.
- Ручная коррекция углов с лупой и валидацией самопересечений; перспектива
  через проективное преобразование.
- Неразрушающие фильтры; Ч/Б — adaptive threshold с выравниванием
  неравномерного освещения (OpenCV); «Улучшение» — удаление теней.
- Многостраничный PDF (A4/авто, поля, качество до 3200 px), Sharesheet,
  drag-and-drop и кнопочная перестановка страниц, импорт из галереи.
- Офлайн OCR: Tesseract (tesseract4android) с моделями rus+eng
  (tessdata_fast) в APK; прогресс по страницам, отмена, редактирование,
  копирование, экспорт TXT.
- AI-экран (саммари/реквизиты/договор) с согласием на отправку текста и
  дисклеймером; текст-only, изображения не отправляются.
- RuStore Billing в release DI: продукты, покупка, восстановление, проверка
  подписки при запуске и обмен покупки на серверный access token; лимиты
  Free-плана.
- Splash screen, adaptive icon, приватность (локальное хранение, исключение
  из бэкапа, «Удалить все данные», политика и условия в приложении).

**Ограничения и допущения:**
- Mock AI существует ТОЛЬКО в debug и только при флаге `USE_MOCK_AI`
  (по умолчанию false, всегда false в release). Release без
  `RELEASE_BACKEND` отключает AI-функции с понятным сообщением — фиктивные
  результаты не показываются.
- Mock RuStore используется ТОЛЬКО в debug, но серверная авторизация и
  AI-запросы остаются настоящими. Release DI использует RuStore; покупки
  требуют публикации приложения в RuStore Console (sandbox-тестеры).
- Инструментальные тесты (детектор на 7 сценариях, OCR на 3 документах,
  Room DAO) не запускаются в CI без эмулятора — команда выше.
- Автоснимок: архитектура готова (стабильная детекция N кадров), выключен.

**Почему Tesseract, а не PaddleOCR:** у PaddleOCR нет официально
сопровождаемого Android-биндинга; интеграция требует ручной сборки C++
библиотек и конвертации моделей, что ненадёжно для первой версии.
`tesseract4android` — активно сопровождаемая обёртка без GMS, модели
`tessdata_fast` (rus 3.9 МБ + eng 4.1 МБ) лежат в assets и копируются во
внутреннее хранилище при первом распознавании. Интерфейс `OcrEngine`
позволяет заменить движок на PaddleOCR без переделки приложения.

## RuStore Console: какие продукты создать

1. Создайте приложение в [RuStore Console](https://console.rustore.ru), получите
   `consoleApplicationId` (число из URL приложения) — передаётся в сборку
   через `-ProstoreConsoleAppId=...`.
2. Раздел «Монетизация → Подписки»: создайте две подписки с ID
   `premium_monthly` (1 месяц) и `premium_yearly` (1 год). Другие ID можно
   передать через `-ProstoreMonthlyId=... -ProstoreYearlyId=...`.
3. Добавьте тестовые аккаунты в sandbox для проверки покупок до публикации.
4. Deeplink-схема оплат: `scannerai` (уже прописана в манифесте).

## Подпись release через GitHub Secrets

Keystore и пароли в Git не хранятся. Создайте secrets:
`KEYSTORE_BASE64` (base64 файла .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`, и в release-workflow декодируйте keystore и передайте
параметры в Gradle:

```yaml
- run: echo "$KEYSTORE_BASE64" | base64 -d > release.jks
- run: ./gradlew :composeApp:assembleRelease \
    -Pandroid.injected.signing.store.file=$PWD/release.jks \
    -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD \
    -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
    -Pandroid.injected.signing.key.password=$KEY_PASSWORD
```

## Backend AI

Рабочий backend — отдельный Kotlin/Ktor-проект `LLMProxy` по пути
`BACKEND_PATH`. Он проверяет покупку, выдаёт собственный access token и
возвращает структурированные JSON-ответы для трёх AI-режимов. Ключ
провайдера хранится только в env сервера; тексты документов не пишутся в
логи. Каталог `backend/` оставлен только как устаревший FastAPI-прототип.

## Архитектура

Один Android-модуль `composeApp`, Clean Architecture:

```
ru.aiscanner.docs/
    core/            AppResult/AppError, диспетчеры, DI (Koin)
    domain/          модели, geometry (QuadValidator/QuadGeometry),
                     logic (имена PDF, лимиты), контракты репозиториев, use cases
    data/            Room, файловое хранилище, OpenCV-обработка и детекция,
                     Tesseract OCR, AI (Ktor/Mock/Disabled), backend-сессия,
                     PDF-экспорт, RuStore Billing, аналитика
    presentation/    Compose-экраны + ViewModel (UiState/UiEffect):
                     Home, Camera, Crop, PageEditor, Document, Ocr, Ai,
                     Settings, Premium
backend/             устаревший FastAPI-прототип
```

Ключевые решения: неразрушающая обработка (оригинал + параметры, живое
превью через ColorMatrix без пересоздания JPEG); координаты обрезки
нормализованы относительно исходника; тяжёлые операции вне Main Thread,
защита от OOM (поэтапный inSampleSize); между экранами передаются только id.

## Тесты

- Unit (`src/test`): QuadValidator, QuadGeometry, PdfFileNameGenerator,
  FreePlanLimiter, use cases (create/reorder/delete/import), AiResponseParser,
  StubSubscriptionRepository, HomeViewModel.
- Инструментальные (`src/androidTest`): OpenCV-детектор на 7 синтетических
  сценариях, Tesseract OCR на 3 синтетических документах, Room DAO.
  Тестовые изображения синтетические, без персональных данных.
