package dev.codedefense.ai;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves a safe command prefix for a locally installed Codex CLI. */
public final class CodexExecutableResolver {
    private static final List<String> POWERSHELL_ARGUMENTS = List.of(
            "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File");

    private final String operatingSystem;
    private final Map<String, String> environment;

    public CodexExecutableResolver(String operatingSystem, Map<String, String> environment) {
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "Operating system");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "Environment"));
    }

    /**
     * Resolves command prefixes in deterministic preference order.
     *
     * <p>On Windows, native {@code codex.exe} launchers are preferred. npm installations are supported through
     * {@code codex.ps1} launched by Windows PowerShell with {@code -File}; the {@code .cmd} and extensionless
     * npm shims are deliberately never invoked.
     */
    public List<CodexExecutable> resolveCandidates() {
        if (!isWindows()) {
            return List.of(new CodexExecutable(List.of("codex")));
        }

        List<Path> entries = pathEntries();
        Set<CodexExecutable> candidates = new LinkedHashSet<>();

        for (Path pathEntry : entries) {
            Path nativeExecutable = pathEntry.resolve("codex.exe");
            if (Files.isRegularFile(nativeExecutable, LinkOption.NOFOLLOW_LINKS)) {
                candidates.add(new CodexExecutable(List.of(normalizedAbsolute(nativeExecutable).toString())));
            }
        }

        powerShellExecutable().ifPresent(powerShell -> {
            for (Path pathEntry : entries) {
                Path powerShellShim = pathEntry.resolve("codex.ps1");
                if (!Files.isRegularFile(powerShellShim, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                List<String> prefix = new ArrayList<>();
                prefix.add(powerShell.toString());
                prefix.addAll(POWERSHELL_ARGUMENTS);
                prefix.add(normalizedAbsolute(powerShellShim).toString());
                candidates.add(new CodexExecutable(prefix));
            }
        });
        return List.copyOf(candidates);
    }

    private boolean isWindows() {
        return operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private List<Path> pathEntries() {
        Optional<String> pathValue = environmentValue("PATH");
        if (pathValue.isEmpty()) {
            return List.of();
        }

        List<Path> entries = new ArrayList<>();
        for (String value : pathValue.get().split(Pattern.quote(File.pathSeparator), -1)) {
            path(value).ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    private Optional<Path> powerShellExecutable() {
        return environmentValue("SystemRoot")
                .or(() -> environmentValue("WINDIR"))
                .flatMap(CodexExecutableResolver::path)
                .map(root -> root.resolve("System32")
                        .resolve("WindowsPowerShell")
                        .resolve("v1.0")
                        .resolve("powershell.exe"))
                .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                .map(CodexExecutableResolver::normalizedAbsolute);
    }

    private Optional<String> environmentValue(String expectedKey) {
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(expectedKey))
                .map(Map.Entry::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private static Optional<Path> path(String value) {
        String candidate = stripOuterQuotes(value.strip());
        if (candidate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(candidate));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static String stripOuterQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static Path normalizedAbsolute(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
