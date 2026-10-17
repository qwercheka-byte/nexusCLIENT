# NEXUS

Два проекта в одном репозитории:

| Папка | Что это |
|---|---|
| [`client/`](client) | **NEXUS Client** — клиентский мод Fabric для **Minecraft Java 1.21.11** (Java 21): меню NEXUS, модули, HUD с визуальным редактором, профили, своё главное меню игры |
| [`launcher/`](launcher) | **NEXUS Launcher** — лаунчер Minecraft для **Windows 10/11 x64** (Java 21 + JavaFX), в который встроен NEXUS Client |
| [`updates/`](updates) | zip-пакеты для автообновления (на них ссылается `nexus-update.json` из релиза) |

Скачать готовый лаунчер: **https://github.com/qwercheka-byte/nexusCLIENT/releases/latest**

| Файл релиза | Для чего |
|---|---|
| `Nexus Launcher.exe` | установщик (рекомендуется): ярлыки, удаление через «Приложения», права администратора не нужны |
| `Nexus Launcher.zip` | портативная версия: распаковать папку `Nexus Launcher` и запустить `Nexus Launcher.exe` |
| `nexus-update.json` | служебный файл автообновления |

GitHub показывает пробел в именах файлов релиза как точку (`Nexus.Launcher.exe`) — это ограничение GitHub, после скачивания файл можно переименовать.

## Без jar-файлов

В репозитории, в релизе и в результатах сборки jar-файлов нет:

- **Лаунчер** — нативный `Nexus Launcher.exe` + папка `app\` (классы и ресурсы обычными файлами) + `runtime\` (Java 21 с JavaFX, собранная `jlink`; модули лежат в образе `lib\modules`).
- **Мод** собирается в `client/build/libs/nexus-client-<версия>+mc1.21.11.zip`.
- **Автообновление** скачивает zip-пакеты с проверкой SHA-256.
- **Gradle** запускается скриптами `gradlew` / `gradlew.bat` без `gradle-wrapper.jar`: они скачивают официальный дистрибутив Gradle и сверяют его SHA-256 из `gradle/wrapper/gradle-wrapper.properties`.

Единственное исключение задаёт сам Fabric Loader: он загружает из папки `mods` только файлы `*.jar`. Поэтому мод получает имя `nexus-client-<версия>+mc1.21.11.jar` в момент установки в игру — на компьютере игрока, лаунчером или командой `./gradlew installMod`. Скачанный Gradle и зависимости в кеше Gradle (`~/.gradle`) тоже содержат jar — это сторонние инструменты, а не часть проекта.

## Сборка из исходников

Нужен **JDK 21** (Temurin). Linux/macOS: `./gradlew …`; Windows: `gradlew.bat …` (нужен PowerShell 5+, он есть в Windows 10/11).

```bash
# мод
cd client
./gradlew build                     # -> build/libs/nexus-client-1.0.1+mc1.21.11.zip
./gradlew installMod                # установить в .minecraft/mods (или -PmodsDir=<папка mods>)
./gradlew runClient                 # запустить игру с модом (разработка)

