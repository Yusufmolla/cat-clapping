# Cat Clapping

Celebrate successful builds and program runs with a clapping cat.

When a supported command finishes successfully, Cat Clapping:

- Shows an animated cat at the top center of the editor
- Plays a celebration sound
- Automatically disappears after the celebration

## Features

🐱 Animated clapping cat

🔊 Automatic celebration sound

⚡ Detects successful builds and program runs

🎯 Appears directly inside your editor

## Usage

Install the extension and continue working normally.

When a supported build or run command exits with code 0 in the VS Code integrated terminal, the cat will appear automatically. Successful VS Code build, test and run tasks also trigger it. Run/Debug sessions trigger it when their debugger reports exit code 0.

Examples:

npm run build

npx tsc

dotnet build

cargo build

go build

mvn package

python script.py

node app.js

.\\program.exe

./program

./program.exe

cargo run

dotnet run

npm run start

The terminal trigger requires VS Code shell integration. Debug sessions require a debugger that reports the program's exit code; runs sent only to an output channel are not detected.

## Settings

Search for **Cat Clapping: Enabled Commands** in VS Code Settings, or edit `catClapping.enabledCommands` in user or workspace `settings.json`.

The default `["*"]` keeps the current automatic detection. To celebrate only npm and Python commands, use:

```json
{
  "catClapping.enabledCommands": ["npm", "python"]
}
```

Use `["npm"]` for npm alone or `["python"]` for Python alone. `python` also matches `py` and versioned Python commands such as `python3`; `npm` also matches `npx`. You can add a specific command such as `myapp.exe`, use `executable` for directly launched programs, `tasks` for tasks whose command cannot be identified, or `debug` for all successful debug sessions. An empty list disables the celebration. Changes take effect without restarting VS Code.

## Requirements

VS Code 1.131.0 or later.

On Linux, sound playback requires `paplay` (PulseAudio/PipeWire) or `aplay` (ALSA). The animation works without an audio player.

## Release Notes

### 0.0.7

Clarified the `settings.json` example for choosing which commands trigger the cat.
