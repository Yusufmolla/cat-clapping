package com.catclapping.jetbrains;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches terminal commands, IDE builds, and run/debug configurations against celebration rules.
 */
public final class CommandMatcher {

    /**
     * Metadata for a built-in command or trigger category displayed in the settings UI.
     */
    public record SupportedCommand(String id, String label, String category) {
    }

    private static final List<SupportedCommand> SUPPORTED_COMMANDS = List.of(
            // IDE & General
            new SupportedCommand("tasks", "IDE Run / Build tasks", "IDE & General"),
            new SupportedCommand("debug", "Debug sessions", "IDE & General"),
            new SupportedCommand("executable", "Executables (./app, *.exe)", "IDE & General"),
            // Python
            new SupportedCommand("python", "python / py / pytest", "Python"),
            new SupportedCommand("uv", "uv / uvx", "Python"),
            new SupportedCommand("poetry", "poetry", "Python"),
            new SupportedCommand("pdm", "pdm", "Python"),
            new SupportedCommand("pipenv", "pipenv", "Python"),
            // C / C++
            new SupportedCommand("gcc", "gcc / cc", "C / C++"),
            new SupportedCommand("clang", "clang", "C / C++"),
            new SupportedCommand("g++", "g++", "C / C++"),
            new SupportedCommand("clang++", "clang++", "C / C++"),
            new SupportedCommand("cl", "cl / cl.exe", "C / C++"),
            new SupportedCommand("cmake", "cmake / ctest", "C / C++"),
            new SupportedCommand("make", "make / nmake / mingw32-make", "C / C++"),
            new SupportedCommand("ninja", "ninja", "C / C++"),
            new SupportedCommand("meson", "meson", "C / C++"),
            // JavaScript / TypeScript
            new SupportedCommand("node", "node / tsx / bun / deno", "JavaScript / TypeScript"),
            new SupportedCommand("npm", "npm / npx", "JavaScript / TypeScript"),
            new SupportedCommand("yarn", "yarn", "JavaScript / TypeScript"),
            new SupportedCommand("pnpm", "pnpm", "JavaScript / TypeScript"),
            new SupportedCommand("tsc", "tsc", "JavaScript / TypeScript"),
            // Java & JVM
            new SupportedCommand("java", "java", "Java & JVM"),
            new SupportedCommand("kotlin", "kotlin", "Java & JVM"),
            new SupportedCommand("mvn", "mvn / mvnw", "Java & JVM"),
            new SupportedCommand("gradle", "gradle / gradlew", "Java & JVM"),
            // Other Languages & Tools
            new SupportedCommand("cargo", "cargo", "Other Languages & Tools"),
            new SupportedCommand("go", "go", "Other Languages & Tools"),
            new SupportedCommand("dotnet", "dotnet", "Other Languages & Tools"),
            new SupportedCommand("swift", "swift", "Other Languages & Tools"),
            new SupportedCommand("ruby", "ruby", "Other Languages & Tools"),
            new SupportedCommand("php", "php", "Other Languages & Tools"),
            new SupportedCommand("perl", "perl", "Other Languages & Tools"),
            new SupportedCommand("lua", "lua", "Other Languages & Tools"),
            new SupportedCommand("rscript", "rscript", "Other Languages & Tools"),
            new SupportedCommand("bash", "bash / sh / zsh", "Other Languages & Tools"),
            new SupportedCommand("sf", "sf / sfdx", "Other Languages & Tools")
    );

    private static final Set<String> SUPPORTED_IDS = SUPPORTED_COMMANDS.stream()
            .map(SupportedCommand::id)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    private static final Pattern ENV_PREFIX = Pattern.compile(
            "^\\s*(?:[A-Za-z_][A-Za-z0-9_]*=(?:\"[^\"]*\"|'[^']*'|\\S+)\\s+)+");

    private static final Pattern FIRST_TOKEN = Pattern.compile(
            "^\\s*(?:&\\s*)?(?:\"([^\"]+)\"|'([^']+)'|([^\\s|;&<>]+))");

    private static final Pattern EXE_INVOKE = Pattern.compile(
            "^(?:\"[^\"]+\\.exe\"|'[^']+\\.exe'|[^\\s\"'|;&<>]+\\.exe)(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATH_EXEC = Pattern.compile(
            "^(?:\\./|\\.\\\\|~/|/|[a-zA-Z]:[\\\\/])[^\\s|;&<>]+");

