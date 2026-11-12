# Change Log

## 0.0.7

- Clarify the `settings.json` example and show how to select only npm or only Python commands.

## 0.0.6

- Add `catClapping.enabledCommands` to choose commands in user or workspace settings.
- Keep automatic detection with `["*"]`, or select commands such as `npm`, `python`, or a specific executable.
- Apply the selection to terminal commands, tasks, and debug sessions without restarting VS Code.

## 0.0.5

- Celebrate successful runs of Python and other scripts, direct executables, common run commands, VS Code run or test tasks, and debug sessions that report exit code 0.
- Ignore unrelated terminal commands such as `cd`, `ls`, and `git status`.
- Debounce closely spaced task and terminal notifications to avoid duplicate sound and animation.

## 0.0.4

- Show the clapping cat at the top center of the editor during a successful build, keeping it visible while scrolling.
- Play the celebration sound on Linux with `paplay` or `aplay` when available, and on macOS with `afplay`.
- Keep Windows audio playback through PowerShell.
- Detect `g++` and `clang++` terminal commands correctly.

## 1.0.1

- Added support for C/C++ build detection.
- Added support for `g++`, `clang++`, MSVC `cl`, `make`, `ninja`, and CMake build commands.
- Added support for C/C++ VS Code build tasks.
- Cat animation and celebration sound now trigger after successful C/C++ compilations.

## 1.0.0

- Initial release
- Successful build detection
- Animated cat celebration
- Automatic celebration sound
- Cursor-based animation
