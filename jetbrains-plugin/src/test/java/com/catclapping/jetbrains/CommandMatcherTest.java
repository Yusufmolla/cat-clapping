package com.catclapping.jetbrains;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained unit checks for CommandMatcher and CatPosition (runnable via java -ea).
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

            // Non-build/non-run shell navigation should NOT trigger
            assertDisabled("ls -la", enabled);
            assertDisabled("cd src", enabled);
            assertDisabled("git status", enabled);
            assertDisabled("pwd", enabled);
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

        // Custom single-word and multi-word commands
        assertEnabled("docker compose up", List.of("docker"));
        assertEnabled("ruff check .", List.of("ruff"));
        assertEnabled("cargo test --workspace", List.of("cargo test"));
        assertDisabled("cargo build", List.of("cargo test"));

        // Run configurations & builds
        assertTrue(CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", false, List.of("python")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("my_app", "CMakeRunConfiguration", false, List.of("c")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("test", "PythonConfigurationType", true, List.of("debug")));
        assertTrue(CommandMatcher.isBuildEnabled(List.of("cmake")));
        assertTrue(!CommandMatcher.isBuildEnabled(List.of("python")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("any", "CustomType", false, defaults));
        assertTrue(CommandMatcher.isBuildEnabled(defaults));

        // Position computation & clamping
        assertPoint(CatPosition.TOP_LEFT.computePoint(100, 50, 800, 600, 1200, 900, 200, 300, 8), 108, 58);
        assertPoint(CatPosition.TOP_CENTER.computePoint(100, 50, 800, 600, 1200, 900, 200, 300, 8), 400, 58);
        assertPoint(CatPosition.TOP_RIGHT.computePoint(100, 50, 800, 600, 1200, 900, 200, 300, 8), 692, 58);
        assertPoint(CatPosition.CENTER.computePoint(0, 0, 1000, 800, 1000, 800, 200, 300, 8), 400, 250);
        assertPoint(CatPosition.BOTTOM_RIGHT.computePoint(0, 0, 1000, 800, 1000, 800, 200, 300, 8), 792, 492);
        // Clamping when anchor extends beyond container
        assertPoint(CatPosition.BOTTOM_RIGHT.computePoint(0, 0, 1500, 1200, 1000, 800, 200, 300, 8), 800, 500);
        assertPoint(CatPosition.TOP_LEFT.computePoint(-50, -50, 500, 500, 1000, 800, 200, 300, 8), 0, 0);

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
