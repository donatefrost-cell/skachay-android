# Скачай: выбор качества, настройки и безопасность

> Выполнять inline по этапам пользователя; после каждого этапа показать краткое резюме и команды/шаги проверки. Не публиковать релиз до окончания всех запрошенных проверок.

**Цель:** заменить обрезанные контролы качества, исправить карточки настроек, объединить выбор формата в одном экране и закрыть перечисленные риски ввода, сети, хранения, обновлений и Android-компонентов.

**Архитектура:** чистые Kotlin-модели задают подписи качества, остановки битрейта, URL-политику и выбор конкретного формата. Compose-экраны потребляют эти модели; общий экран выбора формата используется домашним вводом и Share Activity. Сетевые границы отклоняют недоверенные ссылки и перепроверяют цели редиректов; хранилище остаётся только в app-private cache и MediaStore.

**Стек:** Kotlin, Jetpack Compose Material 3, Room, OkHttp, Coil, JUnit, Android screenshot previews, Android Lint, GitHub Actions.

**Спецификация:** вложение `C:\Users\frost\.codex\attachments\6f3cfac4-3fc8-474f-b91a-7f586767cafe\Вставленный текст.txt`.

## Общие ограничения

- URL: только HTTP/HTTPS, максимум 2048 символов, без user-info; редиректы максимум 5 и с повторной проверкой хоста.
- Разбор HTML/JSON ограничен 5 МиБ; сетевые операции имеют таймауты; загрузки идут потоком на диск.
- Качество: подписи не обрезаются; минимальная область нажатия 48 dp; чипы идут в естественном порядке.
- Карточка настроек: скругление 28 dp, внутренний отступ 20×16 dp, промежутки 12 dp.
- Скриншоты: светлая и тёмная темы, масштабы шрифта 1.0, 1.5 и 2.0.

---

### Этап 1: подписи качества и битрейта

**Файлы:**
- Изменить: `app/src/main/java/ru/skachay/app/MainActivity.kt`
- Изменить: `app/src/main/java/ru/skachay/app/settings/AppSettings.kt`
- Тест: `app/src/test/java/ru/skachay/app/settings/QualityPresetPresentationTest.kt`
- Изменить: `app/src/screenshotTest/kotlin/ru/skachay/app/ScreenScreenshotTests.kt`

- [ ] Добавить тест точного порядка подписей `Авто, 144p, 240p, 360p, 480p, 720p, 1080p, 1440p, 4K, Макс` и тест отображения выбранного качества.
- [ ] Запустить целевые unit-тесты и зафиксировать ожидаемый RED.
- [ ] Заменить равные взвешенные строки чипов на FlowRow с `minWidth=72.dp`, `minHeight=48.dp`, одной строкой текста без ellipsis; добавить строку выбранного значения и доступные описания состояния.
- [ ] Заменить пределы битрейта на остановки 1, 2, 4, 8, 16 Мбит/с и «Макс», сохранив чтение старых сохранённых имён остановок.
- [ ] Добавить подписи остановок под слайдером; при крупном шрифте показывать каждую вторую остановку и текущую выбранную.
- [ ] Проверить настройки качества на светлой/тёмной теме и масштабе 2.0.

### Этап 2: единые карточки настроек

**Файлы:**
- Изменить: `app/src/main/java/ru/skachay/app/MainActivity.kt`
- Изменить: `app/src/main/java/ru/skachay/app/StorageControls.kt`
- Изменить/добавить: `app/src/screenshotTest/kotlin/ru/skachay/app/ScreenScreenshotTests.kt`

- [ ] Свести контейнеры секций к одному `SettingsCard`: Surface, clip, 28 dp, внутренние отступы 20×16 dp и вертикальное расстояние 12 dp.
- [ ] Перестроить «Память и история» в строки с данными, кэшем, отдельной очисткой кэша, пояснением и полноширинной кнопкой очистки истории с подтверждением.
- [ ] Убрать конфликтующие внутренние горизонтальные отступы и проверить остальные карточки на текст у края, обрезание и выход кнопок за границу.
- [ ] Добавить отдельные screenshot previews секций настроек при масштабе 1.0/1.5/2.0 и обеих темах.
- [ ] Запустить screenshot render и просмотреть PNG при крупных шрифтах.

### Этап 3: единый экран загрузки

