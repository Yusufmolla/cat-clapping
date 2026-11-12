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
            "^(?:(?:dotnet|cargo|go)\\s+run\\b|(?:uv|poetry|pdm|pipenv)\\s+run\\b|(?:npm|pnpm|yarn)\\s+(?:run\\s+\\S+|start\\b|test\\b|exec\\b)|npx\\s+\\S+)",
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

    private CommandMatcher() {
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
        String invocation = stripLeadingEnv(command).replaceFirst("^&\\s+", "");
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
        boolean supported = isBuildCommand(command) || isRunCommand(command);
        if (choices.contains("*") && supported) {
            return true;
        }
        for (String selector : commandSelectors(command)) {
            if (choices.contains(selector)) {
                return true;
            }
        }
        return false;
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
        if (isDebug && choices.contains("debug")) {
            return true;
        }
        if (choices.contains("tasks")) {
            return true;
        }
        Set<String> selectors = new LinkedHashSet<>();
        if (profileName != null && !profileName.isBlank()) {
            selectors.add(profileName.toLowerCase(Locale.ROOT));
            selectors.addAll(commandSelectors(profileName));
        }
        if (configTypeId != null && !configTypeId.isBlank()) {
            String type = configTypeId.toLowerCase(Locale.ROOT);
            selectors.add(type);
            if (type.contains("python") || type.contains("pytest")) {
                selectors.add("python");
                selectors.add("py");
            }
            if (type.contains("cmake") || type.contains("cidr") || type.contains("cpp") || type.contains("clion")) {
                selectors.add("c");
                selectors.add("cpp");
                selectors.add("cmake");
                selectors.add("executable");
            }
            if (type.contains("node") || type.contains("javascript") || type.contains("npm")) {
                selectors.add("node");
                selectors.add("npm");
            }
        }
        for (String selector : selectors) {
            if (choices.contains(selector)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuildEnabled(List<String> enabled) {
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        List<String> choices = normalizeChoices(enabled);
        return choices.contains("*")
                || choices.contains("tasks")
                || choices.contains("build")
                || choices.contains("cmake")
                || choices.contains("c")
                || choices.contains("cpp");
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
