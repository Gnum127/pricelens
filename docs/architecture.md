# Архитектура и ключевые решения

## Принципы

1. **Offline by construction.** Исходный манифест не запрашивает сетевых разрешений, а при слиянии
   `INTERNET` и `ACCESS_NETWORK_STATE` принудительно удаляются через `tools:node="remove"`.
   Оба OCR-движка и все их модели лежат в APK: RapidOCR AAR содержит ONNX-модели и нативные
   библиотеки, `rus.traineddata` приложение копирует из `assets` в private storage. Никаких
   загрузок моделей по сети, Play Services dynamic download, аналитики и синхронизации нет.
2. **Чистая логика отдельно от платформы.** Парсер ценника и расчёт цены за единицу — чистые
   Kotlin-функции в `domain`. Их можно тестировать без эмулятора.
3. **Один источник правды для состояния.** `CompareViewModel` держит `CompareUiState` с lists of items;
   UI просто рисует состояние и шлёт события.
4. **БД готова к истории, но история не показана в UI.** Таблицы `comparison_session` и
   `product_item` связаны внешним ключом; прототип использует одну «текущую» сессию.

## Слои

### domain (чистый Kotlin)

- `model/`
  - `OcrFrame(width, height, tokens)` и `OcrToken(text, box, confidence, source)` — координаты
    токенов в пикселях кадра; `OcrBox` использует исключительные границы `right/bottom`, как `Rect`.
  - `ProductItem(id, name, packageAmount?, packageAmountLabel?, packageUnit?, price?, unitPrice, sourceText?)`.
    Поля количества и цены упаковки nullable только для ценника с явной строкой
    «цена за 1 кг/1 л»: такие поля пользователь заполняет вручную до сравнения.
    Для обычного ценника они не-null. `packageAmount` хранится как `BigDecimal`
    в базовых единицах (г или мл), `unit` — `GRAM | MILLILITER`. Цена — `BigDecimal` рублей.
    `packageAmountLabel` хранит подпись диапазона («230-240 г»); для расчёта берётся её верхняя граница.
  - `UnitPrice(valuePerKgOrL: BigDecimal, displayUnit: KILOGRAM | LITER)`.
- `parser/OcrTokenMerger` — чистая функция: по координатам и confidence выбирает крупные рубли
  RapidOCR, мелкие копейки/слова/вес Tesseract и собирает канонический текст ценника.
- `parser/PriceTagParser` — из канонического текста возвращает `ParsedPriceTag?`:
  цена, количество, единица, опционально название. Никаких Android-типов.
- `pricing/UnitPriceCalculator` — `price / (amount / 1000)`; защита от нуля и некорректных значений.
- `usecase/`
  - `RecognizePriceTagUseCase(imageProvider, merger, parser, calculator)` → OCR кадра → сводка токенов → парсер.
  - `AddItemUseCase`, `RemoveItemUseCase`, `CompareItemsUseCase` — работают с до 5 элементами.
- `repository/PriceTagRepository` — интерфейс: сохранить/загрузить текущую выборку.

### data

- `camera/CameraController` — обёртка над CameraX: `bindToLifecycle`, `takePicture`;
  `OcrImageDecoder` декодирует JPEG-кадр в `Bitmap`, `CameraImageTextProvider` обязательно
  закрывает `ImageProxy`.
- `ocr/TesseractOcr` — кириллица, слова и копейки; `TessBaseAPI` с word-level boxes/confidence.
- `ocr/RapidOcr` — крупные рубли; `OcrEngine.detect(Bitmap, output, maxSideLen)`, boxScore/charScores.
- `ocr/TwoLayerTextRecognizer` — прогоняет оба движка по одному Bitmap и возвращает `OcrFrame`.
- `local/db/` — Room: `AppDatabase` (v2), `ProductItemEntity`, `ComparisonSessionEntity`, DAO,
  converters, `MIGRATION_1_2` (добавлен `packageAmountLabel`).
- `local/datastore/` — `SettingsDataStore` (например, последняя выбранная единица).
- `repository/PriceTagRepositoryImpl` — маппинг entity ↔ domain, доступ к текущей сессии.

### ui

- `compare/CompareScreen` — Compose: сетка карточек (до 5), кнопка «Добавить ценник», итоговая панель.
- `compare/CompareViewModel` — state + события; лимит в 5 элементов проверяется и здесь, и в use case.
- `capture/CameraScreen` (или bottom sheet) — предпросмотр камеры, рамка наведения, кнопка съёмки.
- `core/design/` — `AppTheme`, `Color`, `Shape`, `Type`, переиспользуемые `PriceCard`, `AddTile`, `UnitPriceBadge`.

### di

- Hilt-модули: `DatabaseModule`, `RepositoryModule`, `OcrModule`, `CameraModule`.

## Поток данных (добавление ценника)

```
Камера (ImageProxy)
   → OcrImageDecoder (Bitmap)
   → TesseractOcr + RapidOcr (токены с координатами)
   → OcrTokenMerger (канонический текст)
   → PriceTagParser (ParsedPriceTag)
   → UnitPriceCalculator (цена за кг/л)
   → AddItemUseCase (лимит 5, маппинг в ProductItem)
   → Repository (Room, текущая сессия)
   → CompareViewModel обновляет UiState
   → Compose перерисовывает карточку и итог
```