**Файлы:**
- Изменить: `app/src/main/java/ru/skachay/app/ChoiceActivity.kt`
- Изменить: `app/src/main/java/ru/skachay/app/QualityChoiceUi.kt`
- Изменить: `app/src/main/java/ru/skachay/app/MainActivity.kt`
- Создать: `app/src/main/java/ru/skachay/app/choice/FormatSelection.kt`
- Тест: `app/src/test/java/ru/skachay/app/choice/FormatSelectionTest.kt`
- Изменить: `app/build.gradle.kts` (Coil только если в проекте ещё нет загрузчика изображений)

- [ ] Сначала добавить тесты выбора конкретного yt-dlp формата по разрешению, остановке битрейта, кодеку, fps и контейнеру; отдельно проверить pin точного формата и сброс pin при смене фильтра.
- [ ] Создать единую модель `FormatSelection(resolution, bitrateStop, codec, fps, container, audio, pinnedFormatId)` и resolver.
- [ ] Объединить домашний composer и share sheet на одном Compose-компоненте с прокручиваемым содержимым и закреплённой кнопкой.
- [ ] Убрать старый список из четырёх карточек и отдельный экран «Все форматы»; перенести точный формат в сворачиваемую секцию «Дополнительно».
- [ ] Добавить FlowRow чипов, динамические остановки битрейта, совместимость, точные форматы, переключатель без звука и поля сохранения.
- [ ] Загрузить миниатюру через Coil с placeholder, fallback и безопасной сетью; проверить сетевые заголовки и redirect policy.
- [ ] Проверить светлую/тёмную темы, 1.0/1.5/2.0, длинный заголовок, аудио и раскрытое «Дополнительно».

### Этап 4: безопасность и устойчивость

**Файлы:**
- Создать/изменить URL policy, resolver, parser, `DirectDownloader`, `MediaDownloadClient`.
- Проверить/изменить `SharedUrl.kt`, `SavedFileName.kt`, `FileNameTemplate.kt`, `MediaStoreWriter.kt`.
- Проверить/изменить `RecipeFetcher.kt`, `RecipeCodec.kt`, `RecipeStore.kt`, `UpdateDownloader.kt`, `ReleaseFetcher.kt`, `ArchiveValidation.kt`.
- Проверить/изменить `AndroidManifest.xml`, Room schema/migrations, `DownloadService.kt`, `SkachayApp.kt`, workflow и Gradle.
- Создать `SECURITY.md` и `docs/security-review-ru.md`.
- Тесты: `SharedUrlTest`, link/resolver tests, filename tests, recipe/update tests, queue and notification tests.

- [ ] Добавлять тесты до кода для URL schemes/length/user-info/host suffixes, private IP/redirects, file names, recipes, update hash/signature/version, and notification IDs.
- [ ] Включить явный host allowlist, приватные адреса/localhost/link-local denylist и ограничение 5 безопасно проверенных redirect hops.
- [ ] Ограничить body parsing 5 МиБ, добавить call/read/write timeouts и потоковую загрузку; TLS не ослаблять.
- [ ] Проверить filename templates как текстовые шаблоны; очистить titles и MediaStore partial rows при отмене/ошибке; проверить свободное место и очистку кэша.
- [ ] Проверить recipe HTTPS/schema/host constraints и updater SHA-256/подпись/anti-downgrade/confirmation.
- [ ] Проверить разрешения, exported компоненты, immutable PendingIntents, FileProvider, foreground service lifecycle, Room schema/migrations, backup rules, StrictMode и необработанные exceptions.
- [ ] Добавить lint, format/static checks, vulnerability/secret scanning, Dependabot и SHA-pinned Actions.
- [ ] Запустить полный unit, screenshot, lint, build и instrumentation CI; ручные сетевые/процессные сценарии перечислить отдельно, если их нельзя воспроизвести автоматически.

### Этап 5: полный визуальный регресс и документация

**Файлы:**
- Изменить: `app/src/screenshotTest/kotlin/ru/skachay/app/ScreenScreenshotTests.kt`
- Изменить: `README.md`
- Создать: `SECURITY.md`, `docs/security-review-ru.md` (из этапа 4)

- [ ] Сгенерировать полную матрицу home/picker/settings/downloader в двух темах и трёх масштабах.
- [ ] Просмотреть все снимки на обрезанный текст, перекрытия и неравномерные отступы.
- [ ] Обновить README checklist тестов с автоматическими и ручными пунктами.
- [ ] Повторно выполнить проверки этапов 1–4 и сообщить ограничения отдельным списком.
