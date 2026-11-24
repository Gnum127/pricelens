# Структура проекта — быстрый ориентир для агентов

Дерево ниже — целевое. Если папки ещё нет, создавай её по мере появления кода и обновляй этот файл.

```
pricelens/
├── AGENTS.md                      # правила работы (читать первым)
├── README.md                      # краткое описание и как запустить
├── settings.gradle.kts            # включение :app, репозитории
├── build.gradle.kts               # root: плагины, общие настройки
├── gradle.properties              # JVM args, AndroidX, compose flags
├── gradle/
│   └── libs.versions.toml         # ВСЕ версии и алиасы зависимостей
├── app/
│   ├── build.gradle.kts           # applicationId, namespace, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/pricelens/
│       │   │   ├── PriceLensApp.kt            # @HiltAndroidApp
│       │   │   ├── MainActivity.kt             # @AndroidEntryPoint, setContent
│       │   │   ├── core/
│       │   │   │   ├── design/                 # AppTheme, Color/Shape/Type, PriceCard, AddTile, UnitPriceBadge
│       │   │   │   └── util/                   # Result-алиасы, decimal formatters, Dispatchers
│       │   │   ├── domain/
│       │   │   │   ├── model/                  # ProductItem, ParsedPriceTag, UnitPrice, Unit/WeightUnit
│       │   │   │   ├── parser/                 # PriceTagParser (чистый Kotlin)
│       │   │   │   ├── pricing/                # UnitPriceCalculator, сравнение
│       │   │   │   ├── usecase/                # Recognize/Add/Remove/Compare use cases
│       │   │   │   └── repository/             # интерфейс PriceTagRepository
│       │   │   ├── data/
│       │   │   │   ├── camera/                 # CameraController (CameraX)
│       │   │   │   ├── ocr/                    # MlKitTextRecognizer, TextRecognizer interface
│       │   │   │   ├── local/
│       │   │   │   │   ├── db/                 # AppDatabase, DAO, Entity, Converters
│       │   │   │   │   └── datastore/          # SettingsDataStore
│       │   │   │   └── repository/             # PriceTagRepositoryImpl
│       │   │   ├── ui/
│       │   │   │   ├── compare/                # CompareScreen, CompareViewModel, CompareUiState
│       │   │   │   └── capture/                # CameraScreen / CameraBottomSheet
│       │   │   └── di/                         # Hilt-модули
│       │   └── res/
│       │       ├── values/                     # strings.xml, colors.xml, themes.xml
│       │       └── xml/                        # file_paths.xml (если нужен временный файл камеры)
│       ├── test/                               # юнит-тесты: parser, calculator, usecases
│       └── androidTest/                        # инструментальные тесты камеры/БД
├── docs/
│   ├── agents/
│   │   ├── code-review.md          # инструкция ревью кода сабагентом
│   │   └── test-review.md          # инструкция ревью тестов сабагентом
│   ├── plan.md                     # план этапов и регламент выполнения
│   ├── product.md                  # идея, скоуп прототипа, не-цели
│   ├── architecture.md             # слои, потоки, решения
│   ├── structure.md                # этот файл
│   └── ocr-notes.md                # форматы ценников и правила парсинга
```

## Куда класть новый код

| Задача | Куда |
| --- | --- |
| Новое правило распознавания ценника | `domain/parser` + тест в `src/test` + запись в `docs/ocr-notes.md` |
| Новая формула/округление | `domain/pricing` + юнит-тест |
| Новый экран/диалог | `ui/<feature>`; общие визуальные куски — `core/design` |
| Новый источник данных | `data/local` или `data/ocr`, интерфейс — в `domain/repository` |
| Новая зависимость | `gradle/libs.versions.toml`, затем `app/build.gradle.kts`, затем `docs/architecture.md` |
| Новое разрешение | `AndroidManifest.xml` + обоснование в `docs/architecture.md` (помним про офлайн) |

## Правила зависимостей между папками

- `domain` не импортирует `android.*`, `androidx.*`, `com.google.mlkit.*`.
- `data` может импортировать `domain`, но не `ui`.
- `ui` может импортировать `domain` и `core/design`, но не напрямую `data` (кроме DI-провайдеров в `MainActivity`).
- `core/design` не знает про use case-ы и БД.

## Именование пакетов

Базовый пакет: `com.pricelens`. Если продукт переименуется, менять `namespace` в `app/build.gradle.kts`,
`applicationId`, все package-директивы и этот документ **одновременно** одним коммитом.
