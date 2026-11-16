# Cat Clapping

Celebrate successful builds, runs, tests, and debug sessions with a clapping cat and sound effect.

**JetBrains IDE plugin** (PyCharm, CLion, IntelliJ IDEA, WebStorm, etc.) for **macOS, Linux, and Windows**.

Install from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/36093-cat-clapping) (or **Settings → Plugins → Marketplace → "Cat Clapping"**).

## Preview

![Cat Clapping Demo in PyCharm](docs/media/cat-clapping-demo.gif)

![Cat Clapping Settings](docs/media/screenshot-settings.png)

## Features

- Animated clapping cat overlay (configurable position & anchor area: **IDE Window** or **Active Editor**) + celebration sound
- Detects successful **Run / Debug / Test** sessions (`ExecutionManager.EXECUTION_TOPIC`)
- Detects successful **IDE Builds** (`BuildViewManager`, e.g. CMake / Make / Gradle)
- Detects successful **Terminal commands** via JetBrains shell integration (new Terminal, IDE 2025.3+), including:
  - **Python:** `python`, `python3`, `py`, `pytest`, `uv run`, `uvx`, `uv build`, `poetry run`, `pdm run`, `pipenv run`
  - **C / C++ (macOS, Linux, Windows):** `gcc`, `cc`, `clang`, `g++`, `clang++`, `cl` / `cl.exe`, `cmake`, `ctest`, `make`, `mingw32-make`, `nmake`, `ninja`, `meson`, plus direct executables (`./main`, `./a.out`, `.\program.exe`, `./program.exe`, `program.exe`)
  - **Other runtimes/build tools:** `node`, `npm`, `npx`, `yarn`, `pnpm`, `cargo`, `go`, `dotnet`, `mvn`, `gradle`, `java`, `kotlin`, `swift`, etc.
- Configurable under **Settings → Tools → Cat Clapping** (selectable commands, custom commands, draggable window preview, size, volume, and a **Test Cat Clapping** button)

## Build & Release

With Gradle:

```bash
cd jetbrains-plugin
gradle check buildPlugin
```

Or install the generated `jetbrains-plugin/build/distributions/cat-clapping-jetbrains-0.1.8.zip` in PyCharm/CLion via **Settings → Plugins → ⚙️ → Install Plugin from Disk...**.

### GitHub Release Workflow (no auto-build on `main`)

The workflow [.github/workflows/release.yml](.github/workflows/release.yml) only runs on version tags (`v*`) or manually via **Actions → Build & Release → Run workflow**:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## License

[MIT](LICENSE) — Original media assets © 2026 Nicola (`nicosmartworking`), JetBrains plugin © 2026 Yusuf Molla.