# лаунчер (собирает и встраивает мод из ../client)
cd ../launcher
./gradlew run                       # запустить лаунчер (разработка, любая ОС)
./gradlew portableWin installerWin  # Nexus Launcher.zip и Nexus Launcher.exe -> build/distributions
./gradlew release                   # всё для релиза -> build/release
```

Для `portableWin`/`installerWin` дополнительно нужны MinGW-w64 (`x86_64-w64-mingw32-gcc`, `windres`) и NSIS 3 (`makensis`). Windows-сборку можно делать и на Linux: jmods Windows скачиваются автоматически.

Если на машине мало памяти (меньше 4 ГБ), сначала соберите мод отдельно (`cd client && ./gradlew build`), а потом лаунчер с готовым пакетом: `./gradlew portableWin -PclientPackage=../client/build/libs/nexus-client-1.0.1+mc1.21.11.zip`. Иначе два Gradle-процесса работают одновременно.

## Установка

**Через лаунчер** (проще): установить `Nexus Launcher.exe` → «Создать сборку» → Minecraft 1.21.11, загрузчик Fabric → отметить NEXUS Client. Лаунчер сам скачает игру, Fabric и Fabric API и поставит мод.

**Без лаунчера**: Fabric Loader ≥ 0.19.5 и Fabric API для 1.21.11, затем `./gradlew installMod` в папке `client` (или скопировать zip в `mods` и переименовать в `.jar`). Меню NEXUS открывается клавишей **Right Shift**.

## Что реализовано

**NEXUS Client (1.21.11)** — подробно в [client/README.md](client/README.md):
- **Главное меню игры заменено своим** (mixin поверх ванильного `TitleScreen`, логика ванили сохранена): логотип NEXUS, размытая панорама с бликами и частицами, приветствие с ником и скином, анимированная колонка кнопок, кнопка NEXUS Client. Также оформлены меню паузы и все ванильные экраны.
- **Меню NEXUS** (Right Shift): Home, Modules, HUD Editor, Profiles, Settings, About; поиск, категории, настройки модулей 7 типов.
- **15 модулей**: Fullbright, Custom Crosshair, Custom Nametags, Armor/Potion/Coordinates/FPS/Keystrokes/Target/World Time HUD, Module List, Notifications, Zoom, Auto Sprint, Unfocused FPS.
- **Визуальный редактор HUD**: перетаскивание мышью, масштаб угловыми маркерами и колесом, рамка и маркеры выделенного элемента, привязка к краям/центру экрана и к другим элементам, сетка и направляющие, сброс элемента/всех, предпросмотр, сохранение каждого элемента отдельно (сразу после изменения), защита от выхода за экран, без конфликтов с управлением игры (редактор — обычный `Screen`).
- **Централизованная система анимаций** (`ui.animation`): `AnimationClock`, `Animation`, `SmoothValue`, `Fade`, `AnimatedRect`, `PageTransition`, `ToggleAnimation`, `Easing` (11 кривых), глобальная скорость; всё по времени, без аллокаций в цикле отрисовки.
- **Профили и конфиги**: JSON в `.minecraft/config/nexus/`, атомарная запись в отдельном потоке, резервные копии повреждённых файлов, миграция схем.
- Интерфейс — Minecraft GUI API (`Screen`, `DrawContext`) и собственный рендер фигур; сторонних библиотек нет.

**NEXUS Launcher (Windows)** — подробно в [launcher/README.md](launcher/README.md):
- любые версии Minecraft; Fabric, Quilt, Forge, NeoForge; сборки со своими настройками; моды и каталог Modrinth;
- вход через Microsoft (нужен свой Azure Client ID) и офлайн-профили;
- встроенный NEXUS Client; автообновление лаунчера и мода (с согласия игрока, SHA-256, откат при сбое);
- установщик и портативная версия, русский и английский интерфейс.

Пакеты по требованиям: `dev.nexus.client` (client), `module`, `module.impl.visuals`, `module.impl.hud`, `setting`, `ui`, `ui.component`, `ui.animation`, `hud`, `config`, `event`, `render`, `util` — все есть в `client/src/main/java/dev/nexus/client/` (плюс служебные `mixin`, `ui.page`, `ui.theme`, `ui.vanilla`, `module.impl.{interfaces,utility,performance}`).

## Известные ограничения

- Мод только для **Minecraft 1.21.11**; лаунчер только для **Windows x64** (сборок для Linux/macOS/iOS нет). `./gradlew run` и `runClient` работают на любой ОС — это режим разработки.
- Мод проверялся в игре на Linux (Xvfb, программный рендер Mesa), в одиночной игре. На Windows с реальной видеокартой и на серверах не запускался.
- Лаунчер, установщик и автообновление проверялись в Wine 10, на настоящей Windows — нет. Файлы не подписаны: SmartScreen может предупредить при первом запуске.
- Вход через Microsoft требует собственного приложения Azure, одобренного Mojang; до конца не проверен.
- Меню NEXUS только на английском (главное меню игры и лаунчер — русский/английский).
- Лаунчеры 1.0.0/1.0.1 не могут сами перейти на 1.0.2 (другая структура программы): они покажут «Скачать», нужно один раз поставить новый установщик поверх.
- Автообновление обновляет папку `app`, но не `runtime`.
- В истории git остались jar-файлы из старых коммитов (до 1.0.2); в текущем дереве их нет.

## Лицензия

MIT — см. `client/LICENSE` и `launcher/LICENSE`.