Ошибки: sealed `OcrResult { Success, EmptyText, Unparseable, CameraError, OcrEngineError, PermissionDenied }`.
`OcrEngineError` означает, что локальная модель не загрузилась: распознавание не продолжается по одному RapidOCR.
Пользователю показывается понятная строка из `strings.xml`, «сырой» текст можно сохранить в карточке
для отладки парсера (но не в продакшен-UI без надобности).

## Единицы и округление

- Внутри всё в базовых единицах: граммы и миллилитры. 1 кг = 1000 г, 1 л = 1000 мл.
- Цена за единицу считается в рублях за кг / за л, показывается с копейками.
- Сравнение — по числовому значению `BigDecimal`, а не по отформатированной строке.
- Если ценник содержит «цена за кг» напрямую — доверяем ей, но помечаем источник.

## Почему именно такой стек

- Compose + Material 3 — быстро собрать мягкий округлый UI без вёрстки на XML.
- CameraX — единый API камеры с управлением жизненным циклом.
- Два локальных OCR-движка вместо ML Kit: ML Kit Latin не распознаёт кириллицу, а на реальных
  ценниках терял и цифры. Tesseract (`rus`, word-level) читает названия, вес и копейки, но не видит
  крупные рубли; RapidOCR читает рубли, но путает кириллицу и мелкие копейки. Сводка по координатам
  даёт оба поля цены без сети.
- Room — локальное хранение и лёгкий путь к истории сравнений позже.
- Hilt — предсказуемое графовое DI, просто подставлять фейки в тестах.
- Coroutines — `suspend`-интерфейс `ImageTextProvider`; `runBlocking` нужен и в проде для CameraX/Tesseract/RapidOCR,
  и в юнит-тестах use-case. Версия зафиксирована в version catalog.
- CameraX + два bundled OCR-движка — путь от кадра к тексту без сети и без dynamic download.
- Room и DataStore — локальное хранение текущей выборки и настроек; Room-сущности связаны
  с сессией, чтобы история сравнений не требовала миграции.
- Hilt + KSP — DI для подстановки фейков в тестах; KSP используется и Hilt, и Room, чтобы не тянуть kapt.
- `lifecycle-viewmodel-ktx` и `lifecycle-runtime-compose` — `viewModelScope` для распознавания и
  `collectAsStateWithLifecycle` для безопасного сбора потоков ViewModel в Compose.
- `kotlinx-coroutines-test` (только в `testImplementation`) — детерминированный Main-диспетчер
  для юнит-тестов ViewModel с `viewModelScope`.
- `hilt-navigation-compose` — `hiltViewModel()` в Composable без ручного создания фабрики ViewModel.
- `ui-test-junit4` (только `androidTest`) и `ui-test-manifest` (только `debug`) — минимальные Compose
  UI-тесты критического пути; версии берутся из того же Compose BOM, без новых literal-версий.

### Обеспечение OCR (bundled AAR)

| Компонент | Файл | Размер | SHA-256 | Источник |
| --- | --- | --- | --- | --- |
| RapidOCR 1.3.0 | `app/libs/OcrLibrary-1.3.0-release.aar` | 39 394 301 | `735EFDAEB7E43591EEABBB52D34064DEA68AA0FCFC4554D04A0BFE44334E6E18` | RapidAI/RapidOcrAndroidOnnx release 1.3.0 |
| Tesseract4Android 4.9.0 | `app/libs/tesseract4android-4.9.0.aar` | 12 848 454 | `BCE5D6413A1A5AE3D7240033FBBC851BA3217D0A08D9769400E17A077F42CB2A` | JitPack `cz.adaptech.tesseract4android` |
| Русская модель | `app/src/main/assets/tessdata/rus.traineddata` | 3 861 738 | `E16E5E036CCE1D9EC2B00063CF8B54472625B9E14D893A169E2B0DEDEB4DF225` | tessdata 4.0.0 |

AAR лежат в репозитории: сборка воспроизводимая и не требует сети. Быстрый тест ML Kit и вариант
«один Tesseract с предобработкой» отклонены на реальных ценниках: оба варианта теряли рубли.
Лицензии и атрибуция — в `docs/third-party-licenses.md`.

### Офлайн и приватность на устройстве

- `android:fullBackupContent` для Android 11 и ниже и `android:dataExtractionRules` с полным `exclude`
  для `cloud-backup` и `device-transfer` на Android 12+: данные не должны покидать устройство ни через
  облако, ни при переносе на другой телефон; `allowBackup="false"` оставлен для совместимости.
- Проверка офлайна — ручная, на устройстве в режиме полёта (порядок в `README.md`): сеть не нужна ни
  на старте, ни при распознавании, ни после перезапуска. Сетевые разрешения удалены из итогового
  манифеста, OCR bundled.
- `app/lint.xml` выключает только два информационных предупреждения о наличии более новых версий
  (`GradleDependency`, `AndroidGradlePluginVersion`). Версии в `libs.versions.toml` зафиксированы и
  проходят полный прогон сборки; обновление AGP/Compose/CameraX/Room — отдельная задача с повторной
  проверкой камеры и OCR, а не часть QA-полировки прототипа.

Отклонённые варианты: ML Kit Latin (нет кириллицы, плохо читал цифры на реальных ценниках),
один Tesseract с предобработкой кропа (крупные рубли не читаются ни при одном варианте),
один RapidOCR (копейки `99` превращались в `66`), Firebase/облачный OCR (нарушает офлайн),
Java + XML (медленнее разрабатывать), Koin (для прототипа достаточно Hilt).