    private static final Pattern INTERPRETER_RUN = Pattern.compile(
            "^(?:python(?:\\d+(?:\\.\\d+)?)?|py|pytest|uvx|node|bun|deno|tsx|ts-node|ruby|php|perl|java|kotlin|swift|lua|rscript|bash|sh|zsh)(?:\\.exe)?(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RUNNER_SUBCOMMAND = Pattern.compile(
            "^(?:(?:dotnet|cargo|go)\\s+(?:run|test)\\b|(?:uv|poetry|pdm|pipenv)\\s+run\\b|(?:npm|pnpm|yarn)\\s+(?:run\\s+\\S+|start\\b|test\\b|exec\\b)|npx\\s+\\S+)",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> BUILD_PATTERNS = List.of(
            // JavaScript / TypeScript
            Pattern.compile("\\bnpm\\s+run\\s+(?:build|compile)\\b"),
            Pattern.compile("\\b(?:yarn|pnpm)\\s+(?:build|compile)\\b"),
            Pattern.compile("\\bnpx\\s+tsc\\b"),
            Pattern.compile("\\btsc(?:\\.cmd|\\.exe)?(?=\\s|$)"),
            // Python
            Pattern.compile("\\buv\\s+build\\b"),
            Pattern.compile("\\bpoetry\\s+build\\b"),
            Pattern.compile("\\bpython(?:\\d+(?:\\.\\d+)?)?\\s+-m\\s+build\\b"),
            // Java
            Pattern.compile("\\bmvnw?(?:\\.cmd)?(?:\\s|$)"),
            Pattern.compile("\\bgradlew?(?:\\.bat)?(?:\\s|$)"),
            // .NET / Rust / Go
            Pattern.compile("\\b(?:dotnet|cargo|go)\\s+build\\b"),
            // C / C++ (macOS, Linux, Windows)
            Pattern.compile("\\b(?:gcc|cc|clang|g\\+\\+|clang\\+\\+)(?:\\.exe)?(?=\\s|$)"),
            Pattern.compile("\\bcl(?:\\.exe)?(?=\\s|$)"),
            Pattern.compile("\\b(?:cmake|ctest)(?:\\.exe)?(?=\\s|$)"),
            Pattern.compile("\\b(?:make|mingw32-make|nmake|ninja|meson)(?:\\.exe)?(?=\\s|$)"),
            // Salesforce
            Pattern.compile("\\bsf\\s+project\\s+deploy\\b"),
            Pattern.compile("\\bsfdx\\s+force:source:deploy\\b")
    );

    private static final int EXIT_SIGINT = 130;
    private static final int EXIT_SIGTERM = 143;
    private static final int EXIT_WIN_CTRL_C = 0xC000013A;

    private CommandMatcher() {
    }

    /**
     * Returns true when a process exit code represents a real error or crash,
     * ignoring exit code 0 and manual cancellation signals (SIGINT, SIGTERM, Windows Ctrl+C).
     */
    public static boolean isErrorExitCode(int exitCode) {
        return exitCode != 0
                && exitCode != EXIT_SIGINT
                && exitCode != EXIT_SIGTERM
                && exitCode != EXIT_WIN_CTRL_C;
    }

    public static List<SupportedCommand> supportedCommands() {
        return SUPPORTED_COMMANDS;
    }

    public static List<String> supportedCommandIds() {
        return new ArrayList<>(SUPPORTED_IDS);
    }

    public static boolean isSupportedCommandId(String id) {
        if (id == null) {
            return false;
        }
        return SUPPORTED_IDS.contains(id.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves stored command entries (including legacy {@code "*"}) into concrete command IDs
     * matching the settings UI selection model.
     */
    public static List<String> resolveCommandSelection(List<String> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        boolean hasWildcard = false;
        for (String item : stored) {
            if (item == null) {
                continue;
            }
            String normalized = item.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(normalized)) {
                hasWildcard = true;
            } else if (!normalized.isEmpty()) {
                resolved.add(normalized);
            }
        }
        if (hasWildcard) {
            Set<String> withSupported = new LinkedHashSet<>(SUPPORTED_IDS);
            withSupported.addAll(resolved);
            return new ArrayList<>(withSupported);
        }
        return new ArrayList<>(resolved);
    }

    public static String stripLeadingEnv(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        Matcher envMatcher = ENV_PREFIX.matcher(trimmed);
        if (envMatcher.find()) {
            return trimmed.substring(envMatcher.end()).trim();
        }
        return trimmed;
    }

    public static boolean isBuildCommand(String command) {
        String normalized = stripLeadingEnv(command).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        for (Pattern pattern : BUILD_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRunCommand(String command) {
        String invocation = stripLeadingEnv(command).replaceFirst("^&\\s*", "");
        if (invocation.isEmpty()) {
            return false;
        }
        if (EXE_INVOKE.matcher(invocation).find()) {
            return true;
        }
        if (PATH_EXEC.matcher(invocation).find()) {
            return true;
        }
        return INTERPRETER_RUN.matcher(invocation).find()
                || RUNNER_SUBCOMMAND.matcher(invocation).find();
    }

    public static List<String> commandSelectors(String command) {
        String stripped = stripLeadingEnv(command);
        Matcher matcher = FIRST_TOKEN.matcher(stripped);
        if (!matcher.find()) {
            return List.of();
        }
        String rawToken = matcher.group(1) != null ? matcher.group(1)
                : (matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        String token = rawToken.toLowerCase(Locale.ROOT);
        int lastSlash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
        String name = lastSlash >= 0 ? token.substring(lastSlash + 1) : token;
        String stem = name.replaceFirst("\\.(?:exe|cmd|bat)$", "");

        Set<String> selectors = new LinkedHashSet<>();
        selectors.add(name);
        selectors.add(stem);

        if (stem.matches("python(?:\\d+(?:\\.\\d+)?)?|py|pytest") || name.endsWith(".py")) {
            selectors.add("python");
            selectors.add("py");
        }
        if ("uv".equals(stem) || "uvx".equals(stem)) {
            selectors.add("uv");
            selectors.add("python");
        }
        if ("npx".equals(stem)) {
            selectors.add("npm");
        }
        if (stem.matches("node|tsx|ts-node|bun|deno") || name.endsWith(".js")) {
            selectors.add("node");
        }
        if (stem.matches("gcc|cc|clang|cl|g\\+\\+|clang\\+\\+|cmake|ctest|make|mingw32-make|nmake|ninja|meson")) {
            selectors.add("c");
            selectors.add("cpp");
        }
        if ("cc".equals(stem)) {
            selectors.add("gcc");
        }
        if ("ctest".equals(stem)) {
            selectors.add("cmake");
        }
        if ("mingw32-make".equals(stem) || "nmake".equals(stem)) {
            selectors.add("make");
        }
        if ("mvnw".equals(stem)) {
            selectors.add("mvn");
        }
        if ("gradlew".equals(stem)) {
            selectors.add("gradle");
        }
        if ("sh".equals(stem) || "zsh".equals(stem)) {
            selectors.add("bash");
        }
        if ("sfdx".equals(stem)) {
            selectors.add("sf");
        }
        if (token.matches("^(?:\\./|\\.\\\\|~/|/|[a-z]:[\\\\/]).*") || name.endsWith(".exe")) {
            selectors.add("executable");
        }
        return new ArrayList<>(selectors);
    }

    public static boolean isCommandEnabled(String command, List<String> enabled) {
        if (command == null || command.isBlank() || enabled == null || enabled.isEmpty()) {
            return false;
        }
        List<String> choices = normalizeChoices(enabled);
        if (choices.isEmpty()) {
            return false;
        }
        Set<String> choiceSet = new LinkedHashSet<>(choices);
        boolean wildcard = choiceSet.contains("*");
        Set<String> customChoices = new LinkedHashSet<>();
        for (String choice : choiceSet) {
            if (!"*".equals(choice) && !SUPPORTED_IDS.contains(choice)) {
                customChoices.add(choice);
            }
        }

        for (String segment : splitShellSegments(command)) {
            if (segment.isBlank()) {
                continue;
            }
            if (!customChoices.isEmpty() && matchesCustomSegment(segment, customChoices)) {
                return true;
            }
            boolean supported = isBuildCommand(segment) || isRunCommand(segment);
            if (!supported) {
                continue;
            }
            if (wildcard) {
                return true;
            }
            for (String selector : commandSelectors(segment)) {
                if (choiceSet.contains(selector)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesCustomSegment(String segment, Set<String> customChoices) {
        for (String selector : commandSelectors(segment)) {
            if (customChoices.contains(selector)) {
                return true;
            }
        }
        String normalizedSegment = stripLeadingEnv(segment)
                .replaceFirst("^&\\s*", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalizedSegment.isEmpty()) {
            return false;
        }
        for (String choice : customChoices) {
            if (choice.indexOf(' ') >= 0) {
                String normalizedChoice = choice.replaceAll("\\s+", " ").trim();
                if (normalizedSegment.equals(normalizedChoice)
                        || normalizedSegment.startsWith(normalizedChoice + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> splitShellSegments(String command) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        char quote = 0;
        int len = command.length();
        for (int i = 0; i < len; i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"' && i + 1 < len) {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ';') {
                segments.add(command.substring(start, i));
                start = i + 1;
            } else if ((c == '&' || c == '|') && i + 1 < len && command.charAt(i + 1) == c) {
                segments.add(command.substring(start, i));
                i++;
                start = i + 1;
            }
        }
        segments.add(command.substring(start));
        return segments;
    }

    public static boolean isRunConfigurationEnabled(
            String profileName,
            String configTypeId,
            boolean isDebug,
            List<String> enabled) {
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        List<String> choices = normalizeChoices(enabled);
        if (choices.contains("*")) {
            return true;
        }
        if (isDebug) {
            return choices.contains("debug");
        }
        Set<String> selectors = new LinkedHashSet<>();
        boolean recognizedConfig = false;
        if (configTypeId != null && !configTypeId.isBlank()) {
            String type = configTypeId.toLowerCase(Locale.ROOT);
            selectors.add(type);
            if (type.contains("python") || type.contains("pytest") || type.contains("django")) {
                selectors.add("python");
                selectors.add("py");
                recognizedConfig = true;
            }
            if (type.contains("cmake") || type.contains("cidr") || type.contains("cpp") || type.contains("clion")) {
                selectors.add("c");
                selectors.add("cpp");
                selectors.add("cmake");
                selectors.add("executable");
                recognizedConfig = true;
            }
            if (type.contains("node") || type.contains("javascript") || type.contains("npm")
                    || type.contains("jest") || type.contains("vitest") || type.contains("mocha")) {
                selectors.add("node");
                selectors.add("npm");
                recognizedConfig = true;
            }
            if (type.contains("gradle")) {
                selectors.add("gradle");
                recognizedConfig = true;
            }
            if (type.contains("maven")) {
                selectors.add("mvn");
                recognizedConfig = true;
            }
            if ("application".equals(type) || type.contains("junit") || type.contains("testng")
                    || type.contains("jarapplication")) {
                selectors.add("java");
                recognizedConfig = true;
            }
            if (type.contains("kotlin") || type.contains("jetrun")) {
                selectors.add("kotlin");
                recognizedConfig = true;
            }
            if (type.contains("cargo") || type.contains("rust")) {
                selectors.add("cargo");
                recognizedConfig = true;
            }
            if (type.contains("goapplication") || type.contains("gotest") || type.contains("goland")) {
                selectors.add("go");
                recognizedConfig = true;
            }
            if (type.contains("dotnet") || type.contains("coreclr") || type.contains("rider")) {
                selectors.add("dotnet");
                recognizedConfig = true;
            }
        }
        if (profileName != null && !profileName.isBlank()) {
            selectors.add(profileName.toLowerCase(Locale.ROOT));
            selectors.addAll(commandSelectors(profileName));
        }
        for (String selector : selectors) {
            if (choices.contains(selector)) {
                return true;
            }
        }
        return !recognizedConfig && choices.contains("tasks");
    }

    public static boolean isBuildEnabled(List<String> enabled) {
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        List<String> choices = normalizeChoices(enabled);
        for (String id : List.of("*", "tasks", "build", "cmake", "c", "cpp",
                "gradle", "mvn", "make", "ninja", "meson",
                "gcc", "clang", "g++", "clang++", "cl",
                "tsc", "cargo", "go", "dotnet")) {
            if (choices.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizeChoices(List<String> enabled) {
        List<String> out = new ArrayList<>(enabled.size());
        for (String value : enabled) {
            if (value != null) {
                String trimmed = value.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }
}
