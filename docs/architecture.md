# Архитектура и ключевые решения

## Принципы

1. **Offline by construction.** В манифесте нет `INTERNET`. Модель ML Kit включается в APK
   (`com.google.mlkit:text-recognition`, bundled), а не скачивается через Play Services.
2. **Чистая логика отдельно от платформы.** Парсер ценника и расчёт цены за единицу — чистые
   Kotlin-функции в `domain`. Их можно тестировать без эмулятора.
3. **Один источник правды для состояния.** `CompareViewModel` держит `CompareUiState` с lists of items;
   UI просто рисует состояние и шлёт события.
4. **БД готова к истории, но история не показана в UI.** Таблицы `comparison_session` и
   `product_item` связаны внешним ключом; прототип использует одну «текущую» сессию.

## Слои

### domain (чистый Kotlin)

- `model/`
  - `ProductItem(id, name, packageAmount?, packageUnit?, price?, unitPrice, sourceText?)`.
    Поля количества и цены упаковки nullable только для ценника с явной строкой
    «цена за 1 кг/1 л»: такие поля пользователь заполняет вручную до сравнения.
    Для обычного ценника они не-null. `packageAmount` хранится как `BigDecimal`
    в базовых единицах (г или мл), `unit` — `GRAM | MILLILITER`. Цена — `BigDecimal` рублей.
  - `UnitPrice(valuePerKgOrL: BigDecimal, displayUnit: KILOGRAM | LITER)`.
- `parser/PriceTagParser` — из сырого текста ML Kit возвращает `ParsedPriceTag?`:
  цена, количество, единица, опционально название. Никаких Android-типов.
- `pricing/UnitPriceCalculator` — `price / (amount / 1000)`; защита от нуля и некорректных значений.
- `usecase/`
  - `RecognizePriceTagUseCase(imageProvider)` → вызывает OCR + парсер.
  - `AddItemUseCase`, `RemoveItemUseCase`, `CompareItemsUseCase` — работают с до 5 элементами.
- `repository/PriceTagRepository` — интерфейс: сохранить/загрузить текущую выборку.

### data

- `camera/CameraController` — обёртка над CameraX: `bindToLifecycle`, `takePicture`,
  преобразование `ImageProxy` → `InputImage`, обязательный `imageProxy.close()`.
- `ocr/MlKitTextRecognizer` — реализация `TextRecognizer` поверх ML Kit; возвращает чистый текст.
- `local/db/` — Room: `AppDatabase`, `ProductItemEntity`, `ComparisonSessionEntity`, DAO, converters.
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
   → MlKitTextRecognizer (сырой текст)
   → PriceTagParser (ParsedPriceTag)
   → UnitPriceCalculator (цена за кг/л)
   → AddItemUseCase (лимит 5, маппинг в ProductItem)
   → Repository (Room, текущая сессия)
   → CompareViewModel обновляет UiState
   → Compose перерисовывает карточку и итог
```

Ошибки: `Result`/sealed `OcrResult { Success, EmptyText, Unparseable, CameraError, PermissionDenied }`.
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
- ML Kit on-device — офлайн и достаточно хорош для печатного текста ценников.
- Room — локальное хранение и лёгкий путь к истории сравнений позже.
- Hilt — предсказуемое графовое DI, просто подставлять фейки в тестах.
- Coroutines — `suspend`-интерфейс `ImageTextProvider`; в юнит-тестах use-case достаточно
  `runBlocking` из `kotlinx-coroutines-core` (версия зафиксирована в version catalog).

Отклонённые варианты: Tesseract/tess-two (тяжелее и слабее на мелком тексте), Firebase/облачный OCR
(нарушает офлайн), Java + XML (медленнее разрабатывать), Koin (для прототипа достаточно Hilt,
но если агент обоснует — допустимо).
