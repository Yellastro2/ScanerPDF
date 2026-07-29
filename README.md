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

# ID приложения из RuStore Console, не ID ключа Public API
RUSTORE_CONSOLE_APP_ID=123456
RUSTORE_MONTHLY_ID=premium_monthly
RUSTORE_YEARLY_ID=premium_yearly
RUSTORE_FOREVER_ID=premium_forever

# true принудительно оставляет debug на mock-покупках
USE_MOCK_RUSTORE=false
```

`DEBUG_BACKEND` попадает только в debug-сборку, `RELEASE_BACKEND` — только
в release. Для CI те же значения можно передать переменными окружения или
Gradle properties `debugBackend` / `releaseBackend`.

Если `RUSTORE_CONSOLE_APP_ID` не задан, debug автоматически использует mock
RuStore. После заполнения ID debug переключается на настоящий RuStore Pay SDK;
вернуть mock можно через `USE_MOCK_RUSTORE=true`. Mock-покупка создаёт
фиктивный `purchaseId`, но всё равно обменивает его через настоящий
`POST /v1/auth/rustore`. Полностью локальный AI mock включается только вручную
через `-PuseMockAi=true`.

AI-запросы идут на `/v1/ai/summarize`, `/v1/ai/extract` и `/v1/ai/contract`
с серверным Bearer-токеном. `purchaseId` передаётся только при покупке или
восстановлении подписки, а не с каждым AI-запросом.

В debug-сборке сетевой обмен можно смотреть в Logcat по тегу `LLMProxy`.
Лог содержит request ID, endpoint, попытку, HTTP-статус, длительность,
размер ответа и количество распарсенных элементов. Тексты документов,
JSON-тела, `purchaseId` и access token в Logcat не выводятся.

Диагностика настоящего RuStore Pay доступна в debug по тегу `RuStorePay`.
Она показывает доступность платежей и цепочку причин ошибки, загрузку товаров,
этапы платёжной шторки, результат SDK, восстановление и проверку через backend.
`purchaseId` и `invoiceId` выводятся только в маскированном виде; ключи и
токены не логируются. В release по этому тегу сохраняются только обезличенные
ошибки без подробных событий, чтобы отличать сбой SDK от недоступности backend.

Release всегда использует настоящий RuStore Pay SDK и после покупки или
восстановления обменивает `purchaseId` на серверный токен. Значение
Mock-флаги в release игнорируются. Приложение временно разрешает cleartext HTTP
и для release, поэтому `RELEASE_BACKEND` может указывать на `http://IP:port`.
**Внимание: это потенциально опасная временная настройка.** Такой трафик не
шифруется и может быть перехвачен или изменён в сети, включая токены backend и
данные запросов. Перед публичным выпуском необходимо перейти на HTTPS и удалить
`android:usesCleartextTraffic="true"` из основного manifest.
После успешной оплаты приложение сохраняет `purchaseId` локально до ответа
backend. Если активация временно не удалась, она повторяется с задержкой при
возврате в приложение, следующем `onResume` и ручном восстановлении. Поэтому
завершённая в RuStore покупка не теряется при остановке backend или процесса
приложения.

Pay SDK возвращает покупки VK-пользователя только пока действует его короткая
сессия авторизации. При ручном восстановлении приложение сначала вызывает
`getUserAuthorizationStatus()`. Если сессия истекла, открывается штатная
авторизация RuStore через `openRuStoreAuthorization()`; после возврата
`onResume` автоматически повторяет получение покупок и backend-проверку.

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
- RuStore Pay SDK в release DI: продукты, покупка, восстановление, проверка
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
  AI-запросы остаются настоящими. Настоящие покупки требуют регистрации
  приложения, продуктов и тестовых пользователей в RuStore Console.
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
   ID приложения и запишите его в `local.properties` как
   `RUSTORE_CONSOLE_APP_ID`. Это не ID ключа Public API.
2. В разделе монетизации создайте:
   - подписку `premium_monthly` на 1 месяц;
   - подписку `premium_yearly` на 1 год;
   - непотребляемый разовый товар `premium_forever`.
   Другие ID можно задать через `RUSTORE_MONTHLY_ID`, `RUSTORE_YEARLY_ID` и
   `RUSTORE_FOREVER_ID`.
