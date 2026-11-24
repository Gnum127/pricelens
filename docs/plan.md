# План работ — PriceLens

Документ фиксирует план прототипа и обязательный процесс его выполнения.
Обновляется по мере закрытия этапов; старые записи не удаляются, а отмечаются как закрытые.

## 1. Процесс выполнения этапа

Каждый этап выполняется в **отдельной ветке** `stage/<NN>-<slug>`, созданной от актуального `main`.
Напрямую в `main` код не пишется.

Порядок внутри этапа (не менять):

1. **Реализация кода** в ветке этапа.
2. **Ревью кода** — `general`-сабагент по инструкции [`agents/code-review.md`](agents/code-review.md).
   Передать diff изменений. Все `BLOCKER`/`MAJOR` исправить по замечаниям.
3. **Написание тестов** на изменённую логику.
4. **Ревью тестов** — `general`-сабагент по инструкции [`agents/test-review.md`](agents/test-review.md).
   Передать diff тестов и соответствующий продакшен-код. Все `BLOCKER`/`MAJOR` исправить.
5. **Успешный запуск проверок:**
   `./gradlew :app:assembleDebug`, `./gradlew test`, `./gradlew lint` — все зелёные.
6. **Закрытие этапа:** merge ветки в `main`, удалить ветку, `git push origin main`.

Локальные проверки разрешается гонять и раньше, но финальный зелёный прогон делается **после** всех ревью.

### Ограничение на ревью

- Не более **5 итераций** на каждый вид ревью: код и тесты считаются отдельно.
- Если после 5-й итерации остались `BLOCKER`/`MAJOR` — этап **не закрывается**.
  Агент останавливается и отдаёт пользователю отчёт с оставшимися замечаниями; слияние в `main` запрещено.
- Каждое замечание должно получить реакцию: исправлено или явно обосновано, почему не исправляется.
  Для `BLOCKER`/`MAJOR` обоснование не принимается — только фикс.
- `MINOR`/`NIT` можно вынести в follow-up, но они перечисляются в отчёте.

## 2. Этапы

### Этап 0. Фундамент — ✅ закрыт

- Удалён старый `Gnum127/CostCatch`, создан `Gnum127/pricelens`.
- Локально `C:\dev\pricelens`, ветка `main`, remote настроен.
- `AGENTS.md`, `docs/product.md`, `docs/architecture.md`, `docs/structure.md`, `docs/ocr-notes.md`.
- Инструкции сабагентов: `docs/agents/code-review.md`, `docs/agents/test-review.md`.
- Текущий документ зафиксирован как история плана.

### Этап 1. Gradle-скелет и дизайн-система

**Ветка:** `stage/01-gradle-theme`

- `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`.
- `gradle/libs.versions.toml` — все версии зависимостей в одном месте.
- `gradle.properties`, `.gitignore` дополнить Android-артефактами.
- `AndroidManifest.xml`: только `CAMERA`, **без `INTERNET`**.
- `minSdk 26`, актуальный `targetSdk`, `namespace`/`applicationId = com.pricelens`.
- `core/design/AppTheme`: Material 3, мягкие скругления, спокойная светлая палитра, крупная цифра цены.
- `res/values/strings.xml` с базовыми строками.

**Критерий закрытия:** `assembleDebug` зелёный, пустой экран в фирменной теме, ревью подтвердило отсутствие INTERNET и лишних зависимостей.

### Этап 2. Domain: чистый Kotlin

**Ветка:** `stage/02-domain`

- `domain/model/`: `ParsedPriceTag`, `ProductItem`, `UnitPrice`, `WeightUnit`, `OcrResult`.
- `domain/parser/PriceTagParser` — эвристики из `docs/ocr-notes.md`.
- `domain/pricing/UnitPriceCalculator` — ₽/кг и ₽/л, `BigDecimal`, защита от нуля.
- `domain/usecase/`: `RecognizePriceTag`, `AddItem` (лимит 5), `RemoveItem`, `CompareItems`.
- `domain/repository/PriceTagRepository` — интерфейс.
- Юнит-тесты: кейсы из `docs/ocr-notes.md`, границы, негатив.

