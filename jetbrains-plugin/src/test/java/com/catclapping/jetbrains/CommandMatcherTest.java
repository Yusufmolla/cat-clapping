package com.catclapping.jetbrains;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Self-contained unit checks for CommandMatcher, CatPlacement, CatClappingSettings, and CatAnimation.
 */
public final class CommandMatcherTest {

    public static void main(String[] args) {
        List<String> wildcard = List.of("*");
        List<String> defaults = CommandMatcher.supportedCommandIds();

        // Python & uv
        for (List<String> enabled : List.of(wildcard, defaults)) {
            assertEnabled("python main.py", enabled);
            assertEnabled("python3 -m pytest", enabled);
            assertEnabled("py script.py", enabled);
            assertEnabled("pytest", enabled);
            assertEnabled("uv run main.py", enabled);
            assertEnabled("uv run pytest", enabled);
            assertEnabled("uvx ruff check .", enabled);
            assertEnabled("uv build", enabled);
            assertEnabled("poetry run pytest", enabled);
            assertEnabled("pdm run pytest", enabled);
            assertEnabled("pipenv run python app.py", enabled);

            // C / C++ builds & execution across macOS, Linux, Windows
            assertEnabled("gcc main.c -o main", enabled);
            assertEnabled("cc -Wall main.c", enabled);
            assertEnabled("clang main.c -o main", enabled);
            assertEnabled("g++ main.cpp -o main", enabled);
            assertEnabled("clang++ main.cpp -o main", enabled);
            assertEnabled("cl.exe main.c", enabled);
            assertEnabled("cmake --build build", enabled);
            assertEnabled("cmake -B build", enabled);
            assertEnabled("make", enabled);
            assertEnabled("mingw32-make", enabled);
            assertEnabled("nmake", enabled);
            assertEnabled("ninja", enabled);
            assertEnabled("meson setup build", enabled);
            assertEnabled("ctest --output-on-failure", enabled);
            assertEnabled("./main", enabled);
            assertEnabled("./a.out", enabled);
            assertEnabled(".\\program.exe", enabled);
            assertEnabled("./program.exe", enabled);
            assertEnabled("program.exe", enabled);
            assertEnabled("& \".\\program.exe\"", enabled);
            assertEnabled("CFLAGS=\"-O2\" gcc main.c -o main", enabled);

            // JS / TS, JVM, and other supported tools
            assertEnabled("node index.js", enabled);
            assertEnabled("npm test", enabled);
            assertEnabled("npx tsc", enabled);
            assertEnabled("yarn build", enabled);
            assertEnabled("pnpm build", enabled);
            assertEnabled("tsc --noEmit", enabled);
            assertEnabled("java -jar app.jar", enabled);
            assertEnabled("./mvnw test", enabled);
            assertEnabled("./gradlew build", enabled);
            assertEnabled("cargo run", enabled);
            assertEnabled("cargo test", enabled);
            assertEnabled("go run .", enabled);
            assertEnabled("go test ./...", enabled);
            assertEnabled("dotnet build", enabled);
            assertEnabled("dotnet test", enabled);
            assertEnabled("sh script.sh", enabled);
            assertEnabled("zsh script.zsh", enabled);
            assertEnabled("sfdx force:source:deploy", enabled);

            // Non-build/non-run shell commands should NOT trigger
            assertDisabled("ls -la", enabled);
            assertDisabled("cd src", enabled);
            assertDisabled("git status", enabled);
            assertDisabled("pwd", enabled);
            assertDisabled("npm install", enabled);
            assertDisabled("npm --version", enabled);
            assertDisabled("uv venv", enabled);
            assertDisabled("uv pip install requests", enabled);
            assertDisabled("poetry install", enabled);
            assertDisabled("go version", enabled);
            assertDisabled("dotnet restore", enabled);
            assertDisabled("echo \"cd && make\"", enabled);

            // Chained build/run commands and PowerShell & without space
            assertEnabled("cd build && cmake --build .", enabled);
            assertEnabled("mkdir -p out && make", enabled);
            assertEnabled("&\"C:\\Apps\\app.exe\"", enabled);
        }

        // Selective filters & deselecting supported commands
        assertEnabled("uv run app.py", List.of("uv"));
        assertEnabled("uv run app.py", List.of("python"));
        assertDisabled("gcc main.c", List.of("python"));
        assertEnabled("gcc main.c", List.of("c"));
        assertEnabled("gcc main.c", List.of("gcc"));
        assertEnabled("./a.out", List.of("executable"));
        assertDisabled("python main.py", List.of());

        List<String> withoutPython = new ArrayList<>(defaults);
        withoutPython.remove("python");
        assertDisabled("python main.py", withoutPython);
        assertEnabled("uv run app.py", withoutPython);

        List<String> withoutCmake = new ArrayList<>(defaults);
        withoutCmake.remove("cmake");
        assertDisabled("cd build && cmake --build .", withoutCmake);

        // Custom single-word and multi-word commands
        assertEnabled("docker compose up", List.of("docker"));
        assertEnabled("ruff check .", List.of("ruff"));
        assertEnabled("cargo test --workspace", List.of("cargo test"));
        assertEnabled("& cargo test --workspace", List.of("cargo test"));
        assertEnabled("&\"C:\\Apps\\myapp.exe\" --verbose", List.of("myapp.exe"));
        assertEnabled("&\"C:\\Apps\\myapp.exe\" --verbose", List.of("executable"));
        assertDisabled("cargo build", List.of("cargo test"));

        // Run configurations & builds (ensure tasks doesn't override deselected debug/language)
        assertTrue(CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", false, List.of("python")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("my_app", "CMakeRunConfiguration", false, List.of("c")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("test", "PythonConfigurationType", true, List.of("debug")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("any", "CustomType", false, defaults));

        List<String> withoutDebug = new ArrayList<>(defaults);
        withoutDebug.remove("debug");
        assertTrue(!CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", true, withoutDebug));
        assertTrue(!CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", false, withoutPython));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", false, List.of("python")));

        List<String> withoutTasks = new ArrayList<>(defaults);
        withoutTasks.remove("tasks");
        assertTrue(!CommandMatcher.isRunConfigurationEnabled("any", "CustomType", false, withoutTasks));

        assertTrue(CommandMatcher.isBuildEnabled(List.of("cmake")));
        assertTrue(CommandMatcher.isBuildEnabled(List.of("gradle")));
        assertTrue(!CommandMatcher.isBuildEnabled(List.of("python")));
        assertTrue(!CommandMatcher.isBuildEnabled(List.of("debug")));
        assertTrue(CommandMatcher.isBuildEnabled(defaults));

        // Settings migration & normalization
        assertTrue(CatClappingSettings.resolveCommandSelection(null).isEmpty());
        assertTrue(CatClappingSettings.resolveCommandSelection(List.of()).isEmpty());
        assertTrue(CatClappingSettings.resolveCommandSelection(List.of("*")).equals(defaults));
        List<String> withCustom = CatClappingSettings.resolveCommandSelection(List.of("*", "docker"));
        assertTrue(withCustom.contains("python") && withCustom.contains("docker"));
        List<String> normalized = CatClappingSettings.resolveCommandSelection(Arrays.asList(" Python ", null, "  "));
        assertTrue(normalized.equals(List.of("python")));

        // Position computation & clamping
        assertPoint(CatPlacement.computePoint(0.0, 0.0, 100, 50, 800, 600, 1200, 900, 200, 300, 8), 108, 58);
        assertPoint(CatPlacement.computePoint(0.5, 0.0, 100, 50, 800, 600, 1200, 900, 200, 300, 8), 400, 58);
        assertPoint(CatPlacement.computePoint(1.0, 0.0, 100, 50, 800, 600, 1200, 900, 200, 300, 8), 692, 58);
        assertPoint(CatPlacement.computePoint(0.5, 0.5, 0, 0, 1000, 800, 1000, 800, 200, 300, 8), 400, 250);
        assertPoint(CatPlacement.computePoint(1.0, 1.0, 0, 0, 1000, 800, 1000, 800, 200, 300, 8), 792, 492);
        // Clamping when anchor extends beyond container or values exceed [0, 1]
        assertPoint(CatPlacement.computePoint(2.0, 2.0, 0, 0, 1500, 1200, 1000, 800, 200, 300, 8), 800, 500);
        assertPoint(CatPlacement.computePoint(-1.0, -1.0, -50, -50, 500, 500, 1000, 800, 200, 300, 8), 0, 0);
        assertTrue(CatPlacement.clampNormalized(Double.NaN) == 0.0);

        // Bundled GIF metadata & preview smoke check
        int[] gifSize = CatAnimation.rawSize();
        assertTrue(gifSize != null && gifSize[0] > 0 && gifSize[1] > 0);
        assertTrue(CatAnimation.cachedRawSize() != null);
        BufferedImage preview = CatAnimation.firstFramePreview();
        assertTrue(preview != null && preview.getHeight() == 220 && preview.getWidth() > 0);

        System.out.println("All CommandMatcherTest assertions passed!");
    }

    private static void assertEnabled(String command, List<String> enabled) {
        if (!CommandMatcher.isCommandEnabled(command, enabled)) {
            throw new AssertionError("Expected enabled for command: " + command + " with " + enabled);
        }
    }

    private static void assertDisabled(String command, List<String> enabled) {
        if (CommandMatcher.isCommandEnabled(command, enabled)) {
            throw new AssertionError("Expected disabled for command: " + command + " with " + enabled);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void assertPoint(Point actual, int expectedX, int expectedY) {
        if (actual.x != expectedX || actual.y != expectedY) {
            throw new AssertionError("Expected (" + expectedX + ", " + expectedY + "), got (" + actual.x + ", " + actual.y + ")");
        }
    }
}
