package dev.codedefense.provenance;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CodexProvenanceConfig(boolean enabled, Duration timeout,
        Duration terminationGracePeriod, int maximumItems, Set<String> supportedCodexVersions) {
    public static final String ENABLE_VARIABLE = "CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE";

    public CodexProvenanceConfig {
        requirePositive(timeout, "timeout");
        requirePositive(terminationGracePeriod, "terminationGracePeriod");
        if (maximumItems < 1 || maximumItems > 1_000) {
            throw new IllegalArgumentException("maximumItems must be between 1 and 1000");
        }
        Objects.requireNonNull(supportedCodexVersions, "supportedCodexVersions");
        supportedCodexVersions = Set.copyOf(supportedCodexVersions);
        if (supportedCodexVersions.isEmpty() || supportedCodexVersions.stream()
                .anyMatch(version -> !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            throw new IllegalArgumentException("supported Codex versions are invalid");
        }
    }

    public static CodexProvenanceConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        boolean enabled = "true".equalsIgnoreCase(environment.get(ENABLE_VARIABLE));
        return new CodexProvenanceConfig(enabled, Duration.ofSeconds(15), Duration.ofSeconds(2),
                1_000, Set.of("0.143.0", "0.144.0"));
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive");
    }
}