**Критерий закрытия:** `test` зелёный, в domain нет импортов `android.*`/`androidx.*`/ML Kit, test-review подтвердил граничные и негативные кейсы.

### Этап 3. Data: камера, OCR, хранение

**Ветка:** `stage/03-data`

- `data/ocr/TextRecognizer` + `MlKitTextRecognizer` (bundled, без Play Services).
- `data/camera/CameraController` — CameraX, `ImageProxy.close()`, permission-поток.
- `data/local/db/` — Room: `ComparisonSessionEntity`, `ProductItemEntity`, DAO, converters; схема готова к истории.
- `data/local/datastore/SettingsDataStore`.
- `data/repository/PriceTagRepositoryImpl` — маппинг entity ↔ domain.
- Hilt-модули в `di/`.
- Тесты: репозиторий с фейковым DAO, маппинг; `androidTest` только для Room/камеры.

**Критерий закрытия:** распознавание работает на устройстве, данные пишутся в Room, ревью подтвердило lifecycle камеры и отсутствие сети.

### Этап 4. UI: один экран сравнения

**Ветка:** `stage/04-ui`

- `core/design`: `PriceCard`, `AddTile`, `UnitPriceBadge`, итоговая панель.
- `ui/compare/CompareScreen`, `CompareViewModel`, `CompareUiState`, `UiEvent`.
- `ui/capture/` — предпросмотр камеры, рамка наведения, кнопка съёмки, подсказка про блики.
- Состояния ошибок: `EmptyText`, `Unparseable`, `CameraError`, `PermissionDenied`.
- Все строки в `strings.xml`, форматирование ₽/кг и ₽/л.

**Критерий закрытия:** экран «мягкий и округлый», лимит 5 соблюдён, нет хардкода строк, code-review подтвердил state-hoisting и Compose-практики.

### Этап 5. Интеграция и сохранение

**Ветка:** `stage/05-integration`

- ViewModel ↔ use cases ↔ repository.
- Загрузка текущей выборки при старте и сохранение после перезапуска.
- Подсветка «самый выгодный» и разницы с остальными.
- Регрессионные тесты полного сценария: добавил 5 → сравнил → перезапуск.

**Критерий закрытия:** сценарий от фотографии до сравнения работает end-to-end, оба ревью пройдены.

### Этап 6. QA и полировка

**Ветка:** `stage/06-qa`

- Проверка на устройстве **в режиме полёта** — главный критерий офлайна.
- Отказ в разрешении камеры → понятное сообщение.
- `lint`, cleanup предупреждений, форматирование.
- Минимальные Compose UI-тесты только на критический путь.
- Обновить `README.md`: как собрать APK и проверить офлайн.

**Критерий закрытия:** `assembleDebug` + `test` + `lint` зелёные, оба сабагента дали `APPROVE` и `TESTS OK`.

### Этап 7. Передача прототипа

**Ветка:** `stage/07-release`

- Собрать debug APK.
- Тег `prototype-v1`.
- Отчёт: что работает, известные ограничения (мелкий шрифт ценников), бэклог (история сравнений, категории).

## 3. Решения, зафиксированные пользователем

| Вопрос | Решение |
| --- | --- |
| `minSdk = 26`, пакет `com.pricelens` | Подтверждено. Пользователь доверил решение: «не шарю, так что мне норм». Дополнительных согласований не требуется. |
| ML Kit **bundled** (APK тяжелее, но офлайн без Play Services) | Подтверждено: да. |
| Ручная корректировка распознанных полей в прототипе | Подтверждено: включаем. Пользователь может поправить название, вес/объём и цену в карточке **до** сравнения. |

Эти решения обязательны для Этапа 1 и 4 и не пересматриваются без явной просьбы пользователя.
