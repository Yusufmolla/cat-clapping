package com.catclapping.jetbrains;

import java.util.List;

/**
 * Self-contained unit checks for CommandMatcher (runnable via java -ea).
 */
public final class CommandMatcherTest {

    public static void main(String[] args) {
        List<String> wildcard = List.of("*");

        // Python & uv
        assertEnabled("python main.py", wildcard);
        assertEnabled("python3 -m pytest", wildcard);
        assertEnabled("py script.py", wildcard);
        assertEnabled("pytest", wildcard);
        assertEnabled("uv run main.py", wildcard);
        assertEnabled("uv run pytest", wildcard);
        assertEnabled("uvx ruff check .", wildcard);
        assertEnabled("uv build", wildcard);

        // C / C++ builds & execution across macOS, Linux, Windows
        assertEnabled("gcc main.c -o main", wildcard);
        assertEnabled("cc -Wall main.c", wildcard);
        assertEnabled("clang main.c -o main", wildcard);
        assertEnabled("g++ main.cpp -o main", wildcard);
        assertEnabled("clang++ main.cpp -o main", wildcard);
        assertEnabled("cl.exe main.c", wildcard);
        assertEnabled("cmake --build build", wildcard);
        assertEnabled("cmake -B build", wildcard);
        assertEnabled("make", wildcard);
        assertEnabled("mingw32-make", wildcard);
        assertEnabled("nmake", wildcard);
        assertEnabled("ninja", wildcard);
        assertEnabled("ctest --output-on-failure", wildcard);
        assertEnabled("./main", wildcard);
        assertEnabled("./a.out", wildcard);
        assertEnabled(".\\program.exe", wildcard);
        assertEnabled("./program.exe", wildcard);
        assertEnabled("program.exe", wildcard);
        assertEnabled("& \".\\program.exe\"", wildcard);
        assertEnabled("CFLAGS=\"-O2\" gcc main.c -o main", wildcard);

        // Non-build/non-run shell navigation should NOT trigger under '*'
        assertDisabled("ls -la", wildcard);
        assertDisabled("cd src", wildcard);
        assertDisabled("git status", wildcard);
        assertDisabled("pwd", wildcard);

        // Selective filters
        assertEnabled("uv run app.py", List.of("uv"));
        assertEnabled("uv run app.py", List.of("python"));
        assertDisabled("gcc main.c", List.of("python"));
        assertEnabled("gcc main.c", List.of("c"));
        assertEnabled("gcc main.c", List.of("gcc"));
        assertEnabled("./a.out", List.of("executable"));
        assertDisabled("python main.py", List.of());

        // Run configurations & builds
        assertTrue(CommandMatcher.isRunConfigurationEnabled("main", "PythonConfigurationType", false, List.of("python")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("my_app", "CMakeRunConfiguration", false, List.of("c")));
        assertTrue(CommandMatcher.isRunConfigurationEnabled("test", "PythonConfigurationType", true, List.of("debug")));
        assertTrue(CommandMatcher.isBuildEnabled(List.of("cmake")));
        assertTrue(!CommandMatcher.isBuildEnabled(List.of("python")));

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
}
