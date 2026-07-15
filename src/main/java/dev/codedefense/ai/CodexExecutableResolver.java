package dev.codedefense.ai;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
     * <p>Windows npm installs contain an extensionless Unix shim and a {@code .cmd} shim in addition to
     * {@code codex.ps1}. CodeDefense deliberately invokes only the PowerShell shim through {@code -File}.
     */
    public List<CodexExecutable> resolveCandidates() {
        if (!isWindows()) {
            return List.of(new CodexExecutable(List.of("codex")));
        }

        Optional<Path> systemRoot = environmentValue("SystemRoot").flatMap(CodexExecutableResolver::path);
        if (systemRoot.isEmpty()) {
            return List.of();
        }

        Path powerShell = systemRoot.get()
                .resolve("System32")
                .resolve("WindowsPowerShell")
                .resolve("v1.0")
                .resolve("powershell.exe");
        List<CodexExecutable> candidates = new ArrayList<>();
        for (Path pathEntry : pathEntries()) {
            Path powerShellShim = pathEntry.resolve("codex.ps1");
            if (Files.isRegularFile(powerShellShim, LinkOption.NOFOLLOW_LINKS)) {
                List<String> prefix = new ArrayList<>();
                prefix.add(powerShell.toString());
                prefix.addAll(POWERSHELL_ARGUMENTS);
                prefix.add(powerShellShim.toAbsolutePath().normalize().toString());
                candidates.add(new CodexExecutable(prefix));
            }
        }
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
        for (String value : pathValue.get().split(";", -1)) {
            if (!value.isBlank()) {
                path(value).ifPresent(entries::add);
            }
        }
        return List.copyOf(entries);
    }

    private Optional<String> environmentValue(String expectedKey) {
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(expectedKey))
                .map(Map.Entry::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private static Optional<Path> path(String value) {
        try {
            return Optional.of(Path.of(value));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