3. Добавьте тестовые аккаунты в sandbox для проверки покупок до публикации.
4. Deeplink-схема оплат: `scannerai` (уже прописана в манифесте).
5. Package name приложения в RuStore должен быть `ru.aiscanner.docs`, а
   сертификат установленной сборки должен совпадать с сертификатом в RuStore.

Приложение использует RuStore Pay SDK `10.5.0` через BOM `2026.06.01`.
Подписки и бессрочный товар приобретаются одноэтапно (`ONE_STEP`), затем
`purchaseId`, `productId`, `invoiceId` и `productType` отправляются backend.
Приложение считает `premium_forever` активным только для типа
`NON_CONSUMABLE_PRODUCT` в финальном статусе `CONFIRMED`. Настройка SDK соответствует
[официальной инструкции RuStore Pay](https://www.rustore.ru/help/sdk/pay/kotlin-java/10-5-0).

Для тестирования настоящей оплаты debug APK нужно подписывать тем же ключом,
который зарегистрирован в RuStore. Сборка читает его только из окружения:
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Без этих
переменных debug использует стандартный debug keystore, который обычно не
совпадает с опубликованной подписью.

## Backend: настоящая проверка RuStore

Секреты RuStore нельзя добавлять в `local.properties`, Gradle или APK. Они
задаются в `C:\Users\Turbo\StudioProjects\LLMProxy\.env.local`:

```properties
RUSTORE_MODE=api
RUSTORE_TEST=true
RUSTORE_API_ID=put_rustore_key_id_here
RUSTORE_PRIVATE_KEY=put_pkcs8_private_key_here
```

`RUSTORE_TEST=true` включает sandbox, для production используется `false`.
`RUSTORE_API_ID` — ID ключа Public API, а не ID приложения. Приватный RSA-ключ
должен быть PKCS#8 в Base64 или PEM и хранится только на сервере. Backend
проверяет подписку через RuStore API V4, сохраняет статус в SQLite и выдаёт
приложению собственный access token.

### Требование backend для `premium_forever`

Текущая серверная проверка подписок сама по себе не поддерживает разовый
непотребляемый товар. До выкладки `premium_forever` backend должен:

1. Принять необязательные `invoiceId` и `productType` в
   `POST /v1/auth/rustore`, не меняя обработку существующих подписок.
2. Для `productType=NON_CONSUMABLE_PRODUCT` и
   `productId=premium_forever` запросить счёт:
   `GET /public/v2/invoices/{invoiceId}` либо sandbox-вариант
   `GET /public/sandbox/v2/invoices/{invoiceId}`.
3. Выдать Premium только если `invoiceStatus=CONFIRMED`, а `purchaseId`,
   `appId` и `order.itemCode` совпадают с ожидаемыми значениями.
4. Вернуть прежнюю форму ответа `POST /v1/auth/rustore`: статус `active`,
   `productId=premium_forever`, бессрочный `validUntil` и
   `autoRenewEnabled=null`. Endpoint отмены автопродления к такому товару
   неприменим.

Пока эта ветка проверки не реализована на сервере, Android покажет товар и
сможет провести оплату через RuStore, но не должен получить подтверждённый
Premium-доступ от backend.

В успешном ответе `POST /v1/auth/rustore` сервер также возвращает
`subscription.productId`, `subscription.validUntil` и
`subscription.autoRenewEnabled`. Последнее поле может быть `null`, если
RuStore не передал признак автопродления; клиент не должен трактовать такое
значение как отключённое автопродление.

## Подпись release через GitHub Secrets

Keystore и пароли в Git не хранятся. Создайте secrets:
`KEYSTORE_BASE64` (base64 файла .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`, `RELEASE_BACKEND` и `RUSTORE_CONSOLE_APP_ID`. Release-workflow
декодирует keystore во временный файл и передаёт его путь через
`KEYSTORE_FILE`:

```yaml
- run: echo "$KEYSTORE_BASE64" | base64 -d > release.jks
- run: KEYSTORE_FILE=$PWD/release.jks ./gradlew :composeApp:assembleRelease
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
                     PDF-экспорт, RuStore Pay SDK, аналитика
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
