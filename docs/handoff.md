# Handoff — передача в новую сессию

Скопируй содержимое этого файла первым сообщением в новую сессию.

---

Продолжить проект **PriceLens**. Рабочая папка: `C:\dev\pricelens`.

## что прочитать перед изменениями

1. `AGENTS.md`
2. `docs/plan.md`
3. `docs/handoff.md` (этот файл)
4. `docs/architecture.md`
5. `docs/structure.md`
6. `docs/product.md`
7. `docs/ocr-notes.md`
8. `docs/agents/code-review.md`
9. `docs/agents/test-review.md`

## состояние

- Старый репозиторий `Gnum127/CostCatch` удалён. Действующий: `https://github.com/Gnum127/pricelens`.
- Ветка `main` запушена в `origin/main`, рабочее дерево чистое. Точный HEAD посмотреть командой
  `git -C C:\dev\pricelens log -1 --oneline`.
- Кода приложения ещё нет — только документация, план, регламент и зафиксированные решения.

## стек

Kotlin, Jetpack Compose + Material 3, CameraX, ML Kit Text Recognition v2 **bundled on-device**,
Room + DataStore, Hilt, Coroutines/Flow, JUnit 5 + Truth, Gradle Kotlin DSL + `gradle/libs.versions.toml`.
Без `INTERNET`, без авторизации, без облака, без аналитики. Язык интерфейса — русский, строки в `strings.xml`.

## зафиксированные решения (пересматривать только по явной просьбе)

- `minSdk = 26`, пакет / `applicationId` = `com.pricelens`.
- ML Kit **bundled**: офлайн без загрузки модели и без Play Services.
- Ручная корректировка распознанных полей (название, вес/объём, цена) в карточке **включена** в прототип.

## процесс (обязательный)

- Каждый этап — в отдельной ветке `stage/<NN>-<slug>` от свежего `main`. Напрямую в `main` не писать.
- Порядок: реализация → ревью кода `general`-сабагентом (`docs/agents/code-review.md`) → написание тестов →
  ревью тестов `general`-сабагентом (`docs/agents/test-review.md`) → зелёные
  `./gradlew :app:assembleDebug`, `./gradlew test`, `./gradlew lint` → merge в `main` → `git push origin main`.
- Каждому сабагенту явно сказать прочитать нужную инструкцию и передать diff изменений.
- Лимит: не более **5 итераций** на каждый вид ревью. После 5-й с `BLOCKER`/`MAJOR` — стоп,
  отчёт пользователю, merge запрещён.
- Все `BLOCKER`/`MAJOR` исправляются; `MINOR`/`NIT` перечисляются в отчёте.

## первое действие

Создать ветку `stage/01-gradle-theme` и выполнить Этап 1 строго по `docs/plan.md`.
